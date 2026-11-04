package com.example.ratecalc

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun shareText(ctx: Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(Intent.createChooser(i, "Share"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: MainViewModel) {
    val ctx = LocalContext.current
    val profiles by vm.profiles.collectAsState()
    val profile by vm.currentProfile.collectAsState()
    val products by vm.products.collectAsState()
    val history by vm.history.collectAsState()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var showProfiles by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showInvoice by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Product?>(null) }
    var qtyFor by remember { mutableStateOf<Product?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.events.collect { snack.showSnackbar(it) } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFrom(uri)
    }

    val selected = remember(products) { products.filter { it.quantity > 0 } }
    val grandTotal = remember(products) { products.sumOf { Calc.lineTotal(it) } }
    val filtered = remember(products, query) {
        val q = query.trim()
        if (q.isEmpty()) products else products.filter { it.name.contains(q, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.clickable { showProfiles = true }) {
                        Text(profile?.name ?: "Loading…", fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("Tap to switch profile", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Manage profiles") }, onClick = { showMenu = false; showProfiles = true })
                            DropdownMenuItem(text = { Text("Import CSV") }, onClick = { showMenu = false; importLauncher.launch(arrayOf("*/*")) })
                            DropdownMenuItem(text = { Text("Export CSV") }, onClick = {
                                showMenu = false
                                exportLauncher.launch("${profile?.name ?: "products"}.csv")
                            })
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("🧮") }, label = { Text("Calculate") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("📦") }, label = { Text("CC / Pot") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Text("🕘") }, label = { Text("History") })
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Add product") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            when (tab) {
                0 -> CalculateTab(
                    products = filtered,
                    totalCount = products.size,
                    grandTotal = grandTotal,
                    selectedCount = selected.size,
                    query = query,
                    onQuery = { query = it },
                    onQty = { p, q -> vm.setQty(p, q) },
                    onEdit = { editing = it },
                    onTapQty = { qtyFor = it },
                    onInvoice = { showInvoice = true },
                    onClear = { confirmClear = true }
                )
                1 -> StockTab(selected)
                else -> HistoryTab(history, onShare = { shareText(ctx, it.body) }, onDelete = { vm.deleteHistory(it) })
            }
        }
    }

    if (showProfiles) {
        ProfilesDialog(
            profiles = profiles,
            currentId = profile?.id,
            onSelect = { vm.switchProfile(it) },
            onCreate = { vm.createProfile(it) },
            onRename = { id, n -> vm.renameProfile(id, n) },
            onDelete = { vm.deleteProfile(it) },
            onDismiss = { showProfiles = false }
        )
    }
    if (showAdd) {
        ProductDialog(
            initial = null,
            onSave = { n, r, c, p, u -> vm.addProduct(n, r, c, p, u); showAdd = false },
            onDelete = null,
            onDismiss = { showAdd = false }
        )
    }
    editing?.let { p ->
        ProductDialog(
            initial = p,
            onSave = { n, r, c, pt, u -> vm.updateProduct(p, n, r, c, pt, u); editing = null },
            onDelete = { vm.deleteProduct(p); editing = null },
            onDismiss = { editing = null }
        )
    }
    qtyFor?.let { p ->
        QtyDialog(p, onSet = { vm.setQty(p, it); qtyFor = null }, onDismiss = { qtyFor = null })
    }
    if (showInvoice) {
        InvoiceDialog(
            profileName = profile?.name ?: "Profile",
            items = selected,
            onShare = { shareText(ctx, it) },
            onSave = { vm.saveHistory(selected); showInvoice = false },
            onDismiss = { showInvoice = false }
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Clear quantities?",
            text = "All quantities in this profile will be reset to 0.",
            confirmLabel = "Clear",
            onConfirm = { vm.clearQuantities(); confirmClear = false },
            onDismiss = { confirmClear = false }
        )
    }
}

@Composable
fun CalculateTab(
    products: List<Product>,
    totalCount: Int,
    grandTotal: Double,
    selectedCount: Int,
    query: String,
    onQuery: (String) -> Unit,
    onQty: (Product, Int) -> Unit,
    onEdit: (Product) -> Unit,
    onTapQty: (Product) -> Unit,
    onInvoice: () -> Unit,
    onClear: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Grand total", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        money(grandTotal), fontSize = 34.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text("$selectedCount of $totalCount items selected", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onInvoice, enabled = selectedCount > 0) {
                            Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Invoice")
                        }
                        OutlinedButton(onClick = onClear, enabled = selectedCount > 0) { Text("Clear") }
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = onQuery,
                placeholder = { Text("Search products") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, "Clear search") }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (products.isEmpty()) {
            item {
                Text(
                    if (totalCount == 0) "No products yet.\nTap “Add product” or import a CSV from the ⋮ menu."
                    else "No products match your search.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(32.dp)
                )
            }
        }
        items(products, key = { it.id }) { p ->
            ProductCard(p, onQty = { onQty(p, it) }, onEdit = { onEdit(p) }, onTapQty = { onTapQty(p) })
        }
    }
}

@Composable
fun ProductCard(p: Product, onQty: (Int) -> Unit, onEdit: () -> Unit, onTapQty: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${money(Calc.unitPrice(p))} per ${Calc.unitLabel(p)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit ${p.name}") }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { if (p.quantity > 0) onQty(p.quantity - 1) }) {
                    Text("−", fontSize = 22.sp)
                }
                TextButton(onClick = onTapQty) {
                    Text("${p.quantity}", style = MaterialTheme.typography.titleLarge)
                }
                FilledTonalIconButton(onClick = { onQty(p.quantity + 1) }) {
                    Icon(Icons.Default.Add, "Increase")
                }
                Spacer(Modifier.weight(1f))
                Text(
                    money(Calc.lineTotal(p)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun StockTab(items: List<Product>) {
    val tablets = items.sumOf { Calc.tablets(it) }
    val cc = items.sumOf { Calc.ccCount(it) }
    val pot = items.sumOf { Calc.potCount(it) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat("Tablets", num(tablets))
                    Stat("Total CC", num(cc))
                    Stat("Total Pot", num(pot))
                }
            }
        }
        if (items.isEmpty()) {
            item {
                Text(
                    "Select quantities on the Calculate tab to see CC / Pot totals here.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(32.dp)
                )
            }
        }
        items(items, key = { it.id }) { p ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(p.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Tablets: ${num(Calc.tablets(p))}")
                        Text("CC: ${num(Calc.ccCount(p))}")
                        Text("Pot: ${num(Calc.potCount(p))}")
                    }
                }
            }
        }
    }
}

@Composable
fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun HistoryTab(history: List<HistoryEntry>, onShare: (HistoryEntry) -> Unit, onDelete: (HistoryEntry) -> Unit) {
    var toDelete by remember { mutableStateOf<HistoryEntry?>(null) }
    var expandedId by remember { mutableStateOf<Long?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (history.isEmpty()) {
            item {
                Text(
                    "No saved invoices yet.\nCreate an invoice and tap Save.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(32.dp)
                )
            }
        }
        items(history, key = { it.id }) { h ->
            ElevatedCard(Modifier.fillMaxWidth().clickable { expandedId = if (expandedId == h.id) null else h.id }) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(h.profileName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                            Text(formatDate(h.createdAt), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(money(h.total), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    if (expandedId == h.id) {
                        Spacer(Modifier.height(10.dp))
                        Text(h.body, style = MaterialTheme.typography.bodySmall)
                        Row {
                            IconButton(onClick = { onShare(h) }) { Icon(Icons.Default.Share, "Share") }
                            IconButton(onClick = { toDelete = h }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                    }
                }
            }
        }
    }
    toDelete?.let { h ->
        ConfirmDialog(
            title = "Delete invoice?",
            text = "This saved invoice will be removed.",
            onConfirm = { onDelete(h); toDelete = null },
            onDismiss = { toDelete = null }
        )
    }
}
