package com.jhacode.chitrini.network

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

object SocketManager {

    private const val TAG = "SocketManager"
    private const val SERVER_URL = "https://chitrini-server.onrender.com"

    private var socket: Socket? = null
    private var currentUser: String? = null
    private var lastJoinedChatId: String? = null // 🔥 Store to re-join on reconnect

// =========================
// CALLBACKS
// =========================

    private val connectRequestCallbacks = mutableListOf<(String) -> Unit>()
    private val connectAcceptCallbacks = mutableListOf<(String) -> Unit>()
    private val messageCallbacks = mutableListOf<(String, String, String) -> Unit>()
    private val typingCallbacks = mutableListOf<(String, String, Boolean) -> Unit>()

    object Events {
        const val CONNECT_REQUEST = "connect_request"
        const val CONNECT_ACCEPT = "connect_accept"
        const val CHAT_MESSAGE = "chat_message"
        const val TYPING = "typing"
        const val JOIN_CHAT = "join_chat"
        const val LEAVE_CHAT = "leave_chat"
    }

    fun connect(username: String) {
        if (socket?.connected() == true && currentUser == username) {
            Log.d(TAG, "Already connected as $username")
            return
        }

        disconnect()
        currentUser = username

        val options = IO.Options().apply {
            transports = arrayOf("websocket")
            forceNew = true
            reconnection = true
            reconnectionAttempts = Int.MAX_VALUE
            reconnectionDelay = 1000
            timeout = 20000
        }

        try {
            socket = IO.socket(SERVER_URL, options)
        } catch (e: Exception) {
            Log.e(TAG, "Socket initialization failed", e)
            return
        }

        socket?.on(Socket.EVENT_CONNECT) {
            Log.d(TAG, ">>> SOCKET CONNECTED <<<")
            socket?.emit("register", username)
            
            // 🔥 RE-JOIN LAST CHAT ROOM IF ANY
            lastJoinedChatId?.let { cid ->
                socket?.emit(Events.JOIN_CHAT, cid)
                Log.d(TAG, "Re-joined chat room: $cid")
            }
        }

        socket?.on("registered") {
            Log.d(TAG, ">>> REGISTERED SUCCESSFULLY AS $username <<<")
        }

        socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
            Log.e(TAG, "CONNECT ERROR: ${args.getOrNull(0)}")
        }

        socket?.on(Events.CONNECT_REQUEST) { args ->
            try {
                val obj = args[0] as JSONObject
                connectRequestCallbacks.forEach { it(obj.getString("from")) }
            } catch (e: Exception) {}
        }

        socket?.on(Events.CONNECT_ACCEPT) { args ->
            try {
                val obj = args[0] as JSONObject
                connectAcceptCallbacks.forEach { it(obj.getString("from")) }
            } catch (e: Exception) {}
        }

        socket?.on(Events.CHAT_MESSAGE) { args ->
            try {
                val obj = args[0] as JSONObject
                messageCallbacks.forEach { it(obj.getString("chatId"), obj.getString("from"), obj.getString("message")) }
            } catch (e: Exception) {}
        }

        socket?.on(Events.TYPING) { args ->
            try {
                val obj = args[0] as JSONObject
                val cid = obj.getString("chatId")
                val from = obj.getString("from")
                val isTyping = obj.getBoolean("isTyping")
                typingCallbacks.forEach { it(cid, from, isTyping) }
            } catch (e: Exception) {
                Log.e(TAG, "Typing parse error", e)
            }
        }

        socket?.connect()
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
        Log.d(TAG, "Disconnected")
    }

    private fun isConnected() = socket?.connected() == true

    fun sendConnectRequest(toUser: String) {
        if (toUser.isBlank() || toUser == currentUser || !isConnected()) return
        val obj = JSONObject().apply {
            put("to", toUser)
            put("from", currentUser)
        }
        socket?.emit(Events.CONNECT_REQUEST, obj)
    }

    fun onConnectRequest(callback: (String) -> Unit) { connectRequestCallbacks += callback }

    fun acceptConnectRequest(fromUser: String) {
        if (!isConnected()) return
        val obj = JSONObject().apply {
            put("to", fromUser)
            put("from", currentUser)
        }
        socket?.emit(Events.CONNECT_ACCEPT, obj)
    }

    fun onConnectAccepted(callback: (String) -> Unit) { connectAcceptCallbacks += callback }

    fun onMessageReceived(callback: (String, String, String) -> Unit) { messageCallbacks += callback }

    fun sendTyping(chatId: String, from: String, to: String, isTyping: Boolean) {
        if (!isConnected()) return
        val obj = JSONObject().apply {
            put("chatId", chatId)
            put("from", from)
            put("to", to) // 🔥 Added target user for better routing
            put("isTyping", isTyping)
        }
        socket?.emit(Events.TYPING, obj)
        Log.d(TAG, "Emitted typing state: $isTyping for $chatId to $to")
    }

    fun onTypingStatusChanged(callback: (String, String, Boolean) -> Unit) {
        typingCallbacks += callback
    }

    fun removeTypingStatusListener(callback: (String, String, Boolean) -> Unit) {
        typingCallbacks -= callback
    }

    fun joinChat(chatId: String) {
        lastJoinedChatId = chatId
        if (!isConnected()) return
        socket?.emit(Events.JOIN_CHAT, chatId)
        Log.d(TAG, "Joined chat room: $chatId")
    }

    fun leaveChat(chatId: String) {
        lastJoinedChatId = null
        if (!isConnected()) return
        socket?.emit(Events.LEAVE_CHAT, chatId)
        Log.d(TAG, "Left chat room: $chatId")
    }
}
