package com.jhacode.chitrini.network

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

object SocketManager {

    private const val TAG = "SocketManager"
    private const val SERVER_URL =
        "https://chitrini-server.onrender.com"

    private var socket: Socket? = null
    private var currentUser: String? = null

// =========================
// CALLBACKS
// =========================

    private val connectRequestCallbacks =
        mutableListOf<(String) -> Unit>()

    private val connectAcceptCallbacks =
        mutableListOf<(String) -> Unit>()

    private val messageCallbacks =
        mutableListOf<(String, String, String) -> Unit>()



    object Events {
        const val CONNECT_REQUEST = "connect_request"
        const val CONNECT_ACCEPT = "connect_accept"
        const val CHAT_MESSAGE = "chat_message"
    }



    fun connect(username: String) {

        if (
            socket?.connected() == true &&
            currentUser == username
        ) {
            Log.d(TAG, "Already connected as $username")
            return
        }

        Log.d(TAG, "Connecting for user: $username")
        disconnect()

        currentUser = username

        val options = IO.Options().apply {
            // Render and other modern hosts work better with websocket directly
            // as they might not support sticky sessions required for polling.
            transports = arrayOf("websocket")
            forceNew = true
            reconnection = true
            reconnectionAttempts = Int.MAX_VALUE
            reconnectionDelay = 1000
            timeout = 20000 // 20 seconds timeout
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
        }

        socket?.on("registered") {
            Log.d(TAG, ">>> REGISTERED SUCCESSFULLY AS $username <<<")
        }

        socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
            val err = args.getOrNull(0)
            Log.e(TAG, "CONNECT ERROR: $err")
        }

        socket?.on(Socket.EVENT_DISCONNECT) { reason ->
            Log.e(TAG, "SOCKET DISCONNECTED: ${reason.getOrNull(0)}")
        }

        // =========================
        // CONNECT REQUEST
        // =========================

        socket?.on(Events.CONNECT_REQUEST) { args ->
            try {
                val obj = args[0] as JSONObject
                val from = obj.getString("from")

                Log.d(TAG, "RECEIVED REQUEST FROM: $from")

                connectRequestCallbacks.forEach { callback ->
                    callback(from)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing CONNECT_REQUEST", e)
            }
        }

        // =========================
        // CONNECT ACCEPT
        // =========================

        socket?.on(Events.CONNECT_ACCEPT) { args ->
            try {
                val obj = args[0] as JSONObject
                val from = obj.getString("from")

                Log.d(TAG, "RECEIVED ACCEPT FROM: $from")

                connectAcceptCallbacks.forEach { callback ->
                    callback(from)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing CONNECT_ACCEPT", e)
            }
        }

        // =========================
        // CHAT MESSAGE
        // =========================

        socket?.on(Events.CHAT_MESSAGE) { args ->
            try {
                val obj = args[0] as JSONObject
                val chatId = obj.getString("chatId")
                val from = obj.getString("from")
                val message = obj.getString("message")

                Log.d(TAG, "RECEIVED MESSAGE: $message FROM: $from IN CHAT: $chatId")

                messageCallbacks.forEach { callback ->
                    callback(chatId, from, message)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing CHAT_MESSAGE", e)
            }
        }

        socket?.connect()
    }

// =========================
// DISCONNECT
// =========================

    fun disconnect() {

        socket?.disconnect()

        socket = null

        Log.d(TAG, "Disconnected")
    }

// =========================
// CONNECTION CHECK
// =========================

    private fun isConnected(): Boolean {

        val connected =
            socket?.connected() == true

        if (!connected) {

            Log.e(
                TAG,
                "Socket not connected"
            )
        }

        return connected
    }

// =========================
// SEND CONNECT REQUEST
// =========================

    fun sendConnectRequest(toUser: String) {

        Log.d(TAG, "TRY REQUEST -> $toUser from $currentUser")

        if (toUser.isBlank() || toUser == currentUser) {
            Log.w(TAG, "Invalid toUser: $toUser")
            return
        }

        if (!isConnected()) {
            Log.e(TAG, "Cannot send request: Not connected")
            return
        }

        val obj = JSONObject().apply {
            put("to", toUser)
            put("from", currentUser) // Include sender!
        }

        socket?.emit(Events.CONNECT_REQUEST, obj)
        Log.d(TAG, "REQUEST EMITTED to $toUser")
    }

    fun onConnectRequest(
        callback: (String) -> Unit
    ) {
        connectRequestCallbacks += callback
    }

// =========================
// ACCEPT REQUEST
// =========================

    fun acceptConnectRequest(fromUser: String) {

        if (!isConnected()) {
            Log.e(TAG, "Cannot accept request: Not connected")
            return
        }

        val obj = JSONObject().apply {
            put("to", fromUser)
            put("from", currentUser) // Include sender!
        }

        socket?.emit(Events.CONNECT_ACCEPT, obj)
        Log.d(TAG, "ACCEPT EMITTED to $fromUser")
    }

    fun onConnectAccepted(
        callback: (String) -> Unit
    ) {
        connectAcceptCallbacks += callback
    }

// =========================
// SEND MESSAGE
// =========================

    fun sendMessage(
        toChatId: String,
        from: String,
        message: String
    ) {

        if (!isConnected()) {
            return
        }

        val obj = JSONObject().apply {

            put("chatId", toChatId)

            put("from", from)

            put("message", message)
        }

        socket?.emit(
            Events.CHAT_MESSAGE,
            obj
        )

        Log.d(
            TAG,
            "MESSAGE SENT"
        )
    }

    fun onMessageReceived(
        callback: (
            String,
            String,
            String
        ) -> Unit
    ) {
        messageCallbacks += callback
    }


}
