package com.vrgames.minicase.render

import android.util.Log
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.ObjectAudioComponent
import com.pico.spatial.core.ecs.audio.AudioPlayerController
import com.pico.spatial.core.ecs.resource.AudioResource
import com.vrgames.minicase.core.Vec3

/**
 * A character heard from their own figurine on the table: one emitter, moved to whoever
 * is speaking, plays assets/narration/<id>.ogg as spatial sound. Main thread only.
 */
class Voice(private val parent: Entity) {
    private val emitter = Entity().apply {
        components.set(ObjectAudioComponent(1f))
        parent.addChild(this)
    }
    private var controller: AudioPlayerController? = null
    var playing: String? = null
        private set
    private var startedAt = 0L

    fun play(id: String, at: Vec3): Boolean {
        stop()
        return runCatching {
            val res = AudioResource.load("n_$id", "asset://narration/$id.ogg", LoadType.FROM_ASSETS)
            emitter.setPos(at)
            val c = emitter.prepareAudio(res)
            if (!c.valid) return false
            c.play()
            controller = c
            playing = id
            startedAt = android.os.SystemClock.uptimeMillis()
            true
        }.onFailure { Log.i("CaseVoice", "no narration $id: ${it.message}") }.getOrDefault(false)
    }

    fun stop() {
        runCatching { controller?.let { if (it.isPlaying()) it.stop() } }
        controller = null
        playing = null
    }

    /** True once the clip has played out (checked from the frame loop). */
    fun finished(): Boolean {
        val c = controller ?: return false
        if (android.os.SystemClock.uptimeMillis() - startedAt < 800) return false
        return runCatching { !c.isPlaying() }.getOrDefault(true)
    }
}
