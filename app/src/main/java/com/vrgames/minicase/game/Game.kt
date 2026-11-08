package com.vrgames.minicase.game

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vrgames.minicase.BOARD_ID
import com.vrgames.minicase.core.Cases
import com.vrgames.minicase.core.Casebook
import com.vrgames.minicase.core.Clue
import com.vrgames.minicase.core.Investigation

/**
 * State shared by the stage and both windows (one process). [book] holds the rules; the Compose
 * fields mirror what the panels and windows show and change only on events, never per frame.
 */
object Game {
    private const val PREFS = "mini_case"
    private const val TAG = "CaseGame"

    /** Guards [book]: gestures, the frame callback and window buttons all touch it. */
    val lock = Any()
    var book = Casebook(); private set
    private var prefs: android.content.SharedPreferences? = null

    val inv: Investigation get() = book.current
    var caseId by mutableStateOf(book.current.case.id); private set
    /** Bumps when the scene must rebuild (another case, a restart). */
    var sceneVersion by mutableIntStateOf(0); private set
    /** Bumps whenever the investigation changes (a clue, a question, a link, a verdict). */
    var version by mutableIntStateOf(0); private set

    var showTitle by mutableStateOf(true)
    /** The case card (who, where, what happened) has been put away; it comes back with each case. */
    var caseCardDismissed by mutableStateOf(false)
    var menuOpen by mutableStateOf(false)
    /**
     * The window on screen ([BOARD_ID] or [FILE_ID]), or null. One at a time, and never twice: opening a
     * window that is already open starts a second copy of it.
     */
    var window by mutableStateOf<String?>(null); private set
    private val windowCopies = HashMap<String, Any>()
    /** Closes a window (set by the stage; the windows' 关闭 buttons call it). */
    var closeWindow: (String) -> Unit = {}

    /** The window [id] opens (from the stage), or a [copy] of it comes on screen. */
    fun windowShown(id: String, copy: Any? = null) {
        if (copy != null) windowCopies[id] = copy
        window = id
    }

    /** The window [id] is gone: closed from the stage, or the system took this [copy] down. */
    fun windowGone(id: String, copy: Any? = null) {
        if (copy != null && windowCopies[id] !== copy) return
        windowCopies.remove(id)
        if (window == id) window = null
    }
    /** The person being questioned in the scene, or null. */
    var talkingTo by mutableStateOf<String?>(null)
    /** The answer just given in the scene's question panel. */
    var lastAnswer by mutableStateOf<String?>(null)
    /** Clues just found, shown for a moment over the scene ("新线索"). */
    var toast by mutableStateOf<List<Clue>>(emptyList()); private set
    var toastVersion by mutableIntStateOf(0); private set
    /** A short line over the scene after something that did not work ("先打开…"), or while loading. */
    var note by mutableStateOf<String?>(null)
    /** The current hint: what to look at, ask or link. */
    var hint by mutableStateOf<Investigation.Hint?>(null); private set
    /** The last verdict on the board, until the next try. */
    var verdict by mutableStateOf<Investigation.Verdict?>(null)
    /** Clues selected on the board for linking (at most two). */
    var picked by mutableStateOf<List<String>>(emptyList())
    var linkResult by mutableStateOf<String?>(null)
    /** The table top's height above the floor (meters); the player can raise or lower it. */
    var tableHeight by mutableStateOf(DEFAULT_TABLE_HEIGHT)
    var musicOn by mutableStateOf(true); private set
    /** The first-time "pinch a figurine / pick something up" label, until the player has done it once. */
    var coached by mutableStateOf(false); private set

    var sound: com.vrgames.minicase.render.Sound? = null
    var music: com.vrgames.minicase.render.Music? = null
    /** Debug only (AutoPilot): table pose override "x,y,z,yaw,scale" to frame it for the emulator camera. */
    var debugView by mutableStateOf<String?>(null)
    /** Debug only (AutoPilot): a window to open ("board" or "file"), cleared once opened. */
    var debugOpen by mutableStateOf<String?>(null)

    fun init(context: Context) {
        appContext = context.applicationContext
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val saved = Cases.all.mapNotNull { c -> p.getString(KEY_CASE + c.id, null)?.let { c.id to it } }.toMap()
        synchronized(lock) { book = Casebook(saved) }
        p.getFloat(KEY_HEIGHT, -1f).takeIf { it > 0 }?.let { tableHeight = it }
        musicOn = p.getBoolean(KEY_MUSIC, true)
        coached = p.getBoolean(KEY_COACHED, false)
        refresh(rebuild = true)
        AutoPilot.register(context)
    }

    fun start() {
        showTitle = false
    }

    fun select(id: String) {
        val ok = synchronized(lock) { book.load(id) }
        if (!ok) return
        showTitle = false
        caseCardDismissed = false
        resetUi()
        refresh(rebuild = true)
    }

    fun restartCase() {
        synchronized(lock) { book.restart(inv.case.id) }
        resetUi()
        save()
        refresh(rebuild = true)
    }

    fun nextCase() {
        val next = synchronized(lock) { book.next() } ?: return
        // The board would show the new case over its scene: let the case card on the table start it.
        closeWindow(BOARD_ID)
        select(next.id)
    }

    private fun resetUi() {
        talkingTo = null
        lastAnswer = null
        hint = null
        verdict = null
        picked = emptyList()
        linkResult = null
        toast = emptyList()
        note = null
    }

    /** The scene handled a prop (opened, lifted, examined; [sawHidden]: its hidden side was turned to view). */
    fun handled(propId: String, sawHidden: Boolean = false): List<Clue> {
        val got = synchronized(lock) { inv.handle(propId, sawHidden) }
        caseCardDismissed = true
        coach()
        if ((hint as? Investigation.Hint.Look)?.prop?.id == propId && got.isNotEmpty()) hint = null
        found(got)
        return got
    }

    fun talk(personId: String?) {
        stopSpeaking()
        talkingTo = personId
        lastAnswer = null
        caseCardDismissed = true
        if (personId != null) coach()
    }

    /** The question whose answer is on show (its recording: narration/<id>.ogg). */
    var lastQuestion by mutableStateOf<String?>(null); private set
    /** The recording now playing, or null. */
    var speaking by mutableStateOf<String?>(null); private set
    private var narrator: android.media.MediaPlayer? = null
    private var appContext: android.content.Context? = null

    /** Plays a character's recorded line; the music dips under it. */
    fun speak(id: String) {
        stopSpeaking()
        val c = appContext ?: return
        runCatching {
            val fd = c.assets.openFd("narration/$id.ogg")
            narrator = android.media.MediaPlayer().apply {
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                fd.close()
                setOnCompletionListener { stopSpeaking() }
                prepare()
                start()
            }
            speaking = id
            music?.ducked = true
        }.onFailure { android.util.Log.i("MiniCase", "line $id: ${it.message}") }
    }

    fun stopSpeaking() {
        runCatching { narrator?.stop() }
        runCatching { narrator?.release() }
        narrator = null
        speaking = null
        music?.ducked = false
    }

    fun ask(questionId: String) {
        val q = inv.case.people.flatMap { it.questions }.first { it.id == questionId }
        val got = synchronized(lock) { inv.ask(questionId) }
        lastAnswer = q.answer
        lastQuestion = questionId
        speak(questionId)
        if ((hint as? Investigation.Hint.Ask)?.question?.id == questionId) hint = null
        found(got)
    }

    /** Toggles a clue on the board; with two picked, links them. */
    fun pick(id: String) {
        picked = if (id in picked) picked - id else (picked + id).takeLast(2)
        linkResult = null
        if (picked.size == 2) {
            val (a, b) = picked
            val r = synchronized(lock) { inv.link(a, b) }
            linkResult = when (r) {
                is Investigation.Link.New -> "联系起来了！得出新的推论：${r.clue.title.removePrefix("推论：")}"
                is Investigation.Link.Known -> "这两条已经联系过了：${r.clue.title.removePrefix("推论：")}"
                Investigation.Link.Nothing -> "这两条之间看不出联系。换一组试试。"
            }
            if (r is Investigation.Link.New) {
                if (hint is Investigation.Hint.Link) hint = null
                found(listOf(r.clue))
                picked = emptyList()
            }
        }
    }

    fun accuse(person: String, motive: String, proof: String) {
        val v = synchronized(lock) { inv.accuse(person, motive, proof) }
        Log.i(TAG, "accuse ${inv.case.id}: $person / $motive / $proof -> $v")
        verdict = v
        hint = null
        save()
        version++
    }

    fun askHint() {
        hint = synchronized(lock) { inv.hint() }
        save()
        version++
    }

    fun clearHint() {
        hint = null
    }

    fun clearToast() {
        toast = emptyList()
    }

    private fun found(got: List<Clue>) {
        if (got.isNotEmpty()) {
            toast = got
            toastVersion++
            sound?.play(com.vrgames.minicase.render.Sfx.CLUE)
        }
        save()
        version++
    }

    private fun coach() {
        if (coached) return
        coached = true
        prefs?.edit()?.putBoolean(KEY_COACHED, true)?.apply()
    }

    fun toggleMusic() {
        musicOn = !musicOn
        music?.muted = !musicOn
        prefs?.edit()?.putBoolean(KEY_MUSIC, musicOn)?.apply()
    }

    fun raiseTable(d: Float) {
        tableHeight = (tableHeight + d).coerceIn(0.5f, 1.3f)
        prefs?.edit()?.putFloat(KEY_HEIGHT, tableHeight)?.apply()
    }

    /** The table was found by the headset's plane detection: use its height (not saved). */
    fun tableFound(height: Float) {
        tableHeight = height.coerceIn(0.5f, 1.3f)
    }

    /** Test hook: forget all progress. */
    fun resetSave() {
        val e = prefs?.edit()
        Cases.all.forEach { e?.remove(KEY_CASE + it.id) }
        e?.remove(KEY_COACHED)?.apply()
        coached = false
        synchronized(lock) { book = Casebook() }
        resetUi()
        showTitle = true
        caseCardDismissed = false
        refresh(rebuild = true)
    }

    private fun save() {
        val e = prefs?.edit() ?: return
        synchronized(lock) { book.serialize() }.forEach { (id, s) -> e.putString(KEY_CASE + id, s) }
        e.apply()
    }

    private fun refresh(rebuild: Boolean = false) {
        caseId = synchronized(lock) { book.current.case.id }
        version++
        if (rebuild) sceneVersion++
    }

    const val DEFAULT_TABLE_HEIGHT = 0.8f
    private const val KEY_CASE = "case_"
    private const val KEY_HEIGHT = "table_height"
    private const val KEY_MUSIC = "music"
    private const val KEY_COACHED = "coached"
}
