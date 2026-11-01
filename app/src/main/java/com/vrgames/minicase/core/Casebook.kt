package com.vrgames.minicase.core

/**
 * All cases and the work on each: which case is on the table, and the saved investigation of
 * every case begun. A case opens once the one before it is closed.
 */
class Casebook(saved: Map<String, String> = emptyMap()) {
    private val work = LinkedHashMap<String, Investigation>()

    init {
        for (c in Cases.all) saved[c.id]?.let { work[c.id] = Investigation.restore(c, it) }
    }

    var current: Investigation = investigation(firstOpen().id); private set

    fun investigation(id: String): Investigation = work.getOrPut(id) { Investigation(Cases.byId(id)) }

    fun solved(id: String) = work[id]?.solved == true

    fun open(c: Case) = c.number == 1 || solved(Cases.all[c.number - 2].id)

    /** The first case not yet closed (the last one once all are). */
    private fun firstOpen(): Case = Cases.all.firstOrNull { !solved(it.id) } ?: Cases.all.last()

    fun load(id: String): Boolean {
        val c = Cases.byId(id)
        if (!open(c)) return false
        current = investigation(id)
        return true
    }

    /** Starts the case over (its scene is put back as it was). */
    fun restart(id: String) {
        work[id] = Investigation(Cases.byId(id))
        if (current.case.id == id) current = work.getValue(id)
    }

    fun next(): Case? = Cases.all.getOrNull(current.case.number)?.takeIf { open(it) }

    fun serialize(): Map<String, String> = work.mapValues { it.value.serialize() }
}
