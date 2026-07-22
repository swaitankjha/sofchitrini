package com.jhacode.chitrini.ui.chat

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jhacode.chitrini.data.local.entity.MessageEntity
import java.io.File
import kotlinx.coroutines.delay

@Composable
fun ChatInputBar(
    onSend: (String) -> Unit,
    onMediaSelected: (Uri, String, String?) -> Unit,
    isUploading: Boolean,
    replyingTo: MessageEntity? = null,
    editingMessage: MessageEntity? = null,
    onCancelReply: () -> Unit = {},
    onCancelEdit: () -> Unit = {},
    onTyping: (Boolean) -> Unit = {}
) {
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current
    
    LaunchedEffect(editingMessage) {
        if (editingMessage != null) {
            text = editingMessage.text
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val mimeType = context.contentResolver.getType(it) ?: "image/jpeg"
            onMediaSelected(it, mimeType, if (text.isBlank()) null else text.trim())
            text = "" 
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            
            // Previews...
            if (editingMessage != null) {
                Surface(
                    modifier = Modifier.padding(bottom = 6.dp).fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(0.3f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(editingMessage.text, maxLines = 1, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = onCancelEdit, Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) }
                    }
                }
            }

            if (replyingTo != null) {
                Surface(
                    modifier = Modifier.padding(bottom = 6.dp).fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(3.dp).height(24.dp).background(MaterialTheme.colorScheme.primary))
                        Spacer(Modifier.width(8.dp))
                        Text(replyingTo.text, maxLines = 1, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = onCancelReply, Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) }
                    }
                }
            }

            Row(verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) {
                    if (isUploading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                }

                // 🔥 Simplified Wrapper to prevent touch blocking
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.4f), RoundedCornerShape(24.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.1f), RoundedCornerShape(24.dp))
                ) {
                    GboardTextField(
                        value = text,
                        onValueChange = { 
                            text = it
                            onTyping(it.isNotEmpty())
                        },
                        onMediaSelected = { uri, mimeType -> 
                            onMediaSelected(uri, mimeType, if (text.isBlank()) null else text.trim())
                            text = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "Message..."
                    )
                }

                Spacer(Modifier.width(6.dp))

                FloatingActionButton(
                    onClick = { if (text.isNotBlank()) { onSend(text); text = "" } },
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}
