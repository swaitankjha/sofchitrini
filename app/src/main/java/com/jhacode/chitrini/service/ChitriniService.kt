package com.jhacode.chitrini.service

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.jhacode.chitrini.R
import com.jhacode.chitrini.data.local.db.ChatDatabase
import com.jhacode.chitrini.data.local.entity.MessageEntity
import com.jhacode.chitrini.data.repository.ChatRepositoryImpl
import com.jhacode.chitrini.repository.MainRepository
import com.jhacode.chitrini.ui.CallActivity
import com.jhacode.chitrini.ui.MainActivity
import com.jhacode.chitrini.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ChitriniService : Service() {

    private val CHANNEL_ID = "CHITRINI_SERVICE"
    private val MSG_CHANNEL_ID = "CHITRINI_MSG"
    private val CALL_CHANNEL_ID = "CHITRINI_CALL"
    private val GROUP_KEY_MESSAGES = "com.jhacode.chitrini.MESSAGES"

    private lateinit var mainRepository: MainRepository
    private lateinit var chatRepository: ChatRepositoryImpl
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var currentRingtone: Ringtone? = null
    private var listenerStarted = false
    private var ringingCallSender: String? = null

    private val activeMessagingStyles = mutableMapOf<String, NotificationCompat.MessagingStyle>()

    override fun onCreate() {
        super.onCreate()
        mainRepository = MainRepository.getInstance()
        val db = ChatDatabase.getInstance(this)
        chatRepository = ChatRepositoryImpl(db.messageDao(), db.chatDao())
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_STOP_RINGTONE" -> {
                stopRingtone()
                NotificationManagerCompat.from(this).cancel(2)
                return START_NOT_STICKY
            }
            "ACTION_CLEAR_NOTIFICATIONS" -> {
                val user = intent.getStringExtra("username")
                if (user != null) {
                    activeMessagingStyles.remove(user)
                    NotificationManagerCompat.from(this).cancel(user.hashCode())
                }
                return START_NOT_STICKY
            }
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
                        val nm = NotificationManagerCompat.from(this@ChitriniService)
                        nm.cancel(2)
                    }
                    DataModelType.ChatMessage -> {
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
                                    if (!(AppState.isChatScreenActive && AppState.currentChatUser == sender)) {
                                        if (discreteMode) playNotificationSound()
                                        else showIncomingMessageNotification(sender, text)
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
                    DataModelType.DeleteMessage -> {
                        serviceScope.launch(Dispatchers.IO) { chatRepository.markAsDeleted(model.data, this@ChitriniService) }
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
        val notificationManager = NotificationManagerCompat.from(this)
        val user = Person.Builder().setName("Me").build()
        val other = Person.Builder().setName("@$sender").build()
        
        val style = activeMessagingStyles.getOrPut(sender) {
            NotificationCompat.MessagingStyle(user)
                .setConversationTitle("Chat with @$sender")
        }
        
        style.addMessage(text, System.currentTimeMillis(), other)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, sender.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)

        val markReadIntent = Intent(this, NotificationReceiver::class.java).apply {
            action = "ACTION_MARK_AS_READ"
            putExtra("sender", sender)
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(this, sender.hashCode() + 1, markReadIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, MSG_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .setStyle(style)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Mark as Read", markReadPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(GROUP_KEY_MESSAGES)
            .setAutoCancel(true)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(sender.hashCode(), builder.build())
            
            val summaryNotification = NotificationCompat.Builder(this, MSG_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setGroup(GROUP_KEY_MESSAGES)
                .setGroupSummary(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            
            notificationManager.notify(0, summaryNotification)
        }
    }

    private fun showIncomingCallNotification(sender: String, isVideo: Boolean) {
        val fullScreenIntent = Intent(this, CallActivity::class.java).apply {
            putExtra("target", sender)
            putExtra("isCaller", false)
            putExtra("isVideo", isVideo)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(this, 1, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Incoming ${if (isVideo) "Video" else "Audio"} Call")
            .setContentText("@$sender is calling...")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(2, builder.build())
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Sync Service", NotificationManager.IMPORTANCE_MIN)
            val msgChannel = NotificationChannel(MSG_CHANNEL_ID, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                setShowBadge(true)
            }
            val callChannel = NotificationChannel(CALL_CHANNEL_ID, "Calls", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableLights(true)
                lightColor = android.graphics.Color.GREEN
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
