package com.psike.pantrypilot

import java.math.BigDecimal
import java.util.UUID

/** Decimal text avoids rounding quantities through a floating-point database column. */
@ConsistentCopyVisibility
data class Quantity private constructor(val amount: String, val unit: String) {
    val label: String get() = listOf(amount, unit).filter { it.isNotBlank() }.joinToString(" ")

    companion object {
        fun parse(amount: String, unit: String = ""): Quantity? {
            val input = amount.trim().replace(',', '.')
            if (input.length > 32 || !input.matches(Regex("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))) {
                return null
            }
            val number = input.toBigDecimalOrNull() ?: return null
            if (number <= BigDecimal.ZERO) return null
            return Quantity(number.stripTrailingZeros().toPlainString(), unit.trim())
        }
    }
}

enum class Answer { UNCHECKED, HAVE, NEED }

data class ChecklistItem(
    val id: String,
    val name: String,
    val quantity: Quantity,
    val repeatsWeekly: Boolean,
    val weeklyQuantity: Quantity,
    val answer: Answer = Answer.UNCHECKED,
)

data class ShoppingItem(
    val id: String,
    val name: String,
    val quantity: Quantity,
    val purchased: Boolean = false,
)

data class PantrySnapshot(
    val checklist: List<ChecklistItem> = emptyList(),
    val shopping: List<ShoppingItem> = emptyList(),
)

/** Current-week edits and shopping purchases must never overwrite weeklyQuantity. */
object ChecklistActions {
    fun saveItem(
        state: PantrySnapshot,
        id: String?,
        name: String,
        quantity: Quantity,
        repeatsWeekly: Boolean,
        weeklyQuantity: Quantity,
    ): PantrySnapshot {
        require(name.isNotBlank()) { "An item needs a name." }
        val previous = state.checklist.find { it.id == id }
        val item = ChecklistItem(
            id = previous?.id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            quantity = quantity,
            repeatsWeekly = repeatsWeekly,
            weeklyQuantity = weeklyQuantity,
            answer = previous?.answer ?: Answer.UNCHECKED,
        )
        val checklist = if (previous == null) state.checklist + item else {
            state.checklist.map { if (it.id == item.id) item else it }
        }
        // An unpurchased shopping entry follows current-week edits, never the weekly default.
        val shopping = state.shopping.map {
            if (it.id == item.id && !it.purchased) {
                it.copy(name = item.name, quantity = item.quantity)
            } else it
        }
        return state.copy(checklist = checklist, shopping = shopping)
    }

    fun answer(state: PantrySnapshot, id: String, answer: Answer): PantrySnapshot {
        val item = state.checklist.find { it.id == id } ?: return state
        val shopping = when (answer) {
            Answer.NEED -> {
                val entry = ShoppingItem(item.id, item.name, item.quantity)
                if (state.shopping.any { it.id == id }) {
                    state.shopping.map { if (it.id == id) entry else it }
                } else state.shopping + entry
            }
            Answer.HAVE -> state.shopping.filterNot { it.id == id && !it.purchased }
            Answer.UNCHECKED -> state.shopping
        }
        return state.copy(
            checklist = state.checklist.map { if (it.id == id) it.copy(answer = answer) else it },
            shopping = shopping,
        )
    }

    fun setPurchased(state: PantrySnapshot, id: String, purchased: Boolean) = state.copy(
        shopping = state.shopping.map { if (it.id == id) it.copy(purchased = purchased) else it },
    )

    fun removeItem(state: PantrySnapshot, id: String) = state.copy(
        checklist = state.checklist.filterNot { it.id == id },
    )

    fun startNewWeek(state: PantrySnapshot) = state.copy(
        checklist = state.checklist.filter { it.repeatsWeekly }.map {
            it.copy(quantity = it.weeklyQuantity, answer = Answer.UNCHECKED)
        },
        // Shopping entries, their quantities, and purchased states survive this action intact.
    )
}
