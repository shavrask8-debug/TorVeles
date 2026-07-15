package com.example.torrentstreamer

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient

/**
 * Керує життєвим циклом локального Go-підпроцесу TorrServer з інтегрованою crash-recovery
 * системою,health-check інтервалами та обмеженням розміру лог-файлів на диску.
 */
object TorrServerManager {
    private const val TAG = "TorrServerManager"
    private const val SERVER_PORT = "8090"
    private const val HEALTH_CHECK_URL = "http://127.0.0.1:$SERVER_PORT/settings"
    private const val SHUTDOWN_URL = "http://127.0.0.1:$SERVER_PORT/shutdown"
    private const val MAX_LOG_SIZE_BYTES = 2 * 1024 * 1024 // 2MB ліміт дискового логу

    private var process: Process? = null
    private val isRunning = AtomicBoolean(false)
    private val supervisorJob = SupervisorJob()
    private val managerScope = CoroutineScope(Dispatchers.IO + supervisorJob)
    private var watchdogJob: Job? = null

    @Volatile
    private var sharedOkHttpClient: OkHttpClient? = null

    /**
     * Повертає оптимізований OkHttpClient з Connection Pool для уникнення накладних
     * витрат на TCP Handshake при опитуванні localhost (Matrix API).
     */
    @Synchronized
    fun getSharedClient(): OkHttpClient {
        return sharedOkHttpClient ?: OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build().also { sharedOkHttpClient = it }
    }

    /**
     * Запускає Watchdog-моніторинг підпроцесу. Автоматично рестартує сервер при крашах
     * за допомогою експоненційного часу очікування (1s -> 2s -> 4s -> cap 15s).
     */
    fun start(context: Context) {
        if (isRunning.getAndSet(true)) {
            Log.d(TAG, "Watchdog is already running.")
            return
        }

        watchdogJob?.cancel()
        watchdogJob = managerScope.launch {
            var backoffMs = 1000L
            val appContext = context.applicationContext

            while (isActive) {
                val isSuccessfullyStarted = try {
                    runServerProcess(appContext)
                } catch (e: Exception) {
                    Log.e(TAG, "Fatal failure during engine spin-up: ${e.message}", e)
                    false
                }

                if (isSuccessfullyStarted) {
                    backoffMs = 1000L // Скидання затримки при успішному старті та проходженні health-check
                    val exitCode = waitForProcessExit()
                    Log.w(TAG, "Go subprocess unexpectedly exited with code $exitCode. Triggering restart...")
                }

                if (!isActive) break

                Log.w(TAG, "Re-launching TorrServer in ${backoffMs}ms...")
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(15000L) // Ліміт на максимальний інтервал
            }
            isRunning.set(false)
        }
    }

    /**
     * Виконує запуск бінарного файлу та ініціює асинхронні перевірки здоров'я (Health-check).
     */
    private suspend fun runServerProcess(context: Context): Boolean = withContext(Dispatchers.IO) {
        shutdownLocalhostServer() // Превентивне очищення завислих зомбі-процесів на порту
        delay(300)

        val nativeDir = context.applicationInfo.nativeLibraryDir
        val binaryPath = "$nativeDir/libtorrserver.so"
        val dbPath = context.filesDir.absolutePath

        val binaryFile = File(binaryPath)
        if (!binaryFile.exists()) {
            Log.e(TAG, "Go engine binary is missing at directory: $binaryPath")
            return@withContext false
        }

        val dbDir = File(dbPath, "torr_db")
        if (!dbDir.exists()) {
            dbDir.mkdirs()
        }

        Log.i(TAG, "Initializing Go engine. Path: $binaryPath, DB: ${dbDir.absolutePath}")

        val p = ProcessBuilder(binaryPath, "-d", dbDir.absolutePath, "-p", SERVER_PORT)
            .redirectErrorStream(true)
            .start()
        process = p

        // Запуск неблокуючого логування з обмеженням дискового простору
        launch {
            pipeStreamToRotatedLog(context, p.inputStream)
        }

        // Кратне опитування сокету для підтвердження готовності бази даних SQLite
        var isHealthy = false
        for (attempt in 0 until 6) {
            delay(500)
            if (performHealthCheck()) {
                isHealthy = true
                break
            }
        }

        if (!isHealthy) {
            Log.e(TAG, "Engine failed health-check. Terminating process...")
            p.destroy()
            return@withContext false
        }

        true
    }

    /**
     * Очікує завершення підпроцесу без блокування головного потоку.
     */
    private suspend fun waitForProcessExit(): Int = withContext(Dispatchers.IO) {
        val p = process ?: return@withContext -1
        try {
            p.waitFor()
        } catch (e: InterruptedException) {
            -1
        }
    }

    /**
     * Читає потік виведення та записує його у файл із механізмом циклічної ротації логів (Max 2MB).
     */
    private fun pipeStreamToRotatedLog(context: Context, inputStream: java.io.InputStream) {
        val logFile = File(context.filesDir, "torr_service.log")
        try {
            rotateLogsIfNeeded(logFile)
            FileOutputStream(logFile, true).bufferedWriter().use { writer ->
                inputStream.bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val msg = line ?: ""
                        Log.d("TorrServer_GoLog", msg)
                        writer.write("[${System.currentTimeMillis()}] $msg\n")
                        writer.flush()
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error writing rotated log file: ${e.message}")
        }
    }

    /**
     * Перейменовує лог-файл у .bak при перевищенні ліміту у 2MB, зберігаючи сталий footprint на флеш-накопичувачі.
     */
    private fun rotateLogsIfNeeded(logFile: File) {
        if (logFile.exists() && logFile.length() > MAX_LOG_SIZE_BYTES) {
            val backupFile = File(logFile.parent, "${logFile.name}.bak")
            if (backupFile.exists()) {
                backupFile.delete()
            }
            logFile.renameTo(backupFile)
            Log.i(TAG, "Subprocess logs successfully rotated to backup.")
        }
    }

    /**
     * Відправляє HTTP-запит до локального сокету для валідації стану Matrix API.
     */
    private fun performHealthCheck(): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(HEALTH_CHECK_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 800
            connection.readTimeout = 800
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")

            // Наднадійний запит налаштувань для перевірки стану DB
            connection.outputStream.use { os ->
                os.write("{\"action\":\"get\"}".toByteArray(Charsets.UTF_8))
            }
            connection.responseCode == HttpURLConnection.HTTP_OK
        } catch (e: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * М'яко гасить двигун через вбудований API-ендпоінт, звільняючи файлові локи SQLite.
     */
    private fun shutdownLocalhostServer() {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(SHUTDOWN_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 600
            connection.readTimeout = 600
            connection.responseCode
            Log.d(TAG, "Sent shutdown API call to current instance.")
        } catch (_: Exception) {
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Зупиняє Watchdog та ліквідує підпроцес.
     */
    fun stop() {
        watchdogJob?.cancel()
        managerScope.launch {
            shutdownLocalhostServer()
            withContext(Dispatchers.IO) {
                process?.destroy()
                process = null
            }
            isRunning.set(false)
            Log.i(TAG, "Subprocess supervisor successfully terminated.")
        }
    }
}