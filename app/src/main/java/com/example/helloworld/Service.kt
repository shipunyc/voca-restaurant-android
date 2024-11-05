package com.example.helloworld

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import androidx.core.app.NotificationCompat
import android.os.Handler
import com.sunmi.peripheral.printer.*
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

class MyForegroundService : Service() {

    private var sunmiPrinterService: SunmiPrinterService? = null
    private var username: String? = null
    private var password: String? = null

    companion object {
        const val CHANNEL_ID = "ForegroundServiceChannel"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val runnable = object : Runnable {
        override fun run() {
            Log.d("MyForegroundService", "Service is running")
            fetchAndPrintData()
            handler.postDelayed(this, 10000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("MyForegroundService", "Service has been created")
        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Background Service")
            .setContentText("Service is running even when the app is closed")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MyForegroundService", "Service has started, startId: $startId")

        // Get username and password from Intent
        username = intent?.getStringExtra("username")
        password = intent?.getStringExtra("password")

        bindPrinterService()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MyForegroundService", "Service is being destroyed")
        handler.removeCallbacks(runnable)

        sunmiPrinterService?.let {
            InnerPrinterManager.getInstance().unBindService(this, null)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Background Service Notification",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun bindPrinterService() {
        val result =
            InnerPrinterManager.getInstance().bindService(this, object : InnerPrinterCallback() {
                override fun onConnected(service: SunmiPrinterService) {
                    sunmiPrinterService = service
                    Log.e("PrinterService", "Printer service bound successfully")
                    // Start scheduled task
                    handler.post(runnable)
                }

                override fun onDisconnected() {
                    sunmiPrinterService = null
                }
            })
        if (!result) {
            Log.e("PrinterService", "Failed to bind printer service")
        }
    }

    private fun fetchAndPrintData() {
        Thread {
            try {
                val url = URL("https://voca-api.unseenmagic.com/what_to_print")
                val connection = url.openConnection() as HttpsURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.doInput = true
                connection.doOutput = true

                // Set request headers
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.setRequestProperty("Charset", "UTF-8")

                // Check if username and password are empty
                val username = this.username ?: ""
                val password = this.password ?: ""

                if (username.isEmpty() || password.isEmpty()) {
                    Log.e("MyForegroundService", "Username or password is empty, cannot send request")
                    return@Thread
                }

                // Prepare form data
                val postData =
                    "username=${URLEncoder.encode(username, "UTF-8")}&password=${URLEncoder.encode(password, "UTF-8")}"

                // Send form data
                val outputStream = DataOutputStream(connection.outputStream)
                outputStream.writeBytes(postData)
                outputStream.flush()
                outputStream.close()

                // Check response code
                val responseCode = connection.responseCode
                if (responseCode == HttpsURLConnection.HTTP_OK) {
                    // Read response data
                    val inputStream = connection.inputStream
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val response = StringBuilder()
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                        response.append("\n")
                    }

                    reader.close()
                    connection.disconnect()

                    val responseData = response.toString()

                    // Call print method on main thread
                    handler.post {
                        printText(responseData)
                    }
                } else {
                    Log.e("MyForegroundService", "Server returned error code: $responseCode")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("MyForegroundService", "Network request failed: ${e.message}")
            }
        }.start()
    }

    private fun printText(largeText: String) {
        try {
            sunmiPrinterService?.printText("$largeText\n", object :
                InnerResultCallback() {
                override fun onRunResult(isSuccess: Boolean) {
                    Log.d("PrinterService", "Print result: $isSuccess")
                }

                override fun onRaiseException(code: Int, msg: String) {
                    Log.e("PrinterService", "Print exception: $msg")
                }

                override fun onReturnString(result: String) {
                    Log.d("PrinterService", "Return result: $result")
                }

                override fun onPrintResult(code: Int, msg: String) {
                    Log.d("PrinterService", "Print callback: $msg")
                }
            })
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }
}
