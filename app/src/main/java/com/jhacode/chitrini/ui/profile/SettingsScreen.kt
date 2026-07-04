package com.jhacode.chitrini.ui.profile

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jhacode.chitrini.network.SocketManager
import com.jhacode.chitrini.repository.MainRepository
import com.jhacode.chitrini.ui.LoginActivity
import com.jhacode.chitrini.utils.EncryptionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("chitrini_prefs", Context.MODE_PRIVATE)
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Wallpaper States
    var appWallpaperUri by remember { mutableStateOf(prefs.getString("app_wallpaper_uri", "")) }
    var chatWallpaperUri by remember { mutableStateOf(prefs.getString("chat_wallpaper_uri", "")) }
    var appBlur by remember { mutableStateOf(prefs.getBoolean("app_wallpaper_blur", false)) }
    var chatBlur by remember { mutableStateOf(prefs.getBoolean("chat_wallpaper_blur", false)) }

    val appWallpaperLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            appWallpaperUri = it.toString()
            prefs.edit().putString("app_wallpaper_uri", it.toString()).apply()
        }
    }

    val chatWallpaperLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            chatWallpaperUri = it.toString()
            prefs.edit().putString("chat_wallpaper_uri", it.toString()).apply()
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Account permanently?") },
            text = { 
                Text("Are you sure? This will delete your existence on the app. The data can never be retrieved again.") 
            },
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
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Settings",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "Customize your Chitrini experience",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .padding(vertical = 18.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 🔥 Theme Section
            Column {

                Text(
                    "Appearance",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Choose how Chitrini looks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            
            SettingsCard {
                Column(Modifier.padding(12.dp)) {
                    Text("Theme Mode", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val currentTheme = prefs.getString("theme_mode", "System") ?: "System"
                        ThemeOption("Light", currentTheme == "Light") {
                            prefs.edit().putString("theme_mode", "Light").apply()
                        }
                        ThemeOption("Dark", currentTheme == "Dark") {
                            prefs.edit().putString("theme_mode", "Dark").apply()
                        }
                        ThemeOption("System", currentTheme == "System") {
                            prefs.edit().putString("theme_mode", "System").apply()
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 🔥 Wallpaper Section
            Text("Wallpaper", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            
            SettingsCard {
                Column(Modifier.padding(12.dp)) {
                    Column {

                        Text(
                            "App Background",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            "Choose wallpaper for the app",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { appWallpaperLauncher.launch("image/*") }, 
                            Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Image, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Gallery")
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Blur", style = MaterialTheme.typography.labelSmall)
                            Switch(checked = appBlur, onCheckedChange = { 
                                appBlur = it
                                prefs.edit().putBoolean("app_wallpaper_blur", it).apply()
                            })
                        }
                    }
                    
                    StockWallpaperPicker { color ->
                        appWallpaperUri = ""
                        prefs.edit().putString("app_wallpaper_uri", "").putInt("app_stock_color", color).apply()
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            SettingsCard {
                Column(Modifier.padding(12.dp)) {
                    Column {

                        Text(
                            "Chat Background",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            "Choose wallpaper for the app",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { chatWallpaperLauncher.launch("image/*") }, 
                            Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Image, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Gallery")
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Blur", style = MaterialTheme.typography.labelSmall)
                            Switch(checked = chatBlur, onCheckedChange = { 
                                chatBlur = it
                                prefs.edit().putBoolean("chat_wallpaper_blur", it).apply()
                            })
                        }
                    }
                    StockWallpaperPicker { color ->
                        chatWallpaperUri = ""
                        prefs.edit().putString("chat_wallpaper_uri", "").putInt("chat_stock_color", color).apply()
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Account Security",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            SettingsCard {
                ListItem(
                    headlineContent = { 
                        Text("Permanently delete account", color = Color.Red, fontWeight = FontWeight.Bold) 
                    },
                    supportingContent = { 
                        Text("Wipe all data from server and phone") 
                    },
                    leadingContent = {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color.Red)
                    },
                    modifier = Modifier.clickable { showDeleteDialog = true },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Chitrini v2.0 Production",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Default.ColorLens, null, Modifier.size(18.dp)) }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(28.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        content()
    }
}

@Composable
fun StockWallpaperPicker(onColorSelected: (Int) -> Unit) {
    val stockColors = listOf(
        0xFFEAF5E6, // 🌿 Soft Pista (Recommended Default)
    0xFFF2FAF1, // 🍃 Mint Cream
    0xFFF8F4EC, // 🥂 Champagne
    0xFFFCFAF5, // 🤍 Soft Cream
    0xFFF0F7EA, // 🍵 Matcha Milk
    0xFFFFF8EB, // 🌼 Vanilla
    0xFFEAF2F8, // 🩵 Powder Blue
    0xFFEFEAF8, // 💜 Lavender Mist
    0xFFE8F6F1, // 🌊 Seafoam
    0xFFFFF0F8  // 🌸 Pink Mist
    )
    
    Column(Modifier.padding(top = 12.dp)) {
        Text(
            "Premium Colors",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(stockColors) { color ->
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .clickable { onColorSelected(color.toInt()) }
                        .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape)
                )
            }
        }
    }
}
