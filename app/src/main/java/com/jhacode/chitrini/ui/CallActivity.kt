package com.jhacode.chitrini.ui

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.jhacode.chitrini.repository.MainRepository
import com.jhacode.chitrini.ui.call.CallScreen
import com.jhacode.chitrini.utils.DataModelType
import com.jhacode.chitrini.utils.NewEventCallBack
import org.webrtc.SurfaceViewRenderer
import java.util.*
import com.jhacode.chitrini.service.ChitriniService
class CallActivity : ComponentActivity(), MainRepository.WebRTCRepositoryListener {

    private lateinit var mainRepository: MainRepository
    private var localView: SurfaceViewRenderer? = null
    private var remoteView: SurfaceViewRenderer? = null

    private var targetUser by mutableStateOf("")
    private var callStatus by mutableStateOf("Initializing...")
    private var timerText by mutableStateOf("00:00")
    private var isCallActive by mutableStateOf(false)
    private var isIncoming by mutableStateOf(false)
    private var isMicMuted by mutableStateOf(false)
    private var isVideoMuted by mutableStateOf(false)
    private var isVideoCall by mutableStateOf(true) // 🔥 Track if it's a video call
    private var chatMessages by mutableStateOf("")
    private var isInPiP by mutableStateOf(false)
    private var unreadCount by mutableStateOf(0)

    private val signalingCallback = object : NewEventCallBack {
        override fun onNewEventReceived(data: com.jhacode.chitrini.utils.DataModel) {
            when (data.type) {
                DataModelType.StartCall -> {
                    runOnUiThread {
                        targetUser = data.sender
                        isIncoming = true
                        isVideoCall = true
                        callStatus = "Incoming Video Call..."
                    }
                }
                DataModelType.StartAudioCall -> {
                    runOnUiThread {
                        targetUser = data.sender
                        isIncoming = true
                        isVideoCall = false
                        callStatus = "Incoming Audio Call..."
                    }
                }
                DataModelType.EndCall -> {
                    runOnUiThread { finish() }
                }
                else -> {}
            }
        }
    }

    private val timerHandler = Handler(Looper.getMainLooper())
    private var callStartTime: Long = 0
    private val timerRunnable = object : Runnable {
        override fun run() {
            val millis = System.currentTimeMillis() - callStartTime
            val seconds = (millis / 1000).toInt()
            val minutes = seconds / 60
            val secs = seconds % 60
            timerText = String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
            timerHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        mainRepository = MainRepository.getInstance()
        initViews()

        setContent {
            CallScreen(
                targetUser = targetUser,
                status = callStatus,
                timerText = timerText,
                isCallActive = isCallActive,
                isIncoming = isIncoming,
                isMicMuted = isMicMuted,
                isVideoMuted = if (isVideoCall) isVideoMuted else true, // 🔥 Force video off if audio call
                localView = localView!!,
                remoteView = remoteView!!,
                isInPiP = isInPiP,
                unreadCount = unreadCount,
                isVideoCall = isVideoCall, // 🔥 Pass flag
                onAccept = {

                    Intent(this, ChitriniService::class.java).apply {
                        action = "ACTION_STOP_RINGTONE"
                    }.also {
                        startService(it)
                    }

                    mainRepository.answerCall(targetUser, isVideoCall)
                    onCallStarted()
                },
                onReject = {
                    mainRepository.endCall()
                    finish()
                },
                onEndCall = {
                    mainRepository.endCall()
                    finish()
                },
                onToggleMic = {
                    isMicMuted = !isMicMuted
                    mainRepository.toggleAudio(isMicMuted)
                },
                onToggleVideo = {
                    if (isVideoCall) {
                        isVideoMuted = !isVideoMuted
                        mainRepository.toggleVideo(!isVideoMuted)
                    }
                },
                onSwitchCamera = { if (isVideoCall) mainRepository.switchCamera() },
                onFlipPip = { enterPipMode() },
                chatMessages = chatMessages,
                onSendMessage = { msg ->
                    chatMessages += "\nMe: $msg"
                    mainRepository.sendChatMessage(msg)
                }
            )
        }

        mainRepository.listener = this
        mainRepository.subscribeForLatestEvent(signalingCallback)
        handleIntent(intent)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isCallActive) enterPipMode()
                else finish()
            }
        })
    }

    private fun initViews() {
        localView = SurfaceViewRenderer(this)
        remoteView = SurfaceViewRenderer(this)
        mainRepository.initLocalView(localView)
        mainRepository.initRemoteView(remoteView)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.let {
            if (it.hasExtra("target")) {
                val target = it.getStringExtra("target") ?: ""
                val isCaller = it.getBooleanExtra("isCaller", false)
                isVideoCall = it.getBooleanExtra("isVideo", true) // 🔥 Get flag
                targetUser = target

                if (!isCaller) {
                    isIncoming = true
                    callStatus = if (isVideoCall) "Incoming Video Call..." else "Incoming Audio Call..."
                } else {
                    mainRepository.startCall(target, isVideoCall)
                    onCallStarted()
                }
            }
        }
    }

    private fun onCallStarted() {
        isCallActive = true
        isIncoming = false
        callStatus = "Connecting securely..."
    }

    private fun enterPipMode() {
        if (isVideoCall && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aspectRatio = Rational(9, 16)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isCallActive && isVideoCall) enterPipMode()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPiP = isInPictureInPictureMode
    }

    override fun webrtcConnected() {

        Intent(this, ChitriniService::class.java).apply {
            action = "ACTION_STOP_RINGTONE"
        }.also {
            startService(it)
        }

        runOnUiThread {
            callStatus = "Connected • Encrypted"
            startCallTimer()
        }
    }

    override fun webrtcClosed() {
        runOnUiThread {
            stopCallTimer()
            finish()
        }
    }

    override fun onChatMessageReceived(from: String?, message: String?) {
        runOnUiThread {
            chatMessages += "\n$from: $message"
            unreadCount++
        }
    }

    private fun startCallTimer() {
        if (callStartTime == 0L) {
            callStartTime = System.currentTimeMillis()
            timerHandler.postDelayed(timerRunnable, 0)
        }
    }

    private fun stopCallTimer() {
        timerHandler.removeCallbacks(timerRunnable)
        callStartTime = 0
    }

    override fun onDestroy() {
        stopCallTimer()
        mainRepository.unsubscribeForLatestEvent(signalingCallback)
        localView?.release()
        remoteView?.release()
        super.onDestroy()
    }
}