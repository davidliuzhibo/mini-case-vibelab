package com.vrgames.minicase.render

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.ObjectAudioComponent
import com.pico.spatial.core.ecs.audio.AudioPlayerController
import com.pico.spatial.core.ecs.resource.AudioResource
import com.vrgames.minicase.core.Vec3

/** CC0 clips from Kenney's RPG Audio, Interface Sounds and Impact Sounds; see docs/素材来源.md. */
enum class Sfx(val file: String, val volume: Float) {
    /** Something picked up to look at. */
    PICK("pick", 0.7f),
    /** Put back where it was. */
    PUT("put", 0.6f),
    /** A drawer, door or lid comes open. */
    OPEN("open", 0.6f),
    /** A roof lifted off. */
    LIFT("lift", 0.5f),
    /** A new clue. */
    CLUE("clue", 0.6f),
    /** A question asked, a page of the board. */
    PAGE("page", 0.5f),
    /** Two clues linked into a deduction. */
    LINK("link", 0.6f),
    WRONG("wrong", 0.5f),
    /** Case closed. */
    SOLVED("solved", 0.7f),
}

/**
 * Spatial sound from the miniature: each effect has one long-lived emitter with a prepared player,
 * moved to where the sound happens and restarted (creating players per event drops frames).
 * Main thread only.
 */
class Sound(private val parent: Entity) {
    private val emitters = HashMap<Sfx, Entity>()
    private val controllers = HashMap<Sfx, AudioPlayerController>()
    var enabled = true

    /** Loads every clip; a clip that fails is skipped (logged). */
    fun load() {
        for (s in Sfx.entries) {
            runCatching {
                val res = AudioResource.load(s.file, "asset://audio/${s.file}.wav", LoadType.FROM_ASSETS)
                val e = Entity()
                e.components.set(ObjectAudioComponent(s.volume))
                parent.addChild(e)
                val c = e.prepareAudio(res)
                if (c.valid) {
                    emitters[s] = e
                    controllers[s] = c
                }
            }.onFailure { android.util.Log.w(TAG, "sfx ${s.file}: ${it.message}") }
        }
    }

    /** Plays [s] at [at] (in [parent]'s frame; the middle of the table by default). */
    fun play(s: Sfx, at: Vec3 = Vec3(0.0, 0.1, 0.0)) {
        if (!enabled) return
        val c = controllers[s] ?: return
        emitters[s]?.setPos(at)
        runCatching {
            if (c.isPlaying()) c.stop()
            c.seekTo(0)
            c.play()
        }
    }

    companion object {
        private const val TAG = "CaseSound"
    }
}
