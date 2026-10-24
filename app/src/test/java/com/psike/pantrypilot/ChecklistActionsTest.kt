package com.psike.pantrypilot

import org.junit.Assert.*
import org.junit.Test

class ChecklistActionsTest {
    private fun quantity(amount: String, unit: String = "") = checkNotNull(Quantity.parse(amount, unit))
    private fun recurring(current: String = "2", weekly: String = "4") = ChecklistItem(
        "beans", "Beans", quantity(current, "cans"), true, quantity(weekly, "cans"),
    )

    @Test fun `new week restores saved quantity after a smaller purchase`() {
        val original = PantrySnapshot(checklist = listOf(recurring()))
        val needed = ChecklistActions.answer(original, "beans", Answer.NEED)
        val bought = ChecklistActions.setPurchased(needed, "beans", true)

        val nextWeek = ChecklistActions.startNewWeek(bought)

        assertEquals(quantity("4", "cans"), nextWeek.checklist.single().quantity)
        assertEquals(Answer.UNCHECKED, nextWeek.checklist.single().answer)
        assertEquals(bought.shopping, nextWeek.shopping)
        assertEquals(quantity("2", "cans"), nextWeek.shopping.single().quantity)
        assertTrue(nextWeek.shopping.single().purchased)
    }

    @Test fun `editing this week's quantity does not replace saved weekly quantity`() {
        val state = PantrySnapshot(checklist = listOf(recurring()))
        val edited = ChecklistActions.saveItem(
            state, "beans", "Beans", quantity("1", "cans"), true, quantity("4", "cans"),
        )
        assertEquals(quantity("1", "cans"), edited.checklist.single().quantity)
        assertEquals(quantity("4", "cans"), ChecklistActions.startNewWeek(edited).checklist.single().quantity)
    }

    @Test fun `explicitly changing weekly quantity is used on future weeks`() {
        val state = PantrySnapshot(checklist = listOf(recurring()))
        val edited = ChecklistActions.saveItem(
            state, "beans", "Beans", quantity("1", "cans"), true, quantity("6", "cans"),
        )
        val next = ChecklistActions.startNewWeek(edited)
        assertEquals(quantity("6", "cans"), next.checklist.single().quantity)
        assertEquals(next, ChecklistActions.startNewWeek(next))
    }

    @Test fun `weekly refill removes one-time checklist entries but keeps all shopping entries`() {
        val oneTime = recurring().copy(id = "rice", name = "Rice", repeatsWeekly = false)
        val state = PantrySnapshot(
            checklist = listOf(recurring(), oneTime),
            shopping = listOf(
                ShoppingItem("beans", "Beans", quantity("2", "cans")),
                ShoppingItem("rice", "Rice", quantity("1.5", "lb"), purchased = true),
            ),
        )
        val next = ChecklistActions.startNewWeek(state)
        assertEquals(listOf("beans"), next.checklist.map { it.id })
        assertEquals(state.shopping, next.shopping)
    }

    @Test fun `need sends this week's quantity and does not duplicate an existing shopping entry`() {
        val state = PantrySnapshot(checklist = listOf(recurring()))
        val once = ChecklistActions.answer(state, "beans", Answer.NEED)
        val twice = ChecklistActions.answer(once, "beans", Answer.NEED)
        assertEquals(1, twice.shopping.size)
        assertEquals(quantity("2", "cans"), twice.shopping.single().quantity)
        assertEquals(quantity("4", "cans"), twice.checklist.single().weeklyQuantity)
    }

    @Test fun `new-week need reuses purchased entry without adding quantities together`() {
        val state = PantrySnapshot(checklist = listOf(recurring()))
        val bought = ChecklistActions.setPurchased(ChecklistActions.answer(state, "beans", Answer.NEED), "beans", true)
        val next = ChecklistActions.answer(ChecklistActions.startNewWeek(bought), "beans", Answer.NEED)
        assertEquals(1, next.shopping.size)
        assertEquals(quantity("4", "cans"), next.shopping.single().quantity)
        assertFalse(next.shopping.single().purchased)
    }

    @Test fun `editing a needed item updates the shopping amount while preserving its weekly default`() {
        val state = ChecklistActions.answer(PantrySnapshot(checklist = listOf(recurring())), "beans", Answer.NEED)
        val edited = ChecklistActions.saveItem(
            state, "beans", "Red beans", quantity("3", "cans"), true, quantity("4", "cans"),
        )
        assertEquals("Red beans", edited.shopping.single().name)
        assertEquals(quantity("3", "cans"), edited.shopping.single().quantity)
        assertEquals(quantity("4", "cans"), edited.checklist.single().weeklyQuantity)
    }

    @Test fun `have corrects an unpurchased need without changing the weekly default`() {
        val needed = ChecklistActions.answer(PantrySnapshot(checklist = listOf(recurring())), "beans", Answer.NEED)
        val have = ChecklistActions.answer(needed, "beans", Answer.HAVE)
        assertTrue(have.shopping.isEmpty())
        assertEquals(quantity("4", "cans"), have.checklist.single().weeklyQuantity)
    }

    @Test fun `removing checklist entry does not remove its shopping entry`() {
        val needed = ChecklistActions.answer(PantrySnapshot(checklist = listOf(recurring())), "beans", Answer.NEED)
        val removed = ChecklistActions.removeItem(needed, "beans")
        assertTrue(removed.checklist.isEmpty())
        assertEquals(needed.shopping, removed.shopping)
    }

    @Test fun `decimal quantities retain precision and support decimal comma input`() {
        assertEquals("0.125 lb", quantity("0.125", "lb").label)
        assertEquals("2.5 bags", quantity(" 2,500 ", " bags ").label)
        assertEquals("1000", quantity("1000.000").amount)
        assertEquals("0.5", quantity(".5").amount)
    }

    @Test fun `non-positive and malformed quantities are rejected`() {
        listOf("", "0", "0.0", "-1", "NaN", "Infinity", "1e3", "2..5", "1,2,3").forEach {
            assertNull("Must reject $it", Quantity.parse(it))
        }
    }
}
