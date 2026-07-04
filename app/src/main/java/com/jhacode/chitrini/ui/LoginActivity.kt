package com.jhacode.chitrini.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jhacode.chitrini.network.SocketManager
import com.jhacode.chitrini.repository.MainRepository
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.permissionx.guolindev.PermissionX

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                LoginScreen(
                    onLoginSuccess = { username ->
                        SocketManager.connect(username)
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: (String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF8E7))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Chitrini",
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF9C7C38)
        )
        
        Text(
            text = "Connect with elegance.",
            fontSize = 16.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(48.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Choose a Username") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isLoading,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF9C7C38),
                focusedLabelColor = Color(0xFF9C7C38)
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            CircularProgressIndicator(color = Color(0xFF9C7C38))
        } else {
            Button(
                onClick = {
                    if (username.isBlank()) {
                        Toast.makeText(context, "Username cannot be empty", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    PermissionX.init(context as AppCompatActivity)
                        .permissions(
                            android.Manifest.permission.CAMERA,
                            android.Manifest.permission.RECORD_AUDIO
                        )
                        .request { allGranted, _, _ ->
                            if (allGranted) {
                                isLoading = true
                                
                                // 🔥 Pre-fetch Appwrite Session in background
                                Handler(Looper.getMainLooper()).post {
                                    (context as? AppCompatActivity)?.lifecycleScope?.launch(Dispatchers.IO) {
                                        com.jhacode.chitrini.storage.AppwriteManager.ensureSession()
                                    }
                                }

                                // Login via MainRepository
                                MainRepository.getInstance().login(username.trim(), context) {
                                    isLoading = false
                                    val prefs = context.getSharedPreferences("chitrini_prefs", android.content.Context.MODE_PRIVATE)
                                    prefs.edit().putString("username", username.trim()).apply()
                                    onLoginSuccess(username.trim())
                                }
                                
                                // Reset loading if it takes too long (error case)
                                Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    if (isLoading) {
                                        isLoading = false
                                        Toast.makeText(context, "Login Timeout. Is username taken?", Toast.LENGTH_LONG).show()
                                    }
                                }, 10000)
                            } else {
                                Toast.makeText(context, "Permissions required for calls & QR", Toast.LENGTH_SHORT).show()
                            }
                        }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C7C38)),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Enter Chitrini", fontSize = 18.sp)
            }
        }
    }
}
