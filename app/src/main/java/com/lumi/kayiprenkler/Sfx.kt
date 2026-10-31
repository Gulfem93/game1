package com.lumi.kayiprenkler

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Ses dosyası kullanmadan kodla üretilen kısa ses efektleri. */
class Sfx(var enabled: Boolean) {

    private val rate = 22050
    private val tracks = HashMap<String, AudioTrack>()

    init {
        try {
            add("tap", synth(listOf(880f to 0.04f), 0.18f))
            add("step", synth(listOf(520f to 0.03f), 0.07f))
            add("shard", synth(listOf(988f to 0.07f, 1319f to 0.14f), 0.3f))
            add("full", synth(listOf(784f to 0.08f, 988f to 0.08f, 1175f to 0.08f, 1568f to 0.22f), 0.3f))
            add("crystal", synth(listOf(660f to 0.09f, 1320f to 0.3f), 0.28f))
            add("fail", synth(listOf(330f to 0.12f, 247f to 0.12f, 165f to 0.3f), 0.3f))
            add("splash", synth(listOf(320f to 0.04f, 210f to 0.14f), 0.2f))
            add("wind", synth(listOf(600f to 0.03f, 760f to 0.05f), 0.1f))
            add("hunter",synth(listOf(150f to 0.16f, 110f to 0.28f), 0.32f))
            add("win", synth(listOf(523f to 0.1f, 659f to 0.1f, 784f to 0.1f, 1047f to 0.4f), 0.3f))
        } catch (_: Throwable) {
            // Ses aygıtı yoksa oyun sessiz devam eder.
        }
    }

    private fun synth(notes: List<Pair<Float, Float>>, volume: Float): ShortArray {
        val out = ShortArray(notes.sumOf { (it.second * rate).toInt() })
        var i = 0
        for ((freq, dur) in notes) {
            val n = (dur * rate).toInt()
            for (k in 0 until n) {
                val t = k.toFloat() / rate
                val attack = min(1f, k / (0.004f * rate))
                val decay = (1f - k.toFloat() / n).pow(1.6f)
                val s = sin(2 * PI * freq * t) + 0.25 * sin(4 * PI * freq * t)
                out[i++] = (s * attack * decay * volume * 26000).toInt().toShort()
            }
        }
        return out
    }

    private fun add(name: String, data: ShortArray) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(data.size * 2)
            .build()
        track.write(data, 0, data.size)
        tracks[name] = track
    }

    fun play(name: String) {
        if (!enabled) return
        val track = tracks[name] ?: return
        try {
            track.stop()
            track.reloadStaticData()
            track.play()
        } catch (_: Throwable) {
        }
    }

    fun release() {
        tracks.values.forEach { it.release() }
        tracks.clear()
    }
}
