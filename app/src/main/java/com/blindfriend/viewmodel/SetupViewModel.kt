package com.blindfriend.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blindfriend.BlindFriendApp
import com.blindfriend.download.DownloadState
import com.blindfriend.download.ModelDownloader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SetupViewModel(app: Application) : AndroidViewModel(app) {

    private val downloader = ModelDownloader(app)

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state

    fun startDownload(token: String) {
        viewModelScope.launch {
            downloader.download(token).collect { s ->
                _state.value = s
                if (s is DownloadState.Done) {
                    (getApplication() as BlindFriendApp).startInit()
                }
            }
        }
    }
}
