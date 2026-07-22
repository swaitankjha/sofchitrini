package com.jhacode.chitrini.ui.chat

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.jhacode.chitrini.data.local.entity.MessageEntity
import com.jhacode.chitrini.ui.profile.SettingsViewModel
import com.jhacode.chitrini.ui.theme.WallpaperBackground
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    
    val chatWallpaperUri by settingsViewModel.chatWallpaperUri.collectAsState()
    val chatStockColor by settingsViewModel.chatStockColor.collectAsState()
    val chatBlur by settingsViewModel.chatBlur.collectAsState()

    val messages by viewModel.messages.collectAsState(initial = emptyList())
    
    // 🔥 Selection State
    var selectedMessageForActions by remember { mutableStateOf<MessageEntity?>(null) }
    var showDeleteOptions by remember { mutableStateOf(false) }
    
    var previewFile by remember { mutableStateOf<File?>(null) }
    
    var showEmojiPicker by remember { mutableStateOf(false) }
    var reactingToMessage by remember { mutableStateOf<MessageEntity?>(null) }

    var fontSizeMultiplier by remember { mutableStateOf(1f) }
    val transformableState = rememberTransformableState { zoomChange, _, _ ->
        fontSizeMultiplier = (fontSizeMultiplier * zoomChange).coerceIn(0.5f, 3f)
    }

    val listState = rememberLazyListState()
    val showScrollToBottom by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }

    val selectableDates = remember(messages) {
        object : SelectableDates {
            val validDates = messages.map { 
                val cal = Calendar.getInstance()
                cal.timeInMillis = it.timestamp
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }.toSet()

            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return validDates.contains(utcTimeMillis)
            }
        }
    }
    
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(selectableDates = selectableDates)

    BackHandler(previewFile != null || showEmojiPicker || selectedMessageForActions != null) {
        if (previewFile != null) previewFile = null
        else if (showEmojiPicker) showEmojiPicker = false
        else if (selectedMessageForActions != null) selectedMessageForActions = null
        else onBack()
    }

    if (showDeleteOptions && selectedMessageForActions != null) {
        AlertDialog(
            onDismissRequest = { showDeleteOptions = false },
            title = { Text("Delete Message") },
            text = { Text("Choose how you want to remove this message.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMessageForEveryone(selectedMessageForActions!!, context)
                    selectedMessageForActions = null
                    showDeleteOptions = false
                }) {
                    Text("Delete for Everyone", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.deleteMessageForMe(selectedMessageForActions!!.messageId)
                    selectedMessageForActions = null
                    showDeleteOptions = false
                }) {
                    Text("Delete for me")
                }
            }
        )
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedDate ->
                        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                        val targetDateStr = sdf.format(Date(selectedDate))
                        val index = messages.indexOfLast { 
                            sdf.format(Date(it.timestamp)) == targetDateStr 
                        }
                        if (index != -1) {
                            scope.launch { listState.animateScrollToItem(index) }
                        }
                    }
                    showDatePicker = false
                }) {
                    Text("Jump")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                showModeToggle = false,
                title = { Text("Jump to Date", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold) }
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        WallpaperBackground(
            uri = chatWallpaperUri,
            stockColor = chatStockColor,
            isBlur = chatBlur
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    if (selectedMessageForActions != null) {
                        SelectionTopBar(
                            selectedMessage = selectedMessageForActions!!,
                            myUsername = viewModel.myUsername,
                            onClose = { selectedMessageForActions = null },
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(selectedMessageForActions!!.text))
                                selectedMessageForActions = null
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            },
                            onEdit = {
                                viewModel.editingMessage = selectedMessageForActions
                                selectedMessageForActions = null
                            },
                            onDelete = {
                                showDeleteOptions = true
                            }
                        )
                    } else {
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
                                    Toast.makeText(context, "Profile picture loading...", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                },
                containerColor = Color.Transparent
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .imePadding()
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                                // .transformable(state = transformableState, lockRotationOnZoomPan = true),
                            reverseLayout = true,
                            contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)
                        ) {
                            item {
                                if (viewModel.isOtherUserTyping) {
                                    Box(modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)) {
                                        TypingIndicator()
                                    }
                                }
                            }
                            
                            itemsIndexed(messages, key = { _, msg -> msg.messageId }) { index, msg ->
                                val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                                val currentDate = sdf.format(Date(msg.timestamp))
                                val olderMsg = if (index + 1 < messages.size) messages[index + 1] else null
                                val olderDate = olderMsg?.let { sdf.format(Date(it.timestamp)) }
                                
                                val isFirstInDay = currentDate != olderDate
                                val newerMsg = if (index > 0) messages[index - 1] else null
                                val newerDate = newerMsg?.let { sdf.format(Date(it.timestamp)) }
                                val showTail = newerMsg?.sender != msg.sender || currentDate != newerDate

                                Column {
                                    if (isFirstInDay) {
                                        DateHeader(
                                            timestamp = msg.timestamp,
                                            onClick = { showDatePicker = true }
                                        )
                                    }
                                    
                                    val isSelected = selectedMessageForActions?.messageId == msg.messageId

                                    MessageBubble(
                                        message = msg,
                                        isMe = msg.sender == viewModel.myUsername,
                                        fontSizeMultiplier = fontSizeMultiplier,
                                        showTail = showTail,
                                        isSelected = isSelected,
                                        onLongClick = {
                                            if (msg.text != "[deleted_by_sender]") {
                                                selectedMessageForActions = msg
                                            }
                                        },
                                        onReact = { emoji ->
                                            viewModel.reactToMessage(msg, emoji)
                                            selectedMessageForActions = null // 🔥 Auto-remove selection bar
                                        },
                                        onMoreEmoji = {
                                            reactingToMessage = msg
                                            showEmojiPicker = true
                                            selectedMessageForActions = null // 🔥 Auto-remove selection bar
                                        },
                                        onEdit = { viewModel.editingMessage = it },
                                        onImageClick = { file -> 
                                            if (msg.messageType == "VIDEO") {
                                                try {
                                                    if (file.exists() && file.length() > 0) {
                                                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                                            setDataAndType(uri, "video/*")
                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        }
                                                        context.startActivity(intent)
                                                    } else {
                                                        Toast.makeText(context, "File error", Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Cannot play", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                previewFile = file 
                                            }
                                        },
                                        onReply = { viewModel.replyingTo = it }
                                    )
                                }
                            }
                        }

                        if (!viewModel.isOtherUserDeleted) {
                            ChatInputBar(
                                onSend = { viewModel.sendMessage(it) },
                                onMediaSelected = { uri, mimeType, caption -> 
                                    viewModel.sendMedia(context, uri, mimeType, caption)
                                },
                                isUploading = viewModel.isUploading,
                                replyingTo = viewModel.replyingTo,
                                editingMessage = viewModel.editingMessage,
                                onCancelReply = { viewModel.replyingTo = null },
                                onCancelEdit = { viewModel.editingMessage = null },
                                onTyping = { viewModel.setTyping(it, context) }
                            )
                        }
                    }

                    if (showScrollToBottom) {
                        SmallFloatingActionButton(
                            onClick = {
                                scope.launch { listState.animateScrollToItem(0) }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 86.dp), 
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            contentColor = MaterialTheme.colorScheme.primary,
                            elevation = FloatingActionButtonDefaults.elevation(4.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                        }
                    }
                }
            }
        }

        if (previewFile != null) {
            Box(Modifier.fillMaxSize().background(Color.Black).zIndex(10f)) {
                FullScreenImageViewer(
                    file = previewFile!!,
                    onBack = { previewFile = null }
                )
            }
        }

        if (showEmojiPicker && reactingToMessage != null) {
            EmojiPickerBottomSheet(
                onEmojiSelected = { emoji ->
                    viewModel.reactToMessage(reactingToMessage!!, emoji)
                    showEmojiPicker = false
                    reactingToMessage = null
                },
                onDismiss = {
                    showEmojiPicker = false
                    reactingToMessage = null
                }
            )
        }
    }
}

@Composable
fun SelectionTopBar(
    selectedMessage: MessageEntity,
    myUsername: String,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isMe = selectedMessage.sender == myUsername
    val isText = selectedMessage.messageType == "TEXT"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "1 selected",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Row {
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                
                if (isMe && isText) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }

                if (isMe) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun DateHeader(timestamp: Long, onClick: () -> Unit) {
    val calendar = Calendar.getInstance()
    val now = Calendar.getInstance()
    calendar.timeInMillis = timestamp
    
    val dateText = when {
        calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        calendar.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) -> "TODAY"
        
        calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        calendar.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) - 1 -> "YESTERDAY"
        
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp)).uppercase()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.clickable { onClick() },
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Text(
                text = dateText,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
        }
    }
}
