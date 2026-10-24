package com.psike.pantrypilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model = ViewModelProvider(this)[PantryViewModel::class.java]
        setContent { PantryPilotApp(model) }
    }
}

private enum class Screen(val route: String, val label: String, val symbol: String) {
    Checklist("checklist", "Checklist", "✓"),
    Shopping("shopping", "Shopping", "≡"),
    Settings("settings", "Settings", "⚙"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryPilotApp(model: PantryViewModel) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        val navController = rememberNavController()
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
            ?: Screen.Checklist.route
        var menuOpen by remember { mutableStateOf(false) }
        var confirmWeek by rememberSaveable { mutableStateOf(false) }
        var showEditor by rememberSaveable { mutableStateOf(false) }
        var editId by rememberSaveable { mutableStateOf<String?>(null) }
        val enabled = !model.busy && model.error == null

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Pantry Pilot") },
                    actions = {
                        if (currentRoute == Screen.Checklist.route) {
                            Box {
                                TextButton(onClick = { menuOpen = true }, enabled = enabled) {
                                    Text("More")
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Start new week") },
                                        onClick = { menuOpen = false; confirmWeek = true },
                                    )
                                }
                            }
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    Screen.entries.forEach { screen ->
                        NavigationBarItem(
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Text(screen.symbol) },
                            label = { Text(screen.label) },
                        )
                    }
                }
            },
            floatingActionButton = {
                if (currentRoute == Screen.Checklist.route && enabled) {
                    ExtendedFloatingActionButton(onClick = { editId = null; showEditor = true }) {
                        Text("Add item")
                    }
                }
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                model.error?.let { message ->
                    Column(Modifier.padding(16.dp)) {
                        Text(message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = model::reload, enabled = !model.busy) { Text("Try again") }
                    }
                }
                NavHost(navController, startDestination = Screen.Checklist.route, modifier = Modifier.weight(1f)) {
                    composable(Screen.Checklist.route) {
                        ChecklistScreen(
                            model.state.checklist, enabled,
                            onEdit = { editId = it.id; showEditor = true },
                            onAnswer = model::answer,
                        )
                    }
                    composable(Screen.Shopping.route) {
                        ShoppingScreen(model.state.shopping, enabled, model::setPurchased)
                    }
                    composable(Screen.Settings.route) { SettingsScreen() }
                }
            }
        }

        if (showEditor) {
            key(editId) {
                ItemEditor(
                    item = model.state.checklist.find { it.id == editId },
                    onDismiss = { showEditor = false },
                    onSave = { name, quantity, recurring, weekly ->
                        model.saveItem(editId, name, quantity, recurring, weekly)
                        showEditor = false
                    },
                    onRemove = editId?.let { id -> { model.removeItem(id); showEditor = false } },
                )
            }
        }
        if (confirmWeek) {
            AlertDialog(
                onDismissRequest = { confirmWeek = false },
                title = { Text("Start a new week?") },
                text = {
                    Text("Recurring items return with their saved weekly quantities and unanswered " +
                        "Have/Need choices. One-time checklist items are removed. Your shopping list stays as it is.")
                },
                confirmButton = {
                    TextButton(onClick = { model.startNewWeek(); confirmWeek = false }, enabled = enabled) {
                        Text("Start new week")
                    }
                },
                dismissButton = { TextButton(onClick = { confirmWeek = false }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun ChecklistScreen(
    items: List<ChecklistItem>,
    enabled: Boolean,
    onEdit: (ChecklistItem) -> Unit,
    onAnswer: (String, Answer) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Weekly checklist", style = MaterialTheme.typography.headlineSmall)
            Text("Check what you have. Send what you need to Shopping.")
        }
        if (items.isEmpty()) item {
            Text("Add your first item. Turn on weekly refill for supplies you check every week.")
        }
        items(items, key = { it.id }) { item ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    Text("Quantity this week: ${item.quantity.label}")
                    Text(
                        if (item.repeatsWeekly) "Each new week: ${item.weeklyQuantity.label}" else "This week only",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = item.answer == Answer.HAVE,
                            onClick = { onAnswer(item.id, Answer.HAVE) },
                            label = { Text("Have") }, enabled = enabled,
                        )
                        FilterChip(
                            selected = item.answer == Answer.NEED,
                            onClick = { onAnswer(item.id, Answer.NEED) },
                            label = { Text("Need") }, enabled = enabled,
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { onEdit(item) }, enabled = enabled) { Text("Edit") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShoppingScreen(
    items: List<ShoppingItem>, enabled: Boolean, onPurchased: (String, Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Shopping list", style = MaterialTheme.typography.headlineSmall)
            Text("${items.count { !it.purchased }} items left to buy")
        }
        if (items.isEmpty()) item { Text("Mark an item Need on your checklist to add it here.") }
        items(items.sortedBy { it.purchased }, key = { it.id }) { item ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = item.purchased,
                        onCheckedChange = { onPurchased(item.id, it) },
                        enabled = enabled,
                        modifier = Modifier.semantics { contentDescription = "Bought ${item.name}" },
                    )
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(
                            item.name, style = MaterialTheme.typography.titleMedium,
                            textDecoration = if (item.purchased) TextDecoration.LineThrough else null,
                        )
                        Text(item.quantity.label)
                    }
                    if (item.purchased) Text("Bought", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun ItemEditor(
    item: ChecklistItem?,
    onDismiss: () -> Unit,
    onSave: (String, Quantity, Boolean, Quantity) -> Unit,
    onRemove: (() -> Unit)?,
) {
    var name by rememberSaveable { mutableStateOf(item?.name ?: "") }
    var amount by rememberSaveable { mutableStateOf(item?.quantity?.amount ?: "1") }
    var unit by rememberSaveable { mutableStateOf(item?.quantity?.unit ?: "") }
    var recurring by rememberSaveable { mutableStateOf(item?.repeatsWeekly ?: false) }
    var weeklyAmount by rememberSaveable { mutableStateOf(item?.weeklyQuantity?.amount ?: "1") }
    var weeklyEdited by rememberSaveable { mutableStateOf(false) }
    val quantity = Quantity.parse(amount, unit)
    val weekly = if (recurring) Quantity.parse(weeklyAmount, unit) else item?.weeklyQuantity ?: quantity

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Add item" else "Edit item") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Item name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it
                        if (item == null && !weeklyEdited) weeklyAmount = it
                    },
                    label = { Text("Quantity this week") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = quantity == null,
                    supportingText = { if (quantity == null) Text("Enter a quantity greater than zero.") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = unit, onValueChange = { unit = it },
                    label = { Text("Unit (optional)") },
                    placeholder = { Text("cans, bags, lb…") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Refill checklist each week", modifier = Modifier.weight(1f))
                    Switch(checked = recurring, onCheckedChange = { recurring = it })
                }
                if (recurring) {
                    OutlinedTextField(
                        value = weeklyAmount,
                        onValueChange = { weeklyAmount = it; weeklyEdited = true },
                        label = { Text("Saved weekly quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = weekly == null,
                        supportingText = {
                            Text(if (weekly == null) "Enter a quantity greater than zero."
                                else "Used whenever you start a new week.")
                        },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
                onRemove?.let { remove ->
                    TextButton(onClick = remove) { Text("Remove from checklist") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && quantity != null && weekly != null,
                onClick = { if (quantity != null && weekly != null) onSave(name, quantity, recurring, weekly) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SettingsScreen() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Text("Weekly refill", style = MaterialTheme.typography.titleMedium)
        Text("Choose a saved weekly quantity for recurring items. Changing this week's quantity " +
            "doesn't change that saved amount.")
        Text("To begin the next checklist, open More → Start new week. " +
            "You will be asked to confirm. Your shopping list carries over.")
        Text("Your lists are saved on this phone and work offline.")
    }
}
