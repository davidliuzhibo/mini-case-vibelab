package com.vrgames.minicase.game

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.vrgames.minicase.core.Cases

/**
 * Test hook, debug builds only: plays the cases by following the hints, so every case can be
 * checked on the emulator without hands. Driven over adb; results go to logcat (tag CaseTest).
 *
 *   adb shell am broadcast -a com.vrgames.minicase.TEST -p com.vrgames.minicase --es cmd all
 *
 * cmd: all (every case in order) · case --es id wutong|inn|train · solve (the case on the table) ·
 * step (one hinted step) · hint · reset (wipe the save) · stop · view --es id "x,y,z,yaw,scale[,pitch]"|reset · open --es id board|file
 * (table pose for the emulator camera) · music
 */
object AutoPilot {
    const val ACTION = "com.vrgames.minicase.TEST"
    private const val TAG = "CaseTest"
    private const val STEP_MS = 1500L
    private const val NEXT_MS = 6000L

    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<String>()
    private var running = false
    private var solving = false

    /** Set by the stage: carries out one hinted step in the scene; false once the case is closed. */
    var stepper: (() -> Boolean)? = null

    fun register(context: Context) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = command(i.getStringExtra("cmd") ?: "", i.getStringExtra("id"))
        }
        ContextCompat.registerReceiver(context.applicationContext, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED)
        Log.i(TAG, "ready")
    }

    private fun command(cmd: String, id: String?) {
        Log.i(TAG, "cmd $cmd ${id.orEmpty()}")
        when (cmd) {
            "all" -> {
                stop()
                queue.addAll(Cases.all.map { it.id })
                running = true
                next()
            }
            "case" -> if (Cases.all.any { it.id == id }) Game.select(id!!) else Log.w(TAG, "unknown case $id")
            "solve" -> solve()
            "step" -> Log.i(TAG, "step ${stepper?.invoke()}")
            "hint" -> {
                Game.askHint()
                Log.i(TAG, "hint ${Game.hint}")
            }
            "reset" -> {
                stop()
                Game.resetSave()
            }
            "stop" -> stop()
            "view" -> Game.debugView = id?.takeIf { it != "reset" }
            "open" -> Game.debugOpen = id
            "music" -> Game.toggleMusic().also { Log.i(TAG, "music on=${Game.musicOn}") }
            else -> Log.w(TAG, "unknown cmd $cmd")
        }
    }

    private fun stop() {
        running = false
        solving = false
        queue.clear()
        main.removeCallbacksAndMessages(null)
    }

    private fun next() {
        if (!running) return
        val id = queue.removeFirstOrNull()
        if (id == null) {
            running = false
            Log.i(TAG, "done solved=${Cases.all.count { Game.book.solved(it.id) }}/${Cases.all.size}")
            return
        }
        Game.select(id)
        main.postDelayed({ if (running) solve() }, NEXT_MS)
    }

    private fun solve() {
        solving = true
        val case = Game.caseId
        var steps = 0
        fun tick() {
            if (!solving) return
            if (Game.inv.solved) {
                solving = false
                Log.i(TAG, "result $case SOLVED steps=$steps clues=${Game.inv.found.size}/${Game.inv.case.clues.size}")
                if (running) main.postDelayed(::next, NEXT_MS)
                return
            }
            val moved = stepper?.invoke() ?: false
            steps++
            if (!moved || steps > 80) {
                solving = false
                Log.w(TAG, "result $case STUCK steps=$steps")
                if (running) main.postDelayed(::next, NEXT_MS)
                return
            }
            main.postDelayed(::tick, STEP_MS)
        }
        tick()
    }
}
