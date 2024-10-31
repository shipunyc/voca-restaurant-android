package com.example.helloworld

import android.os.Bundle
import android.os.RemoteException
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.helloworld.ui.theme.HelloworldTheme
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.InnerResultCallback
import com.sunmi.peripheral.printer.SunmiPrinterService

class MainActivity : ComponentActivity() {

    private var sunmiPrinterService: SunmiPrinterService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HelloworldTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting("W2ORLD")
                }
            }
        }

        bindPrinterService()
    }

    private fun bindPrinterService() {
        val result =
            InnerPrinterManager.getInstance().bindService(this, object : InnerPrinterCallback() {
                override fun onConnected(service: SunmiPrinterService) {
                    sunmiPrinterService = service
                    Log.e("PrinterService", "Printer service bound successfully")
                    printText("This is a large text for printing.")
                }

                override fun onDisconnected() {
                    sunmiPrinterService = null
                }
            })
        if (!result) {
            Log.e("PrinterService", "Failed to bind printer service")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sunmiPrinterService?.let {
            InnerPrinterManager.getInstance().unBindService(this, null)
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

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    HelloworldTheme {
        Greeting("Android")
    }
}
