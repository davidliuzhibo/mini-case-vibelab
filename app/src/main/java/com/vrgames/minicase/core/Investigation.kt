package com.vrgames.minicase.core

/**
 * The player's work on one case: what has been opened, found, asked and linked, and the
 * accusation. Every action returns what it changed so the scene and the board can show it.
 */
class Investigation(val case: Case) {
    val opened = LinkedHashSet<String>()
    val found = LinkedHashSet<String>()
    val asked = LinkedHashSet<String>()
    var solved = false; private set
    var hintsUsed = 0; private set
    var wrongAccusations = 0; private set

    /** Whether the prop can be handled now (what it sits in or under is open). */
    fun reachable(propId: String) = case.prop(propId).needs.all { it in opened }

    /**
     * The player opened, lifted or examined [propId] (for a prop with a hidden side: turned it
     * so that side showed, [sawHidden]). Returns the clues it newly gives.
     */
    fun handle(propId: String, sawHidden: Boolean = false): List<Clue> {
        val p = case.prop(propId)
        if (!reachable(propId)) return emptyList()
        if (p.action != Action.EXAMINE) opened += propId
        if (p.hiddenSide != null && !sawHidden) return emptyList()
        if (p.action == Action.EXAMINE) opened += propId
        return add(p.gives)
    }

    fun canAsk(q: Question) = q.needs.all { it in found }

    /** Asks [questionId]; returns the testimony it newly gives (the answer is in the question). */
    fun ask(questionId: String): List<Clue> {
        val q = case.people.flatMap { it.questions }.first { it.id == questionId }
        if (!canAsk(q)) return emptyList()
        asked += q.id
        return add(q.gives)
    }

    sealed interface Link {
        data class New(val clue: Clue) : Link
        data class Known(val clue: Clue) : Link
        data object Nothing : Link
    }

    /** Links two found clues on the board. */
    fun link(a: String, b: String): Link {
        if (a !in found || b !in found || a == b) return Link.Nothing
        val d = case.deductions.firstOrNull { (it.a == a && it.b == b) || (it.a == b && it.b == a) } ?: return Link.Nothing
        val c = case.clue(d.gives)
        return if (d.gives in found) Link.Known(c) else Link.New(add(listOf(d.gives)).first())
    }

    sealed interface Verdict {
        data object Right : Verdict
        /** Wrong person: why it cannot be them. */
        data class WrongPerson(val why: String) : Verdict
        data object WeakMotive : Verdict
        data object WeakProof : Verdict
    }

    fun accuse(person: String, motive: String, proof: String): Verdict {
        val s = case.solution
        val v = when {
            person != s.culprit -> Verdict.WrongPerson(s.wrong[person] ?: "证据指向的不是这个人。")
            motive !in s.motive || motive !in found -> Verdict.WeakMotive
            proof !in s.proof || proof !in found -> Verdict.WeakProof
            else -> Verdict.Right
        }
        if (v == Verdict.Right) solved = true else wrongAccusations++
        return v
    }

    sealed interface Hint {
        data class Look(val prop: Prop) : Hint
        data class Ask(val person: Person, val question: Question) : Hint
        data class Link(val a: Clue, val b: Clue) : Hint
        data object Accuse : Hint
        data object Done : Hint
    }

    /**
     * The next useful step toward the answer: a prop that leads on, a question that opens up, two
     * clues to link, or "you can name the culprit now". Counts as a hint used.
     */
    fun hint(): Hint {
        hintsUsed++
        return nextStep()
    }

    /** [hint] without counting it (for tests and the auto-player). */
    fun nextStep(): Hint {
        if (solved) return Hint.Done
        val s = case.solution
        if (s.motive.any { it in found } && s.proof.any { it in found }) return Hint.Accuse
        // Deductions first: they use what the player already holds.
        case.deductions.firstOrNull { it.gives !in found && it.a in found && it.b in found && leadsOn(it.gives) }
            ?.let { return Hint.Link(case.clue(it.a), case.clue(it.b)) }
        for (person in case.people) for (q in person.questions) {
            if (q.id !in asked && canAsk(q) && q.gives.any { it !in found && leadsOn(it) }) return Hint.Ask(person, q)
        }
        for (p in case.props) {
            val done = p.id in opened && (p.gives.isEmpty() || p.gives.all { it in found })
            if (!done && reachable(p.id) && (p.gives.any { it !in found && leadsOn(it) } || unlocksUseful(p))) return Hint.Look(p)
        }
        // Nothing on the answer's path is left: point at anything not yet seen.
        case.props.firstOrNull { it.id !in opened && reachable(it.id) }?.let { return Hint.Look(it) }
        return Hint.Accuse
    }

    /** Whether clue [id] is on the way to the answer (in it, or linked into something that is). */
    private fun leadsOn(id: String, depth: Int = 0): Boolean {
        val s = case.solution
        if (id in s.motive || id in s.proof) return true
        if (depth > 6) return false
        if (case.deductions.any { (it.a == id || it.b == id) && leadsOn(it.gives, depth + 1) }) return true
        return case.people.any { p -> p.questions.any { q -> id in q.needs && q.gives.any { leadsOn(it, depth + 1) } } }
    }

    private fun unlocksUseful(p: Prop): Boolean =
        case.props.any { it.needs.contains(p.id) && it.gives.any { g -> g !in found && leadsOn(g) } }

    private fun add(ids: List<String>): List<Clue> = ids.filter { found.add(it) }.map { case.clue(it) }

    // ---- Save ------------------------------------------------------------------------------------

    fun serialize(): String = listOf(
        opened.joinToString(","), found.joinToString(","), asked.joinToString(","),
        if (solved) "1" else "0", hintsUsed.toString(), wrongAccusations.toString(),
    ).joinToString(";")

    companion object {
        fun restore(case: Case, s: String?): Investigation {
            val inv = Investigation(case)
            val parts = s?.split(";") ?: return inv
            fun ids(i: Int) = parts.getOrNull(i)?.split(",")?.filter { it.isNotEmpty() }.orEmpty()
            val propIds = case.props.map { it.id }.toSet()
            val clueIds = case.clues.map { it.id }.toSet()
            val qIds = case.people.flatMap { p -> p.questions.map { it.id } }.toSet()
            inv.opened += ids(0).filter { it in propIds }
            inv.found += ids(1).filter { it in clueIds }
            inv.asked += ids(2).filter { it in qIds }
            inv.solved = parts.getOrNull(3) == "1"
            inv.hintsUsed = parts.getOrNull(4)?.toIntOrNull() ?: 0
            inv.wrongAccusations = parts.getOrNull(5)?.toIntOrNull() ?: 0
            return inv
        }
    }
}
