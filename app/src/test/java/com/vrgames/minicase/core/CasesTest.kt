package com.vrgames.minicase.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CasesTest {
    @Test
    fun everyReferencePointsAtSomething() {
        for (c in Cases.all) {
            val clues = c.clues.map { it.id }
            assertEquals("${c.id}: clue ids unique", clues.size, clues.toSet().size)
            val props = c.props.map { it.id }
            assertEquals("${c.id}: prop ids unique", props.size, props.toSet().size)
            val questions = c.people.flatMap { p -> p.questions.map { it.id } }
            assertEquals("${c.id}: question ids unique", questions.size, questions.toSet().size)
            for (p in c.props) {
                p.gives.forEach { assertTrue("${c.id}/${p.id} gives $it", it in clues) }
                p.needs.forEach { assertTrue("${c.id}/${p.id} needs $it", it in props) }
            }
            for (q in c.people.flatMap { it.questions }) {
                (q.needs + q.gives).forEach { assertTrue("${c.id}/${q.id} -> $it", it in clues) }
            }
            for (d in c.deductions) listOf(d.a, d.b, d.gives).forEach { assertTrue("${c.id} deduction -> $it", it in clues) }
            val s = c.solution
            assertTrue(c.suspects.any { it.id == s.culprit })
            (s.motive + s.proof).forEach { assertTrue("${c.id} solution -> $it", it in clues) }
            for (p in c.suspects) if (p.id != s.culprit) assertTrue("${c.id}: why not ${p.id}", p.id in s.wrong)
            // Each clue comes from exactly one place, of the matching kind.
            for (clue in c.clues) {
                val from = c.props.count { clue.id in it.gives } +
                    c.people.sumOf { p -> p.questions.count { clue.id in it.gives } } +
                    c.deductions.count { it.gives == clue.id }
                assertEquals("${c.id}: ${clue.id} given once", 1, from)
                val expected = when {
                    c.deductions.any { it.gives == clue.id } -> ClueKind.DEDUCTION
                    c.people.any { p -> p.questions.any { clue.id in it.gives } } -> ClueKind.TESTIMONY
                    else -> ClueKind.EVIDENCE
                }
                assertEquals("${c.id}: ${clue.id} kind", expected, clue.kind)
            }
        }
    }

    @Test
    fun everythingCanBeFound() {
        for (c in Cases.all) {
            val inv = Investigation(c)
            do {
                val before = inv.found.size + inv.opened.size + inv.asked.size
                for (p in c.props) if (inv.reachable(p.id)) inv.handle(p.id, sawHidden = true)
                for (q in c.people.flatMap { it.questions }) if (inv.canAsk(q)) inv.ask(q.id)
                for (d in c.deductions) inv.link(d.a, d.b)
                val after = inv.found.size + inv.opened.size + inv.asked.size
            } while (after > before)
            assertEquals("${c.id}: every clue found", c.clues.map { it.id }.toSet(), inv.found.toSet())
            assertEquals("${c.id}: every prop handled", c.props.map { it.id }.toSet(), inv.opened.toSet())
        }
    }

    @Test
    fun followingTheHintsSolvesEachCase() {
        for (c in Cases.all) {
            val inv = Investigation(c)
            var steps = 0
            while (!inv.solved) {
                if (++steps > 60) fail("${c.id}: hints go round in circles")
                when (val h = inv.nextStep()) {
                    is Investigation.Hint.Look -> inv.handle(h.prop.id, sawHidden = true)
                    is Investigation.Hint.Ask -> inv.ask(h.question.id)
                    is Investigation.Hint.Link -> assertTrue(inv.link(h.a.id, h.b.id) is Investigation.Link.New)
                    Investigation.Hint.Accuse -> {
                        val m = c.solution.motive.first { it in inv.found }
                        val p = c.solution.proof.first { it in inv.found }
                        assertEquals(Investigation.Verdict.Right, inv.accuse(c.solution.culprit, m, p))
                    }
                    Investigation.Hint.Done -> fail("done before solved")
                }
            }
            assertEquals(Investigation.Hint.Done, inv.nextStep())
            println("${c.id}: solved in $steps hinted steps, ${inv.found.size}/${c.clues.size} clues")
        }
    }

    @Test
    fun wrongAnswersSayWhy() {
        for (c in Cases.all) {
            val inv = Investigation(c)
            c.clues.forEach { inv.found += it.id }
            val m = c.solution.motive.first()
            val p = c.solution.proof.first()
            for (s in c.suspects) if (s.id != c.solution.culprit) {
                val v = inv.accuse(s.id, m, p)
                assertTrue(v is Investigation.Verdict.WrongPerson && v.why == c.solution.wrong[s.id])
            }
            val weak = c.clues.first { it.id !in c.solution.motive && it.id !in c.solution.proof }.id
            assertEquals(Investigation.Verdict.WeakMotive, inv.accuse(c.solution.culprit, weak, p))
            assertEquals(Investigation.Verdict.WeakProof, inv.accuse(c.solution.culprit, m, weak))
            assertFalse(inv.solved)
            assertEquals(Investigation.Verdict.Right, inv.accuse(c.solution.culprit, m, p))
        }
    }

    @Test
    fun aHiddenSideMustBeSeen() {
        val c = Cases.byId("wutong")
        val inv = Investigation(c)
        assertTrue(inv.handle("watchbox").isEmpty())
        inv.handle("drawer")
        assertTrue(inv.handle("watchbox").isEmpty())
        assertEquals(listOf("paint"), inv.handle("watchbox", sawHidden = true).map { it.id })
    }

    @Test
    fun aQuestionWaitsForItsClue() {
        val c = Cases.byId("wutong")
        val inv = Investigation(c)
        assertTrue(inv.ask("lin_lamp").isEmpty())
        assertFalse("lin_lamp" in inv.asked)
        inv.handle("lamp")
        assertEquals(listOf("creak"), inv.ask("lin_lamp").map { it.id })
    }

    @Test
    fun savedWorkComesBack() {
        val book = Casebook()
        val inv = book.current
        inv.handle("drawer")
        inv.handle("rentbook")
        inv.ask("zhou_when")
        inv.link("pry", "zhou_key")
        val again = Casebook(book.serialize())
        assertEquals(inv.found, again.current.found)
        assertEquals(inv.opened, again.current.opened)
        assertEquals(inv.asked, again.current.asked)
        assertFalse(again.open(Cases.byId("inn")))
    }

    @Test
    fun closingACaseOpensTheNext() {
        val book = Casebook()
        val c = book.current.case
        c.clues.forEach { book.current.found += it.id }
        book.current.accuse(c.solution.culprit, c.solution.motive.first(), c.solution.proof.first())
        assertTrue(book.open(Cases.byId("inn")))
        assertEquals("inn", book.next()?.id)
        assertEquals("inn", Casebook(book.serialize()).current.case.id)
    }
}
