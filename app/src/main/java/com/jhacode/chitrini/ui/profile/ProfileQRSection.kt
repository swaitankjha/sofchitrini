package com.jhacode.chitrini.ui.profile

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun ProfileQRSection(
    username: String,
    onScanQr: () -> Unit,
    onShareQr: () -> Unit
) {
    val context = LocalContext.current

    // 🔐 QR CONTENT
    val qrContent = "chitrini://user/${username.removePrefix("@")}"

    // 🔥 Generate QR once
    val qrBitmap = remember(qrContent) {
        generateQrBitmap(qrContent)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {

        Surface(
            modifier = Modifier.size(240.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White, // QR needs white background for scanning
            shadowElevation = 8.dp
        ) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "User QR",
                modifier = Modifier.fillMaxSize().padding(24.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Scan to connect with @${username.removePrefix("@")}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, qrContent)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Chitrini QR"))
                },
                modifier = Modifier.height(48.dp)
            ) {
                Text("Share")
            }

            Button(
                onClick = onScanQr,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.height(48.dp)
            ) {
                Text("Scan QR")
            }
        }
    }
}

private fun generateQrBitmap(content: String): Bitmap {
    val writer = QRCodeWriter()
    val matrix = writer.encode(content, BarcodeFormat.QR_CODE, 600, 600)
    val bmp = Bitmap.createBitmap(600, 600, Bitmap.Config.RGB_565)
    for (x in 0 until 600) {
        for (y in 0 until 600) {
            bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bmp
}
