package com.jhacode.chitrini.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPickerBottomSheet(
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val emojiList = listOf(
        "❤️", "👍", "🔥", "😂", "😮", "😢", "🙏", "✨", "🎉", "💯",
        "✅", "❌", "👏", "🙌", "🤔", "😎", "🤩", "😍", "🥳", "😡",
        "😭", "🙄", "😴", "💩", "👻", "💀", "👽", "👾", "🤖", "🎃",
        "🤝", "✌️", "🤞", "🤟", "🤘", "🤙", "🤚", "🖐️", "✋", "🖖",
        "👋", "✍️", "🤳", "💪", "🦾", "🦵", "🦶", "👂", "🦻", "👃",
        "🧠", "🫀", "🫁", "🦷", "🦴", "👀", "👁️", "👅", "👄", "👶",
        "🧒", "👦", "👧", "🧑", "👱", "👨", "🧔", "👩", "🧓", "👴",
        "👵", "🙍", "🙎", "🙅", "🙆", "💁", "🙋", "🧏", "🙇", "🤦",
        "🤷", "👮", "🕵️", "💂", "🥷", "👷", "🤴", "👸", "👳", "👲",
        "🍀", "🌿", "🌾", "🌵", "🌴", "🌳", "🌲", "🪴", "🪵", "🌱",
        "🌼", "🌸", "🌺", "🌹", "🌷", "🌻", "☀️", "☁️", "⛈️", "❄️",
        "🌍", "🌎", "🌏", "🌕", "🌛", "🌟", "🌙", "☄️", "🔥", "💧",
        "🍔", "🍕", "🍎", "🍓", "☕", "🍺", "🍷", "🥤", "🍦", "🍭"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Text(
                "Choose Reaction",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 56.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxWidth().height(400.dp)
            ) {
                items(emojiList) { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 32.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clickable { onEmojiSelected(emoji) }
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}
