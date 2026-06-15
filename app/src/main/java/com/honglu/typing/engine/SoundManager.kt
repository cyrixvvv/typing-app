package com.honglu.typing.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.honglu.typing.R

/**
 * Sound manager using SoundPool for low-latency audio playback.
 * Supports key click, correct, and wrong feedback sounds.
 */
class SoundManager(context: Context) {

    private val soundPool: SoundPool
    private val soundClickId: Int
    private val soundCorrectId: Int
    private val soundWrongId: Int
    private var loaded = false

    init {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            soundPool = SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(attrs)
                .build()
        } else {
            @Suppress("DEPRECATION")
            soundPool = SoundPool(3, android.media.AudioManager.STREAM_MUSIC, 0)
        }

        soundClickId = soundPool.load(context, R.raw.key_click, 1)
        soundCorrectId = soundPool.load(context, R.raw.key_correct, 2)
        soundWrongId = soundPool.load(context, R.raw.key_wrong, 3)

        soundPool.setOnLoadCompleteListener { _, soundId, status ->
            if (status == 0) {
                loaded = true
            }
        }
    }

    private var enabled = true

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun playClick() {
        if (!enabled || !loaded) return
        soundPool.play(soundClickId, 1f, 1f, 0, 0, 1f)
    }

    fun playCorrect() {
        if (!enabled || !loaded) return
        soundPool.play(soundCorrectId, 1f, 1f, 0, 0, 1.2f)
    }

    fun playWrong() {
        if (!enabled || !loaded) return
        soundPool.play(soundWrongId, 1f, 1f, 0, 0, 0.8f)
    }

    fun cleanup() {
        soundPool.release()
    }
}
