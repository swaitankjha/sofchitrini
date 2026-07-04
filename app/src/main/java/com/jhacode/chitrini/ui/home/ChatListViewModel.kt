package com.jhacode.chitrini.ui.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jhacode.chitrini.data.local.dao.ChatDao
import com.jhacode.chitrini.data.local.model.ChatPreview
import com.jhacode.chitrini.data.repository.ChatRepositoryImpl
import com.jhacode.chitrini.network.SocketManager
import com.jhacode.chitrini.repository.MainRepository
import com.jhacode.chitrini.storage.MediaDownloader
import com.jhacode.chitrini.utils.ProfilePicData
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class ChatListViewModel(
    private val chatDao: ChatDao,
    private val repository: ChatRepositoryImpl,
    private val myUsername: String
) : ViewModel() {

    val chats: Flow<List<ChatPreview>> = chatDao.observeChats()

    private val _incomingRequests = MutableStateFlow<List<String>>(emptyList())
    val incomingRequests = _incomingRequests.asStateFlow()

    private val _profilePics = MutableStateFlow<Map<String, File?>>(emptyMap())
    val profilePics = _profilePics.asStateFlow()

    private val _myProfilePic = MutableStateFlow<File?>(null)
    val myProfilePic = _myProfilePic.asStateFlow()

    init {
        listenForConnectRequests()
        listenForConnectAccepts()
    }

    fun refreshMyProfilePic(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            MainRepository.getInstance().getProfilePic(myUsername) { json ->
                if (json != null) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val data = Gson().fromJson(json, ProfilePicData::class.java)
                            val file = MediaDownloader.downloadMedia(context, data.fileId, data.encryptedKey, data.iv)
                            _myProfilePic.value = file
                        } catch (e: Exception) {}
                    }
                }
            }
        }
    }

    fun initProfilePics(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            // My profile pic
            refreshMyProfilePic(context)

            // Contacts profile pics
            chats.first().forEach { chat ->
                val otherUser = if (chat.userA == myUsername) chat.userB else chat.userA
                fetchOtherProfilePic(context, otherUser)
            }

            // Requests profile pics
            _incomingRequests.value.forEach { user ->
                fetchOtherProfilePic(context, user)
            }
        }
    }

    private fun fetchOtherProfilePic(context: Context, otherUser: String) {
        MainRepository.getInstance().getProfilePic(otherUser) { json ->
            if (json != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val data = Gson().fromJson(json, ProfilePicData::class.java)
                        val file = MediaDownloader.downloadMedia(context, data.fileId, data.encryptedKey, data.iv)
                        val currentMap = _profilePics.value.toMutableMap()
                        currentMap[otherUser] = file
                        _profilePics.value = currentMap
                    } catch (e: Exception) {}
                }
            }
        }
    }

    private fun listenForConnectRequests() {
        SocketManager.onConnectRequest { fromUser ->
            viewModelScope.launch {
                if (fromUser == myUsername) return@launch
                if (_incomingRequests.value.contains(fromUser)) return@launch
                val currentChats = chatDao.getChatListOnce()
                if (currentChats.any { it.userA == fromUser || it.userB == fromUser }) return@launch
                _incomingRequests.value = _incomingRequests.value + fromUser
            }
        }
    }

    private fun listenForConnectAccepts() {
        SocketManager.onConnectAccepted { fromUser ->
            viewModelScope.launch {
                if (fromUser == myUsername) return@launch
                addChat(fromUser)
            }
        }
    }

    fun sendConnectRequest(username: String) {
        if (username.isBlank() || username == myUsername) return
        SocketManager.sendConnectRequest(username)
    }

    fun acceptRequest(fromUser: String) {
        viewModelScope.launch {
            SocketManager.acceptConnectRequest(fromUser)
            val chatId = if (myUsername < fromUser) "${myUsername}_$fromUser" else "${fromUser}_$myUsername"
            chatDao.insertChat(ChatPreview(chatId, myUsername, fromUser, "", System.currentTimeMillis()))
            _incomingRequests.value = _incomingRequests.value - fromUser
        }
    }

    private suspend fun addChat(otherUser: String) {
        val chatId = if (myUsername < otherUser) "${myUsername}_$otherUser" else "${otherUser}_$myUsername"
        chatDao.insertChat(ChatPreview(chatId, myUsername, otherUser, "", System.currentTimeMillis()))
    }

    fun rejectRequest(fromUser: String) {
        viewModelScope.launch { _incomingRequests.value = _incomingRequests.value - fromUser }
    }

    fun clearChatMessages(otherUser: String) {
        val chatId = if (myUsername < otherUser) "${myUsername}_$otherUser" else "${otherUser}_$myUsername"
        viewModelScope.launch {
            repository.clearMessages(chatId)
            chatDao.insertChat(ChatPreview(chatId, if (myUsername < otherUser) myUsername else otherUser, if (myUsername < otherUser) otherUser else myUsername, "", System.currentTimeMillis()))
        }
    }

    fun removeFriend(otherUser: String) {
        val chatId = if (myUsername < otherUser) "${myUsername}_$otherUser" else "${otherUser}_$myUsername"
        viewModelScope.launch {
            repository.clearMessages(chatId)
            chatDao.deleteChat(chatId)
        }
    }
}
