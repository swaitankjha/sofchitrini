package com.jhacode.chitrini.ui.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.util.Patterns
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.gson.Gson
import com.jhacode.chitrini.data.local.entity.MessageEntity
import com.jhacode.chitrini.storage.MediaDownloader
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    isMe: Boolean,
    fontSizeMultiplier: Float = 1f,
    showTail: Boolean = true,
    isSelected: Boolean = false,
    onLongClick: () -> Unit = {},
    onImageClick: (File) -> Unit = {},
    onReply: (MessageEntity) -> Unit = {},
    onReact: (String) -> Unit = {},
    onMoreEmoji: () -> Unit = {},
    onEdit: (MessageEntity) -> Unit = {}
) {
    val isDeleted = message.text == "[deleted_by_sender]"
    val context = LocalContext.current
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val gson = remember { Gson() }
    
    var localFile by remember { mutableStateOf<File?>(null) }
    var isDownloading by remember { mutableStateOf(false) }

    val reactions = remember(message.reactions) {
        try {
            message.reactions?.let { 
                val map = gson.fromJson(it, Map::class.java)
                map as Map<String, String> 
            } ?: emptyMap()
        } catch (e: Exception) { emptyMap() }
    }

    var showReactionPicker by remember { mutableStateOf(false) }
    var offsetX by remember { mutableStateOf(0f) }

    LaunchedEffect(message.fileId, isDeleted) {
        if (message.fileId != null && !isDeleted) {
            isDownloading = true
            localFile = MediaDownloader.downloadMedia(
                context, message.fileId, message.encryptedKey ?: "", message.iv ?: ""
            )
            isDownloading = false
        } else {
            localFile = null
        }
    }

    val bubbleShape = if (isMe) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = if (showTail) 2.dp else 16.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (showTail) 2.dp else 16.dp, bottomEnd = 16.dp)
    }

    val baseBubbleColor = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val bubbleColor = if (isSelected) baseBubbleColor.copy(alpha = 0.7f) else baseBubbleColor
    val onBubbleColor = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
    ) {
        
        if (offsetX > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .size(32.dp)
                    .graphicsLayer {
                        alpha = (offsetX / 60f).coerceIn(0f, 1f)
                        scaleX = (offsetX / 60f).coerceIn(0.5f, 1f)
                        scaleY = (offsetX / 60f).coerceIn(0.5f, 1f)
                    }
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.Reply, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = if (showTail) 2.dp else 0.5.dp)
                .offset { IntOffset(offsetX.roundToInt(), 0) },
            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
        ) {
            Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
                
                Surface(
                    color = bubbleColor,
                    shape = bubbleShape,
                    shadowElevation = if (isMe) 1.dp else 0.dp,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clip(bubbleShape)
                        .combinedClickable(
                            onClick = {
                                if ((message.messageType == "IMAGE" || message.messageType == "VIDEO") && localFile != null) {
                                    onImageClick(localFile!!)
                                }
                            },
                            onLongClick = { 
                                if (!isDeleted) {
                                    onLongClick()
                                    showReactionPicker = true 
                                }
                            }
                        )
                        .pointerInput(message.messageId) {
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    if (dragAmount > 0) {
                                        offsetX = (offsetX + dragAmount).coerceAtMost(80f)
                                    } else {
                                        offsetX = (offsetX + dragAmount).coerceAtLeast(0f)
                                    }
                                },
                                onDragEnd = {
                                    if (offsetX >= 60f) { onReply(message) }
                                    offsetX = 0f
                                },
                                onDragCancel = { offsetX = 0f }
                            )
                        }
                ) {
                    Column(modifier = Modifier.padding(if (message.messageType == "IMAGE" || message.messageType == "VIDEO") 3.dp else 8.dp)) {
                        
                        if (message.replyToId != null && !isDeleted) {
                            Surface(
                                color = onBubbleColor.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(bottom = 6.dp).fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.width(2.5.dp).height(24.dp).background(onBubbleColor, RoundedCornerShape(2.dp)))
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = message.replyToSender ?: "",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = onBubbleColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                        )
                                        Text(
                                            text = message.replyToText ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = onBubbleColor.copy(alpha = 0.7f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        if (isDeleted) {
                            Text(
                                text = "Deleted message",
                                color = onBubbleColor.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                fontSize = 11.sp
                            )
                        } else {
                            when (message.messageType) {
                                "IMAGE", "VIDEO" -> {
                                    Column {
                                        if (localFile != null) {
                                            Box(contentAlignment = Alignment.Center) {
                                                AsyncImage(
                                                    model = localFile,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
                                                    contentScale = ContentScale.FillWidth
                                                )
                                                if (message.messageType == "VIDEO") {
                                                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(40.dp).background(Color.Black.copy(0.3f), CircleShape).padding(6.dp))
                                                }
                                            }
                                        }
                                        if (message.text.isNotEmpty() && message.text != "[Media]") {
                                            Spacer(Modifier.height(4.dp))
                                            LinkifiedText(text = message.text, color = onBubbleColor, multiplier = fontSizeMultiplier)
                                        }
                                    }
                                }
                                else -> {
                                    LinkifiedText(text = message.text, color = onBubbleColor, multiplier = fontSizeMultiplier)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.align(Alignment.End).padding(top = 2.dp), 
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (message.isEdited && !isDeleted) {
                                Text(
                                    text = "edited ",
                                    fontSize = 8.sp,
                                    color = onBubbleColor.copy(alpha = 0.5f),
                                    fontStyle = FontStyle.Italic
                                )
                            }
                            Text(
                                text = timeFormatter.format(Date(message.timestamp)), 
                                fontSize = 9.sp, 
                                color = onBubbleColor.copy(alpha = 0.6f)
                            )
                            if (isMe && !isDeleted) {
                                Spacer(Modifier.width(3.dp))
                                Icon(
                                    imageVector = if (message.status == "seen") Icons.Default.DoneAll else Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = if (message.status == "seen") Color(0xFF4CAF50) else onBubbleColor.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
                
                // Reaction Badges
                if (reactions.isNotEmpty() && !isDeleted) {
                    Row(modifier = Modifier.padding(top = 1.dp)) {
                        reactions.values.distinct().take(3).forEach { emoji ->
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = CircleShape,
                                shadowElevation = 1.dp,
                                modifier = Modifier.padding(end = 1.dp).size(18.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = emoji, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (showReactionPicker) {
                Popup(
                    alignment = if (isMe) Alignment.TopEnd else Alignment.TopStart,
                    offset = IntOffset(0, -60),
                    onDismissRequest = { showReactionPicker = false },
                    properties = PopupProperties(focusable = true, dismissOnClickOutside = true, dismissOnBackPress = true)
                ) {
                    Surface(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(24.dp),
                        shadowElevation = 12.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            // Row 1: Emojis
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val emojis = listOf("❤️", "👍", "😂", "😮", "😢", "🙏")
                                emojis.forEach { emoji ->
                                    Text(
                                        text = emoji, 
                                        fontSize = 24.sp,
                                        modifier = Modifier.clickable { onReact(emoji); showReactionPicker = false }
                                    )
                                }
                                IconButton(onClick = { onMoreEmoji(); showReactionPicker = false }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LinkifiedText(text: String, color: Color, multiplier: Float) {
    var showInAppBrowser by remember { mutableStateOf<String?>(null) }
    
    val annotatedString = buildAnnotatedString {
        val matcher = Patterns.WEB_URL.matcher(text)
        var lastIndex = 0
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            
            append(text.substring(lastIndex, start))
            
            pushStringAnnotation(tag = "URL", annotation = text.substring(start, end))
            withStyle(style = SpanStyle(color = Color(0xFF1976D2), textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold)) {
                append(text.substring(start, end))
            }
            pop()
            lastIndex = end
        }
        append(text.substring(lastIndex))
    }

    Column {
        Text(
            text = annotatedString,
            color = color,
            fontSize = (14 * multiplier).sp,
            lineHeight = (18 * multiplier).sp
        )
        
        val firstUrl = remember(text) {
            val m = Patterns.WEB_URL.matcher(text)
            if (m.find()) text.substring(m.start(), m.end()) else null
        }

        if (firstUrl != null) {
            LinkPreviewCard(url = firstUrl, onOpen = { showInAppBrowser = firstUrl })
        }
    }

    if (showInAppBrowser != null) {
        InAppBrowserDialog(url = showInAppBrowser!!, onDismiss = { showInAppBrowser = null })
    }
}

@Composable
fun LinkPreviewCard(url: String, onOpen: () -> Unit) {
    val isYouTube = url.contains("youtube.com") || url.contains("youtu.be")
    
    Surface(
        modifier = Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .clickable { onOpen() },
        color = Color.Black.copy(alpha = 0.04f),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.Black.copy(0.08f))
    ) {
        Row(modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isYouTube) Icons.Default.PlayCircle else Icons.Default.Link,
                contentDescription = null,
                tint = if (isYouTube) Color.Red else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = if (isYouTube) "YouTube" else "Link",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    fontSize = 9.sp
                )
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun InAppBrowserDialog(url: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false // 🔥 Allows full width
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f) // Slightly less than full width for "floating" feel
                .fillMaxHeight(0.9f)
                .shadow(16.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "In-App View", 
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            url, 
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row {
                        val context = LocalContext.current
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                
                // WebView with proper layout
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                webViewClient = WebViewClient()
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                }
                                loadUrl(url)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
