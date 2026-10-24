package com.psike.pantrypilot

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.util.concurrent.Executors

class PantryViewModel(application: Application) : AndroidViewModel(application) {
    var state by mutableStateOf(PantrySnapshot())
        private set
    var busy by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val database = PantryDatabase(application)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var cleared = false

    init { reload() }

    fun reload() = runOperation { database.load() }

    fun saveItem(
        id: String?, name: String, quantity: Quantity,
        repeatsWeekly: Boolean, weeklyQuantity: Quantity,
    ) = update { ChecklistActions.saveItem(it, id, name, quantity, repeatsWeekly, weeklyQuantity) }

    fun answer(id: String, answer: Answer) = update { ChecklistActions.answer(it, id, answer) }
    fun setPurchased(id: String, purchased: Boolean) = update {
        ChecklistActions.setPurchased(it, id, purchased)
    }
    fun startNewWeek() = update(ChecklistActions::startNewWeek)
    fun removeItem(id: String) = update { ChecklistActions.removeItem(it, id) }

    private fun update(change: (PantrySnapshot) -> PantrySnapshot) = runOperation {
        change(database.load()).also(database::save)
    }

    private fun runOperation(operation: () -> PantrySnapshot) {
        busy = true
        error = null
        worker.execute {
            val result = runCatching(operation)
            main.post {
                if (!cleared) {
                    result.onSuccess { state = it }.onFailure {
                        Log.e("PantryPilot", "Could not load or save the lists", it)
                        error = "Your lists could not be loaded or saved. Please try again."
                    }
                    busy = false
                }
            }
        }
    }

    override fun onCleared() {
        cleared = true
        worker.execute { database.close() }
        worker.shutdown()
        super.onCleared()
    }
}
