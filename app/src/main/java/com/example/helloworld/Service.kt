package com.example.helloworld

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.InnerResultCallback
import com.sunmi.peripheral.printer.SunmiPrinterService

class MyForegroundService : Service() {

    private var sunmiPrinterService: SunmiPrinterService? = null

    companion object {
        const val CHANNEL_ID = "ForegroundServiceChannel"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val runnable = object : Runnable {
        override fun run() {
            Log.d("MyForegroundService", "Service is still running")
            printText("This is a large text for print")
            handler.postDelayed(this, 10000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("MyForegroundService", "Service onCreate called")
        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Background service")
            .setContentText("The service is running even if the app is closed")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(1, notification)

        bindPrinterService()

        handler.post(runnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MyForegroundService", "Service onStartCommand called with startId: $startId")
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
                "Background service notification",
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
                    printText("This is a large text for print")
                }

                override fun onDisconnected() {
                    sunmiPrinterService = null
                }
            })
        if (!result) {
            Log.e("PrinterService", "Failed to bind printer service")
        }
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