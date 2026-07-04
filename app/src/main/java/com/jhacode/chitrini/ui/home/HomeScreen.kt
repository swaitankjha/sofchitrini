package com.jhacode.chitrini.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
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

    LaunchedEffect(chats, requests) {
        viewModel.initProfilePics(context)
    }

    Scaffold(
        topBar = {
            HomeTopBar(onProfileClick, myProfilePic)
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddPeopleClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Text("+", fontSize = 24.sp)
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            QuoteCard()

            Spacer(Modifier.height(8.dp))

            if (requests.isNotEmpty()) {
                Text(
                    text = "Requests",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                ) {
                    items(requests) { user ->
                        IncomingRequestItem(
                            username = user,
                            profileFile = profilePics[user],
                            onAccept = { viewModel.acceptRequest(user) },
                            onReject = { viewModel.rejectRequest(user) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Text(
                text = "Recent",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            ChatList(
                chats = chats,
                myUsername = myUsername,
                profilePics = profilePics,
                onChatClick = onChatClick,
                onClearChat = { viewModel.clearChatMessages(it) },
                onRemoveFriend = { viewModel.removeFriend(it) },
                modifier = Modifier.weight(1f)
            )
        }
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (profileFile != null) {
                        AsyncImage(
                            model = profileFile,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = username.take(1).uppercase(), 
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "@$username", 
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp
                )
            }

            Row {
                IconButton(onClick = onReject, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, null, tint = Color.Red.copy(0.7f), modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onAccept, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun HomeTopBar(onProfileClick: () -> Unit, profileFile: File?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(72.dp)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Chitrinī",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 22.sp
            )
        }

        IconButton(
            onClick = onProfileClick,
            modifier = Modifier.size(48.dp).padding(top = 3.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (profileFile != null) {
                        AsyncImage(
                            model = profileFile,
                            contentDescription = "Profile",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Profile",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}
