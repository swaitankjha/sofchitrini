package com.jhacode.chitrini.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jhacode.chitrini.data.local.entity.MessageEntity
import com.jhacode.chitrini.storage.MediaDownloader
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    isMe: Boolean,
    onLongClick: () -> Unit = {},
    onImageClick: (File) -> Unit = {},
    onReply: (MessageEntity) -> Unit = {}
) {
    val isDeleted = message.text == "[deleted_by_sender]"
    val context = LocalContext.current
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    
    var localFile by remember { mutableStateOf<File?>(null) }
    var isDownloading by remember { mutableStateOf(false) }

    // 🔥 Swipe state
    var offsetX by remember { mutableStateOf(0f) }

    LaunchedEffect(message.fileId) {
        if (message.fileId != null) {
            isDownloading = true
            localFile = MediaDownloader.downloadMedia(
                context,
                message.fileId,
                message.encryptedKey ?: "",
                message.iv ?: ""
            )
            isDownloading = false
        }
    }

    // 🔥 Gold-themed gradients for "Me"
    val myGradient = Brush.linearGradient(
        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
    )
    // 🔥 Translucent white for "Other"
    val otherBubbleColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        if (dragAmount > 0) {
                            offsetX = (offsetX + dragAmount).coerceAtMost(100f)
                        } else {
                            offsetX = (offsetX + dragAmount).coerceAtLeast(0f)
                        }
                    },
                    onDragEnd = {
                        if (offsetX >= 70f) {
                            onReply(message)
                        }
                        offsetX = 0f
                    },
                    onDragCancel = { offsetX = 0f }
                )
            }
    ) {
        // 🔥 Reply Icon background
        if (offsetX > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .size(32.dp)
                    .graphicsLayer {
                        alpha = (offsetX / 70f).coerceAtMost(1f)
                        scaleX = (offsetX / 70f).coerceAtMost(1f)
                        scaleY = (offsetX / 70f).coerceAtMost(1f)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .offset { IntOffset(offsetX.roundToInt(), 0) },
            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
        ) {
            Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
                Surface(
                    color = if (isMe) Color.Transparent else otherBubbleColor,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isMe) 16.dp else 2.dp,
                        bottomEnd = if (isMe) 2.dp else 16.dp
                    ),
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clip(RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isMe) 16.dp else 2.dp,
                            bottomEnd = if (isMe) 2.dp else 16.dp
                        ))
                        .then(if (isMe) Modifier.background(myGradient) else Modifier)
                        .combinedClickable(
                            onClick = {
                                if (message.messageType == "IMAGE" && localFile != null) {
                                    onImageClick(localFile!!)
                                }
                            },
                            onLongClick = { if (!isDeleted && isMe) onLongClick() }
                        )
                ) {
                    Column(modifier = Modifier.padding(if (message.messageType == "IMAGE") 4.dp else 10.dp)) {
                        
                        // 🔥 Reply Preview inside bubble
                        if (message.replyToId != null && !isDeleted) {
                            Surface(
                                color = if (isMe) Color.Black.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(bottom = 6.dp).fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(24.dp)
                                            .background(if (isMe) Color.White else MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = message.replyToSender ?: "User",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isMe) Color.White else MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            text = message.replyToText ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (isMe) Color.White.copy(alpha = 0.8f) else Color.Gray,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }

                        if (isDeleted) {
                            Text(
                                text = "Message was deleted",
                                color = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f) else Color.Gray,
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                fontSize = 11.sp
                            )
                        } else {
                            when (message.messageType) {
                                "IMAGE" -> {
                                    if (localFile != null) {
                                        AsyncImage(
                                            model = localFile,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp)),
                                            contentScale = ContentScale.FillWidth
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.size(200.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isDownloading) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = if (isMe) Color.White else MaterialTheme.colorScheme.primary)
                                            } else {
                                                Text("Tap to download", color = if (isMe) Color.White else Color.Gray, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                                "FILE", "VIDEO", "VOICE" -> {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                                        Icon(
                                            imageVector = Icons.Default.InsertDriveFile, 
                                            contentDescription = null, 
                                            tint = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = message.originalFileName ?: "Document",
                                                color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "${message.messageType} • ${message.fileSize?.let { it / 1024 } ?: 0} KB",
                                                color = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                                "MISSED_CALL" -> {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.CallMissed, 
                                            contentDescription = null, 
                                            tint = Color.Red,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "Missed call",
                                            color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                else -> {
                                    Text(
                                        text = message.text,
                                        color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }

                        // Bottom metadata
                        Row(
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = timeFormatter.format(Date(message.timestamp)),
                                fontSize = 9.sp,
                                color = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            if (isMe && !isDeleted) {
                                Spacer(Modifier.width(4.dp))
                                val icon = if (message.status == "sent") Icons.Default.Check else Icons.Default.DoneAll
                                val tint = if (message.status == "seen") Color(0xFF4CAF50) else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                                
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = tint
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
