package com.jhacode.chitrini.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AddPeopleScreen(
    onBack: () -> Unit,
    onSendRequest: (String) -> Unit,
    onOpenScanner: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF8E7)) // quote-tone
            .padding(16.dp)
    ) {

        AddPeopleTopBar(onBack)

        Spacer(Modifier.height(24.dp))

        // 🔤 USERNAME INPUT (manual or QR-filled)
        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it.trim()
                error = null
            },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = error != null
        )

        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(16.dp))

        // 🚀 SEND REQUEST
        Button(
            onClick = {
                if (username.isBlank()) {
                    error = "Username cannot be empty"
                    return@Button
                }
                onSendRequest(username)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF9C7C38)
            )
        ) {
            Text("Send Request")
        }

        Spacer(Modifier.height(32.dp))

        // 📷 QR SCAN
        QrScanCard(
            onClick = onOpenScanner
        )
    }
}
