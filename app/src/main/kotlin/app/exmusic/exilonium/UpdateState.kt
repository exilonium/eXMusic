package app.exmusic.exilonium

import android.net.Uri
import app.exmusic.providers.github.models.Release
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface UpdateState {
    data object Idle : UpdateState
    data class Available(val release: Release, val apkUrl: String) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class ReadyToInstall(val uri: Uri) : UpdateState
}

private val mutableUpdateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
val updateState = mutableUpdateState.asStateFlow()

fun setUpdateState(state: UpdateState) {
    mutableUpdateState.value = state
}
