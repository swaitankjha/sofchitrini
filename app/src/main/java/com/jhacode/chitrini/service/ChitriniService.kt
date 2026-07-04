package com.jhacode.chitrini.service

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jhacode.chitrini.R
import com.jhacode.chitrini.data.local.db.ChatDatabase
import com.jhacode.chitrini.data.local.entity.MessageEntity
import com.jhacode.chitrini.data.repository.ChatRepositoryImpl
import com.jhacode.chitrini.repository.MainRepository
import com.jhacode.chitrini.ui.CallActivity
import com.jhacode.chitrini.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ChitriniService : Service() {

    private val CHANNEL_ID = "CHITRINI_SERVICE"
    private val MSG_CHANNEL_ID = "CHITRINI_MSG"
    private val CALL_CHANNEL_ID = "CHITRINI_CALL"

    private lateinit var mainRepository: MainRepository
    private lateinit var chatRepository: ChatRepositoryImpl
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var currentRingtone: Ringtone? = null
    private var listenerStarted = false
    private var ringingCallSender: String? = null

    override fun onCreate() {
        super.onCreate()
        mainRepository = MainRepository.getInstance()
        val db = ChatDatabase.getInstance(this)
        chatRepository = ChatRepositoryImpl(db.messageDao(), db.chatDao())
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (intent?.action == "ACTION_STOP_RINGTONE") {
            stopRingtone()
            NotificationManagerCompat.from(this).cancel(2)
            return START_NOT_STICKY
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Chitrini Sync")
            .setContentText("Active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

        startForeground(1, notification)

        if (!listenerStarted) {
            listenerStarted = true
            startSignalingListener()
        }

        return START_STICKY
    }

    private fun startSignalingListener() {
        val prefs = getSharedPreferences("chitrini_prefs", MODE_PRIVATE)
        val myUsername = prefs.getString("username", "") ?: ""

        mainRepository.subscribeForLatestEvent(object : NewEventCallBack {
            override fun onNewEventReceived(model: DataModel) {
                val discreteMode = prefs.getBoolean("discrete_mode", false)

                when (model.type) {
                    DataModelType.StartCall -> {
                        ringingCallSender = model.sender
                        AppState.isRinging = true
                        if (discreteMode) playRingtone()
                        else showIncomingCallNotification(model.sender, true)
                    }
                    DataModelType.StartAudioCall -> {
                        ringingCallSender = model.sender
                        AppState.isRinging = true
                        if (discreteMode) playRingtone()
                        else showIncomingCallNotification(model.sender, false)
                    }
                    DataModelType.EndCall -> {
                        if (AppState.isRinging && !AppState.isCallActive) {
                            val sender = ringingCallSender ?: model.sender
                            serviceScope.launch(Dispatchers.IO) {
                                val chatId = if (myUsername < sender) "${myUsername}_$sender" else "${sender}_$myUsername"
                                chatRepository.sendMessage(
                                    MessageEntity(
                                        messageId = java.util.UUID.randomUUID().toString(),
                                        chatId = chatId,
                                        sender = sender,
                                        text = "Missed call",
                                        timestamp = System.currentTimeMillis(),
                                        status = "received",
                                        messageType = "MISSED_CALL"
                                    )
                                )
                            }
                        }
                        ringingCallSender = null
                        AppState.isRinging = false
                        stopRingtone()
                        NotificationManagerCompat.from(this@ChitriniService).cancel(2)
                    }
                    DataModelType.ChatMessage -> {
                        // 🔥 Service is now the sole processor for incoming messages
                        MessageProcessor.processIncomingMessage(
                            context = this@ChitriniService,
                            scope = serviceScope,
                            repository = chatRepository,
                            model = model,
                            myUsername = myUsername,
                            onNotificationNeeded = { sender, text ->
                                if (!AppState.isForeground) {
                                    if (discreteMode) playNotificationSound()
                                    else showIncomingMessageNotification(sender, text)
                                } else {
                                    // App is in foreground, but user might be on Home screen or another chat
                                    if (!(AppState.isChatScreenActive && AppState.currentChatUser == sender)) {
                                        playNotificationSound()
                                    }
                                }
                            }
                        )
                    }
                    DataModelType.MessageDelivered -> {
                        serviceScope.launch(Dispatchers.IO) { chatRepository.updateMessageStatus(model.data, "delivered") }
                    }
                    DataModelType.MessageSeen -> {
                        serviceScope.launch(Dispatchers.IO) { chatRepository.updateMessageStatus(model.data, "seen") }
                    }
                    else -> {}
                }
            }
        })
    }

    private fun playRingtone() {
        stopRingtone()
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val r = RingtoneManager.getRingtone(applicationContext, uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                r.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            currentRingtone = r
            r.play()
        } catch (e: Exception) {
            Log.e("ChitriniService", "Failed to play ringtone", e)
        }
    }

    private fun stopRingtone() {
        try {
            currentRingtone?.let {
                if (it.isPlaying) it.stop()
            }
            currentRingtone = null
        } catch (e: Exception) {}
    }

    private fun playNotificationSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(applicationContext, uri)
            r.play()
        } catch (e: Exception) {}
    }

    private fun showIncomingMessageNotification(sender: String, text: String) {
        val builder = NotificationCompat.Builder(this, MSG_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Message from @$sender")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }

    private fun showIncomingCallNotification(sender: String, isVideo: Boolean) {
        val fullScreenIntent = Intent(this, CallActivity::class.java).apply {
            putExtra("target", sender)
            putExtra("isCaller", false)
            putExtra("isVideo", isVideo)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(this, 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Incoming ${if (isVideo) "Video" else "Audio"} Call")
            .setContentText("@$sender is calling...")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(2, builder.build())
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Background Service", NotificationManager.IMPORTANCE_MIN)
            val msgChannel = NotificationChannel(MSG_CHANNEL_ID, "Messages", NotificationManager.IMPORTANCE_HIGH)
            val callChannel = NotificationChannel(CALL_CHANNEL_ID, "Calls", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager?.createNotificationChannel(serviceChannel)
            manager?.createNotificationChannel(msgChannel)
            manager?.createNotificationChannel(callChannel)
        }
    }

    override fun onDestroy() {
        stopRingtone()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
