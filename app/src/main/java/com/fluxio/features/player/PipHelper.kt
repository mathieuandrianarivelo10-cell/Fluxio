package com.fluxio.features.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object PipHelper {
    var isVideoPlaying by mutableStateOf(false)
    var isInPipMode by mutableStateOf(false)
}
