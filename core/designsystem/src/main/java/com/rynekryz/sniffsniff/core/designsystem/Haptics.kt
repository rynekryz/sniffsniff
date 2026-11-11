package com.rynekryz.sniffsniff.core.designsystem

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

val LocalHapticsEnabled = compositionLocalOf { true }

class Haptics(private val vibrator: Vibrator?, private val enabled: () -> Boolean) {
    fun tick() = pulse(20, 90)
    fun click() = pulse(25, 150)

    fun confirm() {
        if (!enabled()) return
        play(VibrationEffect.createWaveform(longArrayOf(0, 12, 40, 18), intArrayOf(0, 140, 0, 200), -1))
    }

    private fun pulse(duration: Long, amplitude: Int) {
        if (!enabled()) return
        play(VibrationEffect.createOneShot(duration, amplitude))
    }

    private fun play(effect: VibrationEffect) {
        val target = vibrator ?: return
        if (target.hasVibrator()) runCatching { target.vibrate(effect) }
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    val enabled = rememberUpdatedState(LocalHapticsEnabled.current)
    return remember {
        val vibrator = runCatching {
            (context.applicationContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        }.getOrNull()
        Haptics(vibrator) { enabled.value }
    }
}
