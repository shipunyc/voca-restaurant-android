package com.example.helloworld

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.helloworld.ui.theme.HelloworldTheme
import java.io.DataOutputStream
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HelloworldTheme {
                // Use rememberSaveable to save login state
                var isLoggedIn by rememberSaveable { mutableStateOf(false) }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!isLoggedIn) {
                        LoginScreen { username, password ->
                            // Execute login logic
                            login(username, password) { success ->
                                if (success) {
                                    isLoggedIn = true
                                    // Start service and pass username and password
                                    val serviceIntent = Intent(this, MyForegroundService::class.java)
                                    serviceIntent.putExtra("username", username)
                                    serviceIntent.putExtra("password", password)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        startForegroundService(serviceIntent)
                                    } else {
                                        startService(serviceIntent)
                                    }
                                } else {
                                    // Show login failure message
                                    Toast.makeText(this, "Login failed, please check username and password", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else {
                        ReceivingScreen()
                    }
                }
            }
        }
    }

    private fun login(username: String, password: String, callback: (Boolean) -> Unit) {
        // Execute network request in background thread
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

                // Prepare form data
                val postData =
                    "username=${URLEncoder.encode(username, "UTF-8")}&password=${URLEncoder.encode(password, "UTF-8")}"

                // Send form data
                val outputStream = DataOutputStream(connection.outputStream)
                outputStream.writeBytes(postData)
                outputStream.flush()
                outputStream.close()

                // Get response code
                val responseCode = connection.responseCode

                // Check response code, if not 400 then login is considered successful
                val success = responseCode != 400

                // Return to main thread to update UI
                runOnUiThread {
                    callback(success)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("MainActivity", "Login request failed: ${e.message}")
                // Return to main thread to notify login failure
                runOnUiThread {
                    callback(false)
                }
            }
        }.start()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLoginClick: (String, String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Please log in", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                // Call callback function when login button is clicked
                onLoginClick(username, password)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Login")
        }
    }
}

@Composable
fun ReceivingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Receiving...", style = MaterialTheme.typography.headlineMedium)
    }
}
