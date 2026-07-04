package com.jhacode.chitrini.ui.call

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.SurfaceViewRenderer

@Composable
fun CallScreen(
    targetUser: String,
    status: String,
    timerText: String,
    isCallActive: Boolean,
    isIncoming: Boolean,
    isMicMuted: Boolean,
    isVideoMuted: Boolean,
    localView: SurfaceViewRenderer,
    remoteView: SurfaceViewRenderer,
    isInPiP: Boolean = false,
    unreadCount: Int = 0,
    isVideoCall: Boolean = true, // 🔥 Flag to distinguish
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleVideo: () -> Unit,
    onSwitchCamera: () -> Unit,
    onFlipPip: () -> Unit,
    chatMessages: String,
    onSendMessage: (String) -> Unit
) {
    var showChat by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F1A))) {

        // 1. Remote Video (Only if video call)
        if (isVideoCall) {
            AndroidView(
                factory = { remoteView },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (!isInPiP) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
            )

            // 2. Local Preview (Only if video call and active)
            if (isVideoCall && !isVideoMuted && isCallActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(24.dp)
                        .size(110.dp, 160.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.DarkGray)
                ) {
                    AndroidView(
                        factory = { localView },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 3. Info
            Column(
                modifier = Modifier
                    .align(if (isVideoCall) Alignment.TopCenter else Alignment.Center)
                    .padding(top = if (isVideoCall) 40.dp else 0.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large Avatar for Audio Call
                if (!isVideoCall) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3A2352).copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = targetUser.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }

                Text(
                    text = "@$targetUser",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isCallActive) timerText else status,
                    color = if (status.contains("Connected")) Color(0xFF00D289) else Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp
                )
            }

            // 4. Controls
            if (isCallActive) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp)
                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(40.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CallControlBtn(
                        icon = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        isActive = !isMicMuted,
                        onClick = onToggleMic
                    )

                    if (isVideoCall) {
                        CallControlBtn(
                            icon = if (isVideoMuted) Icons.Default.VideocamOff else Icons.Default.Videocam,
                            isActive = !isVideoMuted,
                            onClick = onToggleVideo
                        )
                    }

                    // Chat Button with Badge
                    Box {
                        CallControlBtn(
                            icon = Icons.Default.Chat,
                            isActive = showChat,
                            onClick = { showChat = !showChat }
                        )
                        if (unreadCount > 0 && !showChat) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(20.dp)
                                    .background(Color.Red, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = unreadCount.toString(),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (isVideoCall) {
                        CallControlBtn(
                            icon = Icons.Default.Cameraswitch,
                            isActive = true,
                            onClick = onSwitchCamera
                        )
                    }

                    CallControlBtn(
                        icon = Icons.Default.CallEnd,
                        isActive = false,
                        isDanger = true,
                        onClick = onEndCall
                    )
                }
            }

            // 5. Incoming UI
            if (isIncoming && !isCallActive) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF1A1A2E).copy(alpha = 0.95f)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF3A2352)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = targetUser.take(1).uppercase(),
                                color = Color.White,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(text = "@$targetUser", color = Color.White, fontSize = 28.sp)
                        Text(text = if (isVideoCall) "Incoming Video Call" else "Incoming Audio Call", color = Color.White.copy(alpha = 0.6f))

                        Spacer(modifier = Modifier.weight(1f))

                        Row(
                            modifier = Modifier.padding(bottom = 80.dp),
                            horizontalArrangement = Arrangement.spacedBy(64.dp)
                        ) {
                            IncomingActionBtn(Icons.Default.Close, Color(0xFFFF4B5C), onReject)
                            IncomingActionBtn(if (isVideoCall) Icons.Default.Videocam else Icons.Default.Call, Color(0xFF00D289), onAccept)
                        }
                    }
                }
            }

            // 6. Chat Overlay
            AnimatedVisibility(
                visible = showChat,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                ChatOverlay(
                    messages = chatMessages,
                    onClose = { showChat = false },
                    onSend = onSendMessage
                )
            }
        }
    }
}

@Composable
fun CallControlBtn(
    icon: ImageVector,
    isActive: Boolean,
    isDanger: Boolean = false,
    onClick: () -> Unit
) {
    val bgColor = when {
        isDanger -> Color(0xFFFF4B5C)
        isActive -> Color.White.copy(alpha = 0.2f)
        else -> Color.White.copy(alpha = 0.1f)
    }

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(bgColor)
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun IncomingActionBtn(icon: ImageVector, color: Color, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(color)
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(32.dp))
    }
}

@Composable
fun ChatOverlay(
    messages: String,
    onClose: () -> Unit,
    onSend: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .padding(16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(40.dp, 4.dp).background(Color.LightGray, CircleShape))
            }

            Text(
                text = "In-call Chat",
                modifier = Modifier.padding(horizontal = 16.dp),
                fontWeight = FontWeight.Bold
            )

            Box(modifier = Modifier.weight(1f).padding(16.dp)) {
                Text(text = messages, color = Color.Black)
            }

            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type...") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
                IconButton(onClick = {
                    if (text.isNotBlank()) {
                        onSend(text)
                        text = ""
                    }
                }) {
                    Icon(Icons.Default.Send, null, tint = Color(0xFF3A2352))
                }
            }
        }
    }
}
