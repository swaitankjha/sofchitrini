package com.jhacode.chitrini.utils

object AppState {
    var isForeground: Boolean = false
    var isCallActive: Boolean = false
    var isRinging: Boolean = false
    
    // 🔥 Track current chat state for single-source processing
    var currentChatUser: String? = null
    var isChatScreenActive: Boolean = false
}
