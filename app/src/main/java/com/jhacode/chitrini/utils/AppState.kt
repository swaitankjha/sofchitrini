package com.jhacode.chitrini.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AppState {
    var isForeground: Boolean = false
    var isCallActive: Boolean = false
    var isRinging: Boolean = false
    
    var currentChatUser: String? = null
    var isChatScreenActive: Boolean = false

    // 🔥 Hidden Friends State
    var isSelectionModeActive by mutableStateOf(false)
    var isHiddenChatsVisible by mutableStateOf(false)
}
