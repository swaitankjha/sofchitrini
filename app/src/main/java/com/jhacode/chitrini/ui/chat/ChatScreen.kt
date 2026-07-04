package com.jhacode.chitrini.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jhacode.chitrini.data.local.entity.MessageEntity
import com.jhacode.chitrini.ui.theme.WallpaperBackground
import com.jhacode.chitrini.ui.profile.SettingsViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    username: String,
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit
) {
    val context = LocalContext.current
    val settingsViewModel = remember { SettingsViewModel(context) }
    
    // 🔥 Chat Wallpaper State
    val chatWallpaperUri by settingsViewModel.chatWallpaperUri.collectAsState()
    val chatStockColor by settingsViewModel.chatStockColor.collectAsState()
    val chatBlur by settingsViewModel.chatBlur.collectAsState()

    val messages by viewModel.messages.collectAsState(initial = emptyList())
    var selectedMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // Full Screen Preview State
    var previewFile by remember { mutableStateOf<File?>(null) }

    if (showDeleteDialog && selectedMessage != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Message") },
            text = { Text("Do you want to delete this message for everyone?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMessageForEveryone(selectedMessage!!)
                    showDeleteDialog = false
                }) {
                    Text("Delete for Everyone", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    WallpaperBackground(
        uri = chatWallpaperUri,
        stockColor = chatStockColor,
        isBlur = chatBlur
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                ChatTopBar(
                    username = username,
                    status = viewModel.otherUserStatus,
                    isDeleted = viewModel.isOtherUserDeleted,
                    profileFile = viewModel.otherProfileFile,
                    onBack = onBack,
                    onAudioCall = onAudioCall,
                    onVideoCall = onVideoCall,
                    onProfileClick = {
                        if (viewModel.otherProfileFile != null) {
                            previewFile = viewModel.otherProfileFile
                        } else {
                            android.widget.Toast.makeText(context, "Profile picture loading...", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    reverseLayout = true,
                    contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp, start = 12.dp, end = 12.dp)
                ) {
                    items(messages) { msg ->
                        MessageBubble(
                            message = msg,
                            isMe = msg.sender == viewModel.myUsername,
                            onLongClick = {
                                if (msg.sender == viewModel.myUsername) {
                                    val now = System.currentTimeMillis()
                                    if (now - msg.timestamp < 15 * 60 * 1000) {
                                        selectedMessage = msg
                                        showDeleteDialog = true
                                    }
                                }
                            },
                            onImageClick = { file -> previewFile = file },
                            onReply = { viewModel.replyingTo = it } // 🔥 Added
                        )
                    }
                }

                if (!viewModel.isOtherUserDeleted) {
                    ChatInputBar(
                        onSend = { viewModel.sendMessage(it) },
                        onMediaSelected = { uri, mimeType -> 
                            viewModel.sendMedia(context, uri, mimeType)
                        },
                        isUploading = viewModel.isUploading,
                        replyingTo = viewModel.replyingTo,
                        onCancelReply = { viewModel.replyingTo = null }
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                        color = Color.White.copy(alpha = 0.9f)
                    ) {
                        Text(
                            "This contact is no longer available",
                            color = Color.Red.copy(alpha = 0.6f),
                            modifier = Modifier.padding(24.dp),
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    // Full Screen Overlay
    if (previewFile != null) {
        FullScreenImageViewer(
            file = previewFile!!,
            onBack = { previewFile = null }
        )
    }
}
