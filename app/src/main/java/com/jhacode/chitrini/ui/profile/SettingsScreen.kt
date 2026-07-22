package com.jhacode.chitrini.ui.profile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jhacode.chitrini.network.SocketManager
import com.jhacode.chitrini.repository.MainRepository
import com.jhacode.chitrini.ui.LoginActivity
import com.jhacode.chitrini.update.manager.UpdateManager
import com.jhacode.chitrini.update.model.UpdateState
import com.jhacode.chitrini.update.repository.UpdateRepository
import com.jhacode.chitrini.update.ui.UpdateDialog
import com.jhacode.chitrini.update.viewmodel.UpdateViewModel
import com.jhacode.chitrini.utils.EncryptionManager
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("chitrini_prefs", Context.MODE_PRIVATE)
    
    val updateViewModel = remember {
        val client = OkHttpClient()
        val repo = UpdateRepository(client, "https://raw.githubusercontent.com/swaitankjha/sofchitrini/main/version.json")
        val manager = UpdateManager(context)
        val currentVersionCode = com.jhacode.chitrini.BuildConfig.VERSION_CODE
        UpdateViewModel(repo, manager, currentVersionCode)
    }
    val updateState by updateViewModel.state.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }

    val appBlur by settingsViewModel.appBlur.collectAsState()
    val chatBlur by settingsViewModel.chatBlur.collectAsState()

    // 🔥 Robust helper to save wallpaper to internal storage (Fixed)
    fun processWallpaperSelection(uri: Uri, type: String) {
        try {
            val folder = File(context.filesDir, "wallpapers")
            if (!folder.exists()) folder.mkdirs()
            val destFile = File(folder, "${type}_wallpaper.jpg")
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            val internalUriString = Uri.fromFile(destFile).toString()
            prefs.edit()
                .putString("${type}_wallpaper_uri", internalUriString)
                .putInt("${type}_stock_color", 0) // Clear color
                .apply()
            
            Toast.makeText(context, "Wallpaper set!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Selection failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    val appWallpaperLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { processWallpaperSelection(it, "app") }
    }

    val chatWallpaperLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { processWallpaperSelection(it, "chat") }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Account permanently?") },
            text = { Text("Are you sure? This will delete your existence on the app. The data can never be retrieved again.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        MainRepository.getInstance().deleteAccount {
                            SocketManager.disconnect()
                            context.deleteDatabase("chitrini_db")
                            EncryptionManager.deleteKeys()
                            prefs.edit().clear().apply()
                            context.startActivity(Intent(context, LoginActivity::class.java))
                            (context as Activity).finish()
                        }
                    }
                ) {
                    Text("Delete Permanently", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "PREFERENCES",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 2.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.primary) }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))

            SettingsHeader(icon = Icons.Default.Palette, title = "Visuals")
            
            SettingsCard {
                Column(Modifier.padding(16.dp)) {
                    Text("Theme Mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val currentTheme = prefs.getString("theme_mode", "System") ?: "System"
                        ThemeButton("Light", currentTheme == "Light", Modifier.weight(1f)) {
                            prefs.edit().putString("theme_mode", "Light").apply()
                        }
                        ThemeButton("Dark", currentTheme == "Dark", Modifier.weight(1f)) {
                            prefs.edit().putString("theme_mode", "Dark").apply()
                        }
                        ThemeButton("Auto", currentTheme == "System", Modifier.weight(1f)) {
                            prefs.edit().putString("theme_mode", "System").apply()
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            SettingsCard {
                Column(Modifier.padding(16.dp)) {
                    Text("Wallpapers", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    
                    WallpaperRow(
                        label = "App Interface", 
                        onGallery = { appWallpaperLauncher.launch("image/*") },
                        isBlur = appBlur,
                        onBlurChange = { prefs.edit().putBoolean("app_wallpaper_blur", it).apply() }
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    StockWallpaperPicker(title = "App Color Palette") { color ->
                        prefs.edit()
                            .putString("app_wallpaper_uri", "")
                            .putInt("app_stock_color", color)
                            .apply()
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    
                    WallpaperRow(
                        label = "Chat Screen", 
                        onGallery = { chatWallpaperLauncher.launch("image/*") },
                        isBlur = chatBlur,
                        onBlurChange = { prefs.edit().putBoolean("chat_wallpaper_blur", it).apply() }
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    StockWallpaperPicker(title = "Chat Color Palette") { color ->
                        prefs.edit()
                            .putString("chat_wallpaper_uri", "")
                            .putInt("chat_stock_color", color)
                            .apply()
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            SettingsHeader(icon = Icons.Default.PrivacyTip, title = "Privacy")

            SettingsCard {
                Column(Modifier.padding(vertical = 4.dp)) {
                    val typingStatus by settingsViewModel.showTypingStatus.collectAsState()
                    val onlineStatus by settingsViewModel.showOnlineStatus.collectAsState()

                    ToggleItem(
                        title = "Typing Indicator",
                        subtitle = "Others see when you type",
                        checked = typingStatus,
                        onCheckedChange = {
                            prefs.edit().putBoolean("show_typing_status", it).apply()
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    ToggleItem(
                        title = "Online Presence",
                        subtitle = "Show your active status",
                        checked = onlineStatus,
                        onCheckedChange = {
                            prefs.edit().putBoolean("show_online_status", it).apply()
                            val mainRepo = MainRepository.getInstance()
                            if (it) mainRepo.setUserStatus("online") else mainRepo.setUserStatus("offline")
                        }
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            SettingsHeader(icon = Icons.Default.Settings, title = "System")

            SettingsCard {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val versionName = com.jhacode.chitrini.BuildConfig.VERSION_NAME
                            Text("Chitrinī v$versionName", fontWeight = FontWeight.Black)
                            Text("Official Release", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }

                        Button(
                            onClick = { updateViewModel.checkForUpdates() },
                            shape = RoundedCornerShape(10.dp),
                            enabled = updateState !is UpdateState.Checking && updateState !is UpdateState.Downloading,
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text(if (updateState is UpdateState.Checking) "..." else "Check Update", fontSize = 12.sp)
                        }
                    }

                    if (updateState is UpdateState.UpToDate) {
                        Text("You're on the latest version", color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
                    }

                    if (updateState is UpdateState.Error) {
                        Text((updateState as UpdateState.Error).message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
                    }

                    if (updateState is UpdateState.Downloading) {
                        Column(Modifier.padding(top = 12.dp)) {
                            val progress = (updateState as UpdateState.Downloading).progress
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            TextButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(0.05f), contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Delete Account Permanently", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
    
    if (updateState is UpdateState.UpdateAvailable) {
        UpdateDialog(
            versionInfo = (updateState as UpdateState.UpdateAvailable).versionInfo,
            onUpdate = { updateViewModel.startDownload((updateState as UpdateState.UpdateAvailable).versionInfo.downloadUrl) },
            onDismiss = { updateViewModel.resetState() }
        )
    }
}

@Composable
fun SettingsHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun WallpaperRow(label: String, onGallery: () -> Unit, isBlur: Boolean, onBlurChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            TextButton(onClick = onGallery, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Default.Image, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Select Image", fontSize = 11.sp)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Blur", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
            Switch(checked = isBlur, onCheckedChange = onBlurChange, modifier = Modifier.scale(0.65f))
        }
    }
}

@Composable
fun ToggleItem(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Switch(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.85f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
fun ThemeButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label, 
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)),
        shadowElevation = 0.5.dp
    ) {
        content()
    }
}

@Composable
fun StockWallpaperPicker(title: String, onColorSelected: (Int) -> Unit) {
    val stockColors = listOf(
        0, 
        0xFF000000, 0xFF1C1C1E, 0xFFD4AF37, 
        0xFF007AFF, 0xFF5856D6, 0xFF28CD41, 
        0xFFFF2D55, 0xFFEAF5E6, 0xFFFFF8E7
    )
    
    Column {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(stockColors) { color ->
                Surface(
                    onClick = { onColorSelected(color.toInt()) },
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = if (color == 0L) Color.Transparent else Color(color),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ) {
                    if (color == 0L) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
