package com.fluxio.core.notification

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object Subscription_Sound_Player {
    fun playSuccessSong() {
        GlobalScope.launch(Dispatchers.Default) {
            try {
                val sampleRate = 44100
                
                // We define a list of notes. Each element has:
                // - A list of frequencies (to support chords!)
                // - Duration in milliseconds
                val song = listOf(
                    listOf(523.25f) to 120,      // C5
                    listOf(587.33f) to 120,      // D5
                    listOf(659.25f) to 120,      // E5
                    listOf(783.99f) to 120,      // G5
                    listOf(880.00f) to 120,      // A5
                    listOf(1046.50f) to 250,     // C6
                    listOf(1046.50f, 1318.51f, 1567.98f) to 600 // C6 + E6 + G6 Chord!
                )
                
                var totalSamples = 0
                for (step in song) {
                    totalSamples += (sampleRate * step.second / 1000)
                }
                
                val audioBuffer = ShortArray(totalSamples)
                var currentSample = 0
                
                for (step in song) {
                    val frequencies = step.first
                    val durationMs = step.second
                    val samplesForStep = (sampleRate * durationMs / 1000)
                    
                    for (i in 0 until samplesForStep) {
                        val t = i.toDouble() / sampleRate
                        
                        // Mix all frequencies in the chord
                        var mixedSignal = 0.0
                        for (freq in frequencies) {
                            mixedSignal += Math.sin(2.0 * Math.PI * freq * t)
                        }
                        // Normalize by the number of frequencies to prevent clipping
                        mixedSignal /= frequencies.size
                        
                        // Apply ADSR-like simple volume envelope
                        // Fade in over first 15ms to avoid sudden click
                        val fadeInSamples = (sampleRate * 15 / 1000).coerceAtMost(samplesForStep / 4)
                        // Fade out over last 30ms to avoid click
                        val fadeOutSamples = (sampleRate * 30 / 1000).coerceAtMost(samplesForStep / 2)
                        
                        val envelope = when {
                            i < fadeInSamples -> i.toDouble() / fadeInSamples
                            i > (samplesForStep - fadeOutSamples) -> {
                                val remaining = samplesForStep - i
                                remaining.toDouble() / fadeOutSamples
                            }
                            else -> 1.0
                        }
                        
                        val value = (mixedSignal * envelope * Short.MAX_VALUE * 0.4).toInt()
                        audioBuffer[currentSample++] = value.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                }
                
                val audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    totalSamples * 2,
                    AudioTrack.MODE_STATIC
                )
                
                audioTrack.write(audioBuffer, 0, totalSamples)
                audioTrack.play()
                
                // Let the track finish playing
                val totalDurationMs = song.sumOf { it.second }
                Thread.sleep(totalDurationMs.toLong() + 100)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
