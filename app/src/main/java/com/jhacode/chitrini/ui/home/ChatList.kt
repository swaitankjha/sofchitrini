package com.jhacode.chitrini.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.jhacode.chitrini.data.local.model.ChatPreview
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatList(
    chats: List<ChatPreview>,
    myUsername: String,
    profilePics: Map<String, File?>,
    onChatClick: (String) -> Unit,
    onClearChat: (String) -> Unit,
    onRemoveFriend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    var selectedUser by remember { mutableStateOf<String?>(null) }

    if (showDialog && selectedUser != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Chat Options") },
            text = { Text("What would you like to do with @$selectedUser?") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveFriend(selectedUser!!)
                    showDialog = false
                }) {
                    Text("Remove Friend", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onClearChat(selectedUser!!)
                    showDialog = false
                }) {
                    Text("Clear Messages")
                }
            }
        )
    }

    LazyColumn(modifier = modifier) {
        items(chats) { chat ->
            val otherUser = if (chat.userA == myUsername) chat.userB else chat.userA

            ChatRow(
                username = otherUser,
                lastMessage = chat.lastMessage,
                time = formatTime(chat.lastTimestamp),
                profileFile = profilePics[otherUser],
                modifier = Modifier.combinedClickable(
                    onClick = { onChatClick(otherUser) },
                    onLongClick = {
                        selectedUser = otherUser
                        showDialog = true
                    }
                )
            )
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val oneDay = 24 * 60 * 60 * 1000

    return when {
        diff < oneDay ->
            SimpleDateFormat("hh:mm a", Locale.getDefault())
                .format(Date(timestamp))

        diff < 2 * oneDay -> "Yesterday"

        else ->
            SimpleDateFormat("dd MMM", Locale.getDefault())
                .format(Date(timestamp))
    }
}
