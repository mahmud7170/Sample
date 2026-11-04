package com.example.ratecalc

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = "Delete",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ProductDialog(
    initial: Product?,
    onSave: (String, Double, Double, Double, String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var rate by remember { mutableStateOf(initial?.let { num(it.rate) } ?: "") }
    var cc by remember { mutableStateOf(initial?.let { num(it.cc) } ?: "") }
    var pot by remember { mutableStateOf(initial?.let { num(it.pot) } ?: "") }
    var unit by remember { mutableStateOf(initial?.soldUnit ?: "Normal") }
    var error by remember { mutableStateOf<String?>(null) }
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add product" else "Edit product") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rate, onValueChange = { rate = it },
                    label = { Text("Rate (৳) per tablet") }, singleLine = true,
                    keyboardOptions = decimal, modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cc, onValueChange = { cc = it },
                        label = { Text("Tablets / CC") }, singleLine = true,
                        keyboardOptions = decimal, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = pot, onValueChange = { pot = it },
                        label = { Text("Tablets / Pot") }, singleLine = true,
                        keyboardOptions = decimal, modifier = Modifier.weight(1f)
                    )
                }
                Text("Sold as", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Calc.units.forEach { u ->
                        FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u) })
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val r = rate.toDoubleOrNull()
                if (name.isBlank()) {
                    error = "Enter a name"
                } else if (r == null || r < 0) {
                    error = "Enter a valid rate"
                } else {
                    onSave(name.trim(), r, cc.toDoubleOrNull() ?: 0.0, pot.toDoubleOrNull() ?: 0.0, unit)
                }
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
fun QtyDialog(product: Product, onSet: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(product.quantity.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(product.name) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() }.take(6) },
                label = { Text("Quantity (${Calc.unitLabel(product)})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onSet(text.toIntOrNull() ?: 0) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun InvoiceDialog(
    profileName: String,
    items: List<Product>,
    onShare: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val now = remember { System.currentTimeMillis() }
    val total = items.sumOf { Calc.lineTotal(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invoice") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("$profileName · ${formatDate(now)}", style = MaterialTheme.typography.labelMedium)
                HorizontalDivider()
                if (items.isEmpty()) Text("No items selected yet.")
                items.forEach { p ->
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${p.quantity} × ${money(Calc.unitPrice(p))} / ${Calc.unitLabel(p)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(money(Calc.lineTotal(p)))
                    }
                }
                HorizontalDivider()
                Row {
                    Text("Total", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(money(total), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = items.isNotEmpty(), onClick = { onShare(Calc.invoiceText(profileName, items, now)) }) { Text("Share") }
        },
        dismissButton = {
            Row {
                TextButton(enabled = items.isNotEmpty(), onClick = onSave) { Text("Save") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

@Composable
fun ProfilesDialog(
    profiles: List<Profile>,
    currentId: Long?,
    onSelect: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Profile?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Profile?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profiles") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                profiles.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = p.id == currentId, onClick = { onSelect(p.id); onDismiss() })
                        Text(p.name, Modifier.weight(1f), maxLines = 1)
                        IconButton(onClick = { renaming = p; renameText = p.name }) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename")
                        }
                        IconButton(onClick = { deleting = p }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    label = { Text("New profile name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(enabled = newName.isNotBlank(), onClick = { onCreate(newName.trim()); newName = ""; onDismiss() }) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )

    renaming?.let { p ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename profile") },
            text = {
                OutlinedTextField(
                    value = renameText, onValueChange = { renameText = it },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(enabled = renameText.isNotBlank(), onClick = { onRename(p.id, renameText.trim()); renaming = null }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } }
        )
    }

    deleting?.let { p ->
        ConfirmDialog(
            title = "Delete profile?",
            text = "\"${p.name}\" and all of its products will be deleted.",
            onConfirm = { onDelete(p.id); deleting = null },
            onDismiss = { deleting = null }
        )
    }
}
