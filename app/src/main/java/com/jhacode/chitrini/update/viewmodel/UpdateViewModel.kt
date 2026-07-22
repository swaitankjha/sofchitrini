package com.jhacode.chitrini.update.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jhacode.chitrini.update.manager.UpdateManager
import com.jhacode.chitrini.update.model.UpdateState
import com.jhacode.chitrini.update.repository.UpdateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UpdateViewModel(
    private val repository: UpdateRepository,
    private val updateManager: UpdateManager,
    private val currentVersionCode: Int
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state = _state.asStateFlow()

    fun checkForUpdates() {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return

        _state.value = UpdateState.Checking
        viewModelScope.launch {
            repository.fetchVersionInfo()
                .onSuccess { info ->
                    if (info.versionCode > currentVersionCode) {
                        _state.value = UpdateState.UpdateAvailable(info)
                    } else {
                        _state.value = UpdateState.UpToDate
                    }
                }
                .onFailure { error ->
                    _state.value = UpdateState.Error(error.message ?: "Unknown error")
                }
        }
    }

    fun startDownload(url: String) {
        _state.value = UpdateState.Downloading(0)
        updateManager.downloadAndInstall(
            url = url,
            onProgress = { progress ->
                _state.value = UpdateState.Downloading(progress)
            },
            onSignatureMismatch = {
                _state.value = UpdateState.Error("Security verification failed: Signature mismatch. APK deleted.")
            },
            onError = { message ->
                _state.value = UpdateState.Error(message)
            }
        )
    }

    fun resetState() {
        _state.value = UpdateState.Idle
    }
}
