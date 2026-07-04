package com.jhacode.chitrini.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.LifecycleEventObserver
import com.jhacode.chitrini.data.local.db.ChatDatabase
import com.jhacode.chitrini.data.repository.ChatRepositoryImpl
import com.jhacode.chitrini.network.SocketManager
import com.jhacode.chitrini.ui.add.AddPeopleScreen
import com.jhacode.chitrini.ui.chat.ChatScreen
import com.jhacode.chitrini.ui.chat.ChatViewModel
import com.jhacode.chitrini.ui.home.ChatListViewModel
import com.jhacode.chitrini.ui.home.HomeScreen
import com.jhacode.chitrini.ui.profile.ProfilePanel
import com.jhacode.chitrini.ui.profile.SettingsScreen
import com.jhacode.chitrini.ui.qr.QrScannerScreen
import com.jhacode.chitrini.ui.theme.ChitriniTheme
import com.jhacode.chitrini.ui.theme.WallpaperBackground
import com.jhacode.chitrini.ui.profile.SettingsViewModel
import com.jhacode.chitrini.repository.MainRepository
import com.jhacode.chitrini.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
enum class Screen { HOME, CHAT }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestPermissions()

        setContent {
            val context = LocalContext.current
            val settingsViewModel = remember { SettingsViewModel(context) }
            val themeMode by settingsViewModel.themeMode.collectAsState()

            ChitriniTheme(themeMode = themeMode) {
                val prefs = remember { context.getSharedPreferences("chitrini_prefs", MODE_PRIVATE) }
                
                val appWallpaperUri by settingsViewModel.appWallpaperUri.collectAsState()
                val appStockColor by settingsViewModel.appStockColor.collectAsState()
                val appBlur by settingsViewModel.appBlur.collectAsState()

                val myUsername = prefs.getString("username", "") ?: ""
                val mainRepository = remember { MainRepository.getInstance() }
                val scope = rememberCoroutineScope()

                val db = remember { ChatDatabase.getInstance(context) }
                val chatRepository = remember { ChatRepositoryImpl(db.messageDao(), db.chatDao()) }

                var currentScreen by remember { mutableStateOf(Screen.HOME) }
                var activeChatUser by remember { mutableStateOf<String?>(null) }
                var showProfile by remember { mutableStateOf(false) }
                var showAddPeople by remember { mutableStateOf(false) }
                var showQrScanner by remember { mutableStateOf(false) }
                var showSettings by remember { mutableStateOf(false) }

                // Online status & foreground tracking
                DisposableEffect(Unit) {
                    val obs = LifecycleEventObserver { _, event ->
                        when (event) {
                            androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                                scope.launch(Dispatchers.IO) { mainRepository.setUserStatus("online") }
                                AppState.isForeground = true
                            }
                            androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                                scope.launch(Dispatchers.IO) { mainRepository.setUserStatus("offline") }
                                AppState.isForeground = false
                            }
                            else -> {}
                        }
                    }
                    lifecycle.addObserver(obs)
                    onDispose { lifecycle.removeObserver(obs) }
                }

                // Global Signal Listener
                DisposableEffect(myUsername) {
                    if (myUsername.isBlank()) return@DisposableEffect onDispose {}
                    val listener = object : NewEventCallBack {
                        override fun onNewEventReceived(model: DataModel) {
                            when (model.type) {
                                DataModelType.ChatMessage -> {
                                    // 🔥 Service handles processing to avoid double sound and double DB saving
                                }
                                DataModelType.MessageDelivered -> {
                                    scope.launch(Dispatchers.IO) { chatRepository.updateMessageStatus(model.data, "delivered") }
                                }
                                DataModelType.MessageSeen -> {
                                    scope.launch(Dispatchers.IO) { chatRepository.updateMessageStatus(model.data, "seen") }
                                }
                                DataModelType.StartCall -> {
                                    scope.launch(Dispatchers.Main) {
                                        showQrScanner = false
                                        startActivity(Intent(context, CallActivity::class.java).apply {
                                            putExtra("target", model.sender)
                                            putExtra("isCaller", false)
                                            putExtra("isVideo", true)
                                        })
                                    }
                                }
                                DataModelType.StartAudioCall -> {
                                    scope.launch(Dispatchers.Main) {
                                        showQrScanner = false
                                        startActivity(Intent(context, CallActivity::class.java).apply {
                                            putExtra("target", model.sender)
                                            putExtra("isCaller", false)
                                            putExtra("isVideo", false)
                                        })
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                    mainRepository.subscribeForLatestEvent(listener)
                    onDispose { mainRepository.unsubscribeForLatestEvent(listener) }
                }

                LaunchedEffect(myUsername) {
                    if (myUsername.isNotBlank()) {
                        withContext(Dispatchers.IO) {
                            com.jhacode.chitrini.storage.AppwriteManager.init(context)
                            SocketManager.connect(myUsername)
                            mainRepository.login(myUsername, context) {}
                        }
                    }
                }

                val chatListViewModel = remember { ChatListViewModel(db.chatDao(), chatRepository, myUsername) }

                WallpaperBackground(
                    uri = appWallpaperUri,
                    stockColor = appStockColor,
                    isBlur = appBlur
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            when (currentScreen) {
                                Screen.HOME -> HomeScreen(chatListViewModel, myUsername, { showProfile = true }, { showAddPeople = true }, { u -> 
                                    AppState.currentChatUser = u
                                    AppState.isChatScreenActive = true
                                    activeChatUser = u
                                    currentScreen = Screen.CHAT 
                                })
                                Screen.CHAT -> {
                                    val chatId = if (myUsername < (activeChatUser?:"")) "${myUsername}_$activeChatUser" else "${activeChatUser}_$myUsername"
                                    val chatViewModel = remember(chatId) { ChatViewModel(chatId, myUsername, chatRepository) }
                                    
                                    LaunchedEffect(chatId) { chatViewModel.init(context) }

                                    ChatScreen(activeChatUser?:"", chatViewModel, { 
                                        AppState.currentChatUser = null
                                        AppState.isChatScreenActive = false
                                        currentScreen = Screen.HOME
                                        activeChatUser = null 
                                    }, 
                                        { activeChatUser?.let { u -> 
                                            mainRepository.sendCallRequest(u, false) {  }
                                            startActivity(Intent(context, CallActivity::class.java).apply { 
                                                putExtra("target", u)
                                                putExtra("isCaller", true)
                                                putExtra("isVideo", false)
                                            }) 
                                        } },
                                        { activeChatUser?.let { u -> 
                                            mainRepository.sendCallRequest(u, true) {  }
                                            startActivity(Intent(context, CallActivity::class.java).apply { 
                                                putExtra("target", u)
                                                putExtra("isCaller", true)
                                                putExtra("isVideo", true)
                                            }) 
                                        } }
                                    )
                                }
                            }
                        }

                        // 🔥 PROFILE PANEL - RIGHT SIDE SLIDE (FIXED ANCHOR)
                        val screenWidth = LocalConfiguration.current.screenWidthDp.dp
                        val panelWidth = screenWidth * 0.85f
                        
                        val offsetX by animateDpAsState(
                            targetValue = if (showProfile) 0.dp else panelWidth, 
                            animationSpec = tween(350),
                            label = "profileSlide"
                        )

                        // Scrim
                        if (showProfile) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(0.4f))
                                    .clickable { showProfile = false }
                            )
                        }

                        // FIXED: Actual panel box anchored to CenterEnd
                        if (showProfile || offsetX < panelWidth) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(panelWidth)
                                    .align(Alignment.CenterEnd)
                                    .offset(x = offsetX)
                                    .clickable(enabled = false) { }
                            ) {
                                ProfilePanel(
                                    onClose = { showProfile = false },
                                    onScanQr = { showProfile = false; showAddPeople = true },
                                    onShareQr = {},
                                    onSettingsClick = { showProfile = false; showSettings = true },
                                    chatListViewModel = chatListViewModel,
                                    settingsViewModel = settingsViewModel
                                )
                            }
                        }

                        if (showSettings) SettingsScreen { showSettings = false }
                        if (showAddPeople) AddPeopleScreen({ showAddPeople = false }, { u -> chatListViewModel.sendConnectRequest(u); showAddPeople = false; Toast.makeText(context, "Sent", Toast.LENGTH_SHORT).show() }, { showQrScanner = true })
                        if (showQrScanner) QrScannerScreen({ u -> chatListViewModel.sendConnectRequest(u); showQrScanner = false; showAddPeople = false }, { showQrScanner = false })

                        BackHandler(showProfile || showAddPeople || showQrScanner || showSettings || currentScreen == Screen.CHAT) {
                            when {
                                showSettings -> showSettings = false
                                showQrScanner -> showQrScanner = false
                                showAddPeople -> showAddPeople = false
                                showProfile -> showProfile = false
                                currentScreen == Screen.CHAT -> { 
                                    AppState.currentChatUser = null
                                    AppState.isChatScreenActive = false
                                    currentScreen = Screen.HOME
                                    activeChatUser = null 
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun playDiscreteSound(context: Context) {
        try {
            val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            val r = android.media.RingtoneManager.getRingtone(context, uri)
            r.play()
        } catch (e: Exception) {}
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        val toRequest = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (toRequest.isNotEmpty()) ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), 101)
    }
}
