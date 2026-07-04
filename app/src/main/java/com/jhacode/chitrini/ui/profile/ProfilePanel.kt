package com.jhacode.chitrini.ui.profile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.jhacode.chitrini.network.SocketManager
import com.jhacode.chitrini.repository.MainRepository
import com.jhacode.chitrini.storage.MediaUploader
import com.jhacode.chitrini.ui.LoginActivity
import com.jhacode.chitrini.ui.home.ChatListViewModel
import com.jhacode.chitrini.ui.theme.WallpaperBackground
import com.jhacode.chitrini.utils.ProfilePicData
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ProfilePanel(
    onClose: () -> Unit,
    onScanQr: () -> Unit,
    onShareQr: () -> Unit,
    onSettingsClick: () -> Unit,
    chatListViewModel: ChatListViewModel,
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("chitrini_prefs", Context.MODE_PRIVATE)
    val myUsername = prefs.getString("username", "") ?: ""
    val scope = rememberCoroutineScope()

    var discreteMode by remember { mutableStateOf(prefs.getBoolean("discrete_mode", false)) }
    val myProfilePic by chatListViewModel.myProfilePic.collectAsState()
    
    var isUploading by remember { mutableStateOf(false) }

    val appWallpaperUri by settingsViewModel.appWallpaperUri.collectAsState()
    val appStockColor by settingsViewModel.appStockColor.collectAsState()
    val appBlur by settingsViewModel.appBlur.collectAsState()

    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()

    // 2. Crop Launcher
    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val resultUri = result.data?.let { UCrop.getOutput(it) }
            resultUri?.let { uri ->
                isUploading = true
                scope.launch {
                    val uploadResult = MediaUploader.uploadMedia(context, uri, "image/jpeg")
                    if (uploadResult != null) {
                        val data = ProfilePicData(uploadResult.fileId, uploadResult.encryptedKey, uploadResult.iv)
                        MainRepository.getInstance().storeProfilePic(Gson().toJson(data))
                        chatListViewModel.refreshMyProfilePic(context)
                    }
                    isUploading = false
                }
            }
        }
    }

    // 1. Gallery Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { sourceUri ->
            val destinationUri = Uri.fromFile(File(context.cacheDir, "cropped_profile_${System.currentTimeMillis()}.jpg"))
            val options = UCrop.Options()
            options.setCompressionFormat(android.graphics.Bitmap.CompressFormat.JPEG)
            options.setCompressionQuality(90)
            options.setHideBottomControls(false)
            options.setFreeStyleCropEnabled(true)
            options.setToolbarColor(primaryColor)
            options.setStatusBarColor(primaryColor)
            options.setActiveControlsWidgetColor(primaryColor)

            val uCropIntent = UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(1f, 1f)
                .withMaxResultSize(1000, 1000)
                .withOptions(options)
                .getIntent(context)

            cropLauncher.launch(uCropIntent)
        }
    }

    WallpaperBackground(
        uri = appWallpaperUri,
        stockColor = appStockColor,
        isBlur = appBlur
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            ProfileHeader(
                username = "@$myUsername",
                profileFile = myProfilePic,
                onClose = onClose,
                onProfileClick = { if (!isUploading) galleryLauncher.launch("image/*") }
            )

            if (isUploading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(20.dp))

            ProfileQRSection(
                username = "@$myUsername",
                onScanQr = onScanQr,
                onShareQr = onShareQr
            )

            Spacer(Modifier.height(24.dp))

            Text(
                "Preferences",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SettingsItem(
                icon = Icons.Default.Settings,
                title = "Account Settings",
                subtitle = "Security and data",
                onClick = onSettingsClick
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            NotificationToggle(
                title = "Discrete Mode",
                subtitle = if (discreteMode) "Stealth alerts" else "Normal alerts",
                checked = discreteMode,
                onCheckedChange = { 
                    discreteMode = it
                    prefs.edit().putBoolean("discrete_mode", it).apply()
                }
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    SocketManager.disconnect()
                    prefs.edit().clear().apply()
                    context.startActivity(Intent(context, LoginActivity::class.java))
                    (context as Activity).finish()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Logout Session", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, 
                    null, 
                    tint = MaterialTheme.colorScheme.primary, 
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun NotificationToggle(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.NotificationsActive, 
                null, 
                tint = MaterialTheme.colorScheme.primary, 
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.scale(0.75f)
        )
    }
}
