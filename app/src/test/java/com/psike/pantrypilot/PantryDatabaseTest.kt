package com.psike.pantrypilot

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PantryDatabaseTest {
    private val name = "quantities-test.db"
    private lateinit var context: Context
    private lateinit var database: PantryDatabase
    private fun quantity(amount: String) = checkNotNull(Quantity.parse(amount, "bags"))
    private fun original() = PantrySnapshot(
        checklist = listOf(ChecklistItem("peas", "Peas", quantity("1.25"), true, quantity("4.5"), Answer.NEED)),
        shopping = listOf(ShoppingItem("peas", "Peas", quantity("1.25"), purchased = true)),
    )

    @Before fun openDatabase() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(name)
        database = PantryDatabase(context, name)
    }

    @After fun cleanUp() {
        database.close()
        context.deleteDatabase(name)
    }

    private fun reopen() {
        database.close()
        database = PantryDatabase(context, name)
    }

    @Test fun `saved weekly quantity survives restart then refill and another restart`() {
        val before = original()
        database.save(before)
        reopen()
        assertEquals(before, database.load())

        database.save(ChecklistActions.startNewWeek(database.load()))
        reopen()
        val restored = database.load()
        assertEquals(quantity("4.5"), restored.checklist.single().quantity)
        assertEquals(quantity("4.5"), restored.checklist.single().weeklyQuantity)
        assertEquals(Answer.UNCHECKED, restored.checklist.single().answer)
        assertEquals(before.shopping, restored.shopping)
    }

    @Test fun `failed write rolls back both lists including their programmed quantities`() {
        val before = original()
        database.save(before)
        val invalid = before.copy(checklist = before.checklist + before.checklist.single())
        try {
            database.save(invalid)
            fail("Duplicate item IDs should fail.")
        } catch (_: SQLiteConstraintException) {
            assertEquals(before, database.load())
        }
    }

    @Test fun `shopping items survive removal of one-time checklist entry and a restart`() {
        val before = original().let { state ->
            state.copy(checklist = state.checklist.map { it.copy(repeatsWeekly = false) })
        }
        database.save(ChecklistActions.startNewWeek(before))
        reopen()
        assertTrue(database.load().checklist.isEmpty())
        assertEquals(before.shopping, database.load().shopping)
    }
}
