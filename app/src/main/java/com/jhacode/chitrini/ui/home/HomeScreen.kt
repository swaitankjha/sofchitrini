package com.jhacode.chitrini.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File
import androidx.compose.foundation.layout.statusBarsPadding
import com.jhacode.chitrini.utils.AppState
import com.jhacode.chitrini.utils.HiddenUsersHelper
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ChatListViewModel,
    myUsername: String,
    onProfileClick: () -> Unit,
    onAddPeopleClick: () -> Unit,
    onChatClick: (String) -> Unit
) {
    val chats by viewModel.chats.collectAsState(initial = emptyList())
    val requests by viewModel.incomingRequests.collectAsState()
    val profilePics by viewModel.profilePics.collectAsState()
    val myProfilePic by viewModel.myProfilePic.collectAsState()
    val context = LocalContext.current

    var hiddenUsernames by remember { mutableStateOf(HiddenUsersHelper.getHiddenUsers(context)) }
    val selectedToHide = remember { mutableStateListOf<String>() }

    var showChatOptions by remember { mutableStateOf(false) }
    var selectedUserForOptions by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(AppState.isSelectionModeActive) {
        if (!AppState.isSelectionModeActive) {
            selectedToHide.clear()
        } else {
            selectedToHide.addAll(hiddenUsernames)
        }
    }

    val filteredChats = remember(chats, hiddenUsernames, AppState.isHiddenChatsVisible, AppState.isSelectionModeActive) {
        if (AppState.isSelectionModeActive || AppState.isHiddenChatsVisible) {
            chats
        } else {
            chats.filter { chat ->
                val otherUser = if (chat.userA == myUsername) chat.userB else chat.userA
                !hiddenUsernames.contains(otherUser)
            }
        }
    }

    LaunchedEffect(chats, requests) {
        viewModel.initProfilePics(context)
    }

    if (showChatOptions && selectedUserForOptions != null) {
        AlertDialog(
            onDismissRequest = { showChatOptions = false },
            title = { Text("Options for @$selectedUserForOptions") },
            text = { Text("Manage your interaction with this contact.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeFriend(selectedUserForOptions!!)
                    showChatOptions = false
                }) {
                    Text("Remove Friend", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.clearChatMessages(selectedUserForOptions!!)
                    showChatOptions = false
                }) {
                    Text("Clear Chat")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            HomeTopBar(
                onProfileClick = onProfileClick,
                profileFile = myProfilePic,
                isSelectionMode = AppState.isSelectionModeActive,
                onConfirmHiding = {
                    HiddenUsersHelper.setHiddenUsers(context, selectedToHide.toSet())
                    hiddenUsernames = selectedToHide.toSet()
                    AppState.isSelectionModeActive = false
                    AppState.isHiddenChatsVisible = false
                }
            )
        },
        floatingActionButton = {
            if (!AppState.isSelectionModeActive) {
                FloatingActionButton(
                    onClick = onAddPeopleClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .padding(bottom = 56.dp, end = 4.dp)
                        .size(48.dp),
                    elevation = FloatingActionButtonDefaults.elevation(6.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(24.dp))
                }
            }
        },
        containerColor = Color.Transparent // 🔥 MUST BE TRANSPARENT FOR WALLPAPER
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)) {
                    QuoteCard()
                }
                Spacer(Modifier.height(4.dp))
            }

            if (requests.isNotEmpty() && !AppState.isSelectionModeActive) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            "PENDING",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
                items(requests) { user ->
                    Box(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                        IncomingRequestItem(
                            username = user,
                            profileFile = profilePics[user],
                            onAccept = { viewModel.acceptRequest(user) },
                            onReject = { viewModel.rejectRequest(user) }
                        )
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (AppState.isSelectionModeActive) "Manage Visibility" else "Messages",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (!AppState.isSelectionModeActive) {
                        Text(
                            text = "${filteredChats.size} active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(0.7f)
                        )
                    }
                }
            }

            items(filteredChats, key = { it.chatId }) { chat ->
                val otherUser = if (chat.userA == myUsername) chat.userB else chat.userA
                val isSelected = selectedToHide.contains(otherUser)
                
                ChatRow(
                    username = otherUser,
                    lastMessage = chat.lastMessage,
                    time = formatTime(chat.lastTimestamp),
                    profileFile = profilePics[otherUser],
                    isSelectionMode = AppState.isSelectionModeActive,
                    isSelected = isSelected,
                    onSelectedChange = { sel ->
                        if (sel) selectedToHide.add(otherUser) else selectedToHide.remove(otherUser)
                    },
                    onClick = {
                        if (!AppState.isSelectionModeActive) {
                            onChatClick(otherUser)
                        }
                    },
                    onLongClick = {
                        if (!AppState.isSelectionModeActive) {
                            selectedUserForOptions = otherUser
                            showChatOptions = true
                        }
                    }
                )
            }
            
            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    if (timestamp == 0L) return ""
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

@Composable
fun IncomingRequestItem(
    username: String,
    profileFile: File?,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), // 🔥 Semi-translucent
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    if (profileFile != null) {
                        AsyncImage(model = profileFile, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Text(username.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(username, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            }

            Row {
                IconButton(onClick = onReject, modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), CircleShape)) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onAccept, modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) {
                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun HomeTopBar(
    onProfileClick: () -> Unit, 
    profileFile: File?,
    isSelectionMode: Boolean = false,
    onConfirmHiding: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "CHITRINĪ",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp
            )
        }

        if (isSelectionMode) {
            Button(
                onClick = onConfirmHiding,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Text("DONE", fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
        } else {
            Surface(
                onClick = onProfileClick,
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            ) {
                if (profileFile != null) {
                    AsyncImage(model = profileFile, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Default.AccountCircle, null, modifier = Modifier.padding(8.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
