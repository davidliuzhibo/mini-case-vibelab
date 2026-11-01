package com.vrgames.minicase.core

/**
 * One case: the miniature scene's props, the people in it, the clues they lead to, which clues
 * combine into deductions, and the answer. Pure data; [Investigation] plays it.
 *
 * Everything is reached in the scene or by asking: a prop gives its clues when the player opens,
 * lifts or examines it ([Prop.action]); a question gives testimony; two clues linked on the case
 * board give a deduction. Prop and question gates ([Prop.needs], [Question.needs]) make the order.
 */
data class Case(
    val id: String,
    val number: Int,
    val title: String,
    val place: String,
    val time: String,
    /** What the client tells the detective: the opening of the case file. */
    val brief: String,
    val props: List<Prop>,
    val people: List<Person>,
    val clues: List<Clue>,
    val deductions: List<Deduction>,
    val solution: Solution,
    /** The whole story, told once the case is closed. */
    val truth: String,
) {
    fun prop(id: String) = props.first { it.id == id }
    fun person(id: String) = people.first { it.id == id }
    fun clue(id: String) = clues.first { it.id == id }
    val suspects get() = people.filter { it.suspect }
}

/** How a prop is handled in the scene. */
enum class Action {
    /** Slide a drawer out, swing a door or lid open. */
    OPEN,
    /** Lift something off (a roof, a loose floorboard): it comes away and stays off. */
    LIFT,
    /** Pick it up and look at it: it floats up close, the hand turns it. */
    EXAMINE,
}

data class Prop(
    val id: String,
    val name: String,
    val action: Action,
    /** Clue ids this prop gives. */
    val gives: List<String> = emptyList(),
    /** Props that must be opened or lifted first (a box in a drawer). */
    val needs: List<String> = emptyList(),
    /**
     * For EXAMINE: the clue shows only once the player turns the object to see this side of it
     * (its underside, its back, a pocket). Null: looking at it is enough.
     */
    val hiddenSide: Side? = null,
    /** Where the hint points the player, in a few words ("灶房的灶膛"). */
    val where: String,
)

/** A side of an object, in the object's own frame (+y up, -z its back). */
enum class Side(val x: Double, val y: Double, val z: Double, val label: String) {
    BOTTOM(0.0, -1.0, 0.0, "底部"),
    BACK(0.0, 0.0, -1.0, "背面"),
    LEFT(-1.0, 0.0, 0.0, "左侧"),
    RIGHT(1.0, 0.0, 0.0, "右侧"),
    TOP(0.0, 1.0, 0.0, "顶上"),
}

enum class ClueKind(val label: String) { EVIDENCE("物证"), TESTIMONY("证词"), DEDUCTION("推论") }

data class Clue(val id: String, val kind: ClueKind, val title: String, val text: String)

data class Person(
    val id: String,
    val name: String,
    /** Who they are, in a few words ("房东" "跑堂"). */
    val role: String,
    val intro: String,
    val questions: List<Question>,
    /** False for the client: they are asked, never accused. */
    val suspect: Boolean = true,
)

data class Question(
    val id: String,
    val ask: String,
    val answer: String,
    /** Clues the player must hold to ask it (it is about them). */
    val needs: List<String> = emptyList(),
    /** Testimony clue ids the answer gives. */
    val gives: List<String> = emptyList(),
)

/** Linking clues [a] and [b] on the board gives clue [gives]. */
data class Deduction(val a: String, val b: String, val gives: String)

/**
 * The answer: who did it, a clue that shows why (any of [motive]), and a clue that proves it
 * (any of [proof]). [wrong]: what the board says when the player names someone else, per person.
 */
data class Solution(
    val culprit: String,
    val motive: Set<String>,
    val proof: Set<String>,
    val wrong: Map<String, String>,
)
