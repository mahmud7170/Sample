package com.example.ratecalc

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@Composable
private fun cardColors() = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)

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
    var renameTitle by remember { mutableStateOf(false) }
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { showProfiles = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, "Profiles", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    profile?.name ?: "…",
                    modifier = Modifier.weight(1f),
                    fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, maxLines = 2
                )
                IconButton(onClick = { renameTitle = true }) { Icon(Icons.Default.Edit, "Rename") }
                IconButton(onClick = { showInvoice = true }) { Icon(Icons.Default.Share, "Invoice") }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Profiles") }, onClick = { showMenu = false; showProfiles = true })
                        DropdownMenuItem(text = { Text("Import CSV") }, onClick = { showMenu = false; importLauncher.launch(arrayOf("*/*")) })
                        DropdownMenuItem(text = { Text("Export CSV") }, onClick = {
                            showMenu = false
                            exportLauncher.launch("products.csv")
                        })
                    }
                }
            }
        },
        bottomBar = {
            Column {
                TotalBar(
                    total = grandTotal,
                    showHistoryButton = tab == 0,
                    clearLabel = if (tab == 0) "Clear All" else "Clear",
                    canSave = selected.isNotEmpty(),
                    onHistory = { vm.saveHistory(selected) },
                    onClear = { confirmClear = true }
                )
                NavigationBar {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                        icon = { Icon(Icons.Default.ShoppingCart, null) }, label = { Text("Calculate") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                        icon = { Icon(Icons.Default.Star, null) }, label = { Text("CC") })
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                        icon = { Icon(Icons.Default.Menu, null) }, label = { Text("History") })
                }
            }
        },
        floatingActionButton = {
            if (tab != 2) {
                FloatingActionButton(
                    onClick = { showAdd = true },
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) { Icon(Icons.Default.Add, "Add product") }
            }
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            when (tab) {
                0 -> CalculateTab(filtered, products.isEmpty(), query, { query = it }, onTap = { qtyFor = it })
                1 -> StockTab(selected, query, { query = it })
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
    if (renameTitle) {
        RenameDialog(
            initial = profile?.name ?: "",
            onSave = { n -> profile?.let { vm.renameProfile(it.id, n) }; renameTitle = false },
            onDismiss = { renameTitle = false }
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
        QtyDialog(
            product = p,
            onSet = { vm.setQty(p, it); qtyFor = null },
            onEdit = { qtyFor = null; editing = p },
            onDismiss = { qtyFor = null }
        )
    }
    if (showInvoice) {
        InvoiceScreen(
            items = selected,
            onShare = { InvoiceImage.share(ctx, selected) },
            onClose = { showInvoice = false }
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Clear all quantities?",
            text = "All quantities in this profile will be reset to 0.",
            confirmLabel = "Clear",
            onConfirm = { vm.clearQuantities(); confirmClear = false },
            onDismiss = { confirmClear = false }
        )
    }
}

@Composable
fun TotalBar(
    total: Double,
    showHistoryButton: Boolean,
    clearLabel: String,
    canSave: Boolean,
    onHistory: () -> Unit,
    onClear: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Grand Total", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(money(total), fontSize = 38.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (showHistoryButton) {
                    Button(onClick = onHistory, enabled = canSave) {
                        Text("History", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                TextButton(onClick = onClear) {
                    Text(clearLabel, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

@Composable
fun SearchField(query: String, onQuery: (String) -> Unit) {
    OutlinedTextField(
        value = query, onValueChange = onQuery,
        placeholder = { Text("Search Products", fontSize = 18.sp) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, "Clear search") }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun QtyBadge(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(
        text, textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(32.dp)
    )
}

@Composable
fun CalculateTab(
    products: List<Product>,
    noProducts: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onTap: (Product) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SearchField(query, onQuery) }
        if (products.isEmpty()) {
            item {
                EmptyHint(
                    if (noProducts) "No products yet.\nTap + to add one, or import a CSV from the ⋮ menu."
                    else "No products match your search."
                )
            }
        }
        items(products, key = { it.id }) { p ->
            ElevatedCard(
                onClick = { onTap(p) },
                shape = RoundedCornerShape(28.dp),
                colors = cardColors(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(horizontal = 22.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Rate: ${money(p.rate)}", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("Total: ${money(Calc.lineTotal(p))}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    QtyBadge("Qty: ${Calc.qtyLabel(p)}")
                }
            }
        }
    }
}

@Composable
fun StockTab(selected: List<Product>, query: String, onQuery: (String) -> Unit) {
    val q = query.trim()
    val items = selected.filter {
        (Calc.ccCount(it) > 0 || Calc.potCount(it) > 0) && (q.isEmpty() || it.name.contains(q, ignoreCase = true))
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SearchField(query, onQuery) }
        if (items.isEmpty()) item { EmptyHint("Set quantities on the Calculate tab (and “Tablets per CC” in each product) to see CC totals here.") }
        items(items, key = { it.id }) { p ->
            val cc = Calc.ccCount(p)
            val pot = Calc.potCount(p)
            ElevatedCard(
                shape = RoundedCornerShape(28.dp),
                colors = cardColors(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(horizontal = 22.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        if (cc > 0) {
                            Text("Tablets per CC: ${num(p.cc)}", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text("Total CC: ${String.format(java.util.Locale.US, "%.2f", cc)}", fontSize = 22.sp,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                        }
                        if (pot > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text("Tablets per Pot: ${num(p.pot)}", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text("Total Pot: ${String.format(java.util.Locale.US, "%.2f", pot)}", fontSize = 22.sp,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    QtyBadge(
                        listOfNotNull(
                            if (cc > 0) "${num(cc)} CC" else null,
                            if (pot > 0) "${num(pot)} Pot" else null
                        ).joinToString(" · ")
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryTab(history: List<HistoryEntry>, onShare: (HistoryEntry) -> Unit, onDelete: (HistoryEntry) -> Unit) {
    var toDelete by remember { mutableStateOf<HistoryEntry?>(null) }
    var expandedId by remember { mutableStateOf<Long?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (history.isEmpty()) item { EmptyHint("No saved calculations yet.\nTap History on the Calculate tab to save one.") }
        items(history, key = { it.id }) { h ->
            ElevatedCard(
                onClick = { expandedId = if (expandedId == h.id) null else h.id },
                shape = RoundedCornerShape(28.dp),
                colors = cardColors(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(h.profileName, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Saved " + formatDate(h.createdAt), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(money(h.total), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    if (expandedId == h.id) {
                        Spacer(Modifier.height(10.dp))
                        Text(h.body, style = MaterialTheme.typography.bodyMedium)
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
            title = "Delete entry?",
            text = "This saved calculation will be removed.",
            onConfirm = { onDelete(h); toDelete = null },
            onDismiss = { toDelete = null }
        )
    }
}
