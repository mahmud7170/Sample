package com.example.ratecalc

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val profileDao = db.profileDao()
    private val productDao = db.productDao()
    private val historyDao = db.historyDao()

    private val currentId = MutableStateFlow<Long?>(null)

    val profiles = profileDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val currentProfile = combine(profiles, currentId) { list, id -> list.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val products = currentId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else productDao.observe(id)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val history = historyDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            if (profileDao.count() == 0) profileDao.insert(Profile(name = formatDate(System.currentTimeMillis())))
            currentId.value = profileDao.latest()?.id
        }
    }

    private fun say(msg: String) { _events.tryEmit(msg) }

    // ---- products ----
    fun addProduct(name: String, rate: Double, cc: Double, pot: Double, unit: String) {
        val pid = currentId.value ?: return
        viewModelScope.launch {
            if (productDao.findByName(pid, name) != null) { say("\"$name\" already exists"); return@launch }
            productDao.insert(Product(profileId = pid, name = name, rate = rate, cc = cc, pot = pot, soldUnit = unit))
        }
    }

    fun updateProduct(old: Product, name: String, rate: Double, cc: Double, pot: Double, unit: String) {
        viewModelScope.launch {
            val dup = productDao.findByName(old.profileId, name)
            if (dup != null && dup.id != old.id) { say("\"$name\" already exists"); return@launch }
            productDao.update(old.copy(name = name, rate = rate, cc = cc, pot = pot, soldUnit = unit))
        }
    }

    fun deleteProduct(p: Product) { viewModelScope.launch { productDao.delete(p) } }

    // FIX: quantity can never go negative or overflow.
    fun setQty(p: Product, q: Int) { viewModelScope.launch { productDao.setQty(p.id, q.coerceIn(0, 999_999)) } }

    fun clearQuantities() {
        val pid = currentId.value ?: return
        viewModelScope.launch { productDao.clearQty(pid) }
    }

    // ---- profiles ----
    fun switchProfile(id: Long) {
        viewModelScope.launch {
            profileDao.touch(id, System.currentTimeMillis())
            currentId.value = id
        }
    }

    fun createProfile(name: String) {
        viewModelScope.launch {
            val id = profileDao.insert(Profile(name = name.ifBlank { formatDate(System.currentTimeMillis()) }))
            currentId.value = id
        }
    }

    fun renameProfile(id: Long, name: String) { viewModelScope.launch { profileDao.rename(id, name) } }

    fun deleteProfile(id: Long) {
        viewModelScope.launch {
            profileDao.delete(id) // products cascade
            if (currentId.value == id) {
                if (profileDao.count() == 0) currentId.value = profileDao.insert(Profile(name = formatDate(System.currentTimeMillis())))
                else currentId.value = profileDao.latest()?.id
            }
        }
    }

    // ---- history ----
    fun saveHistory(items: List<Product>) {
        val name = currentProfile.value?.name ?: "Profile"
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            historyDao.insert(
                HistoryEntry(
                    profileName = name,
                    createdAt = now,
                    total = items.sumOf { Calc.lineTotal(it) },
                    body = Calc.invoiceText(items)
                )
            )
            say("Saved to history")
        }
    }

    fun deleteHistory(h: HistoryEntry) { viewModelScope.launch { historyDao.delete(h.id) } }

    // ---- CSV ----
    fun exportTo(uri: Uri) {
        val pid = currentId.value ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val csv = Csv.export(productDao.all(pid))
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                        it.write(csv.toByteArray(Charsets.UTF_8))
                    } ?: error("Cannot open file")
                }
                say("Exported successfully")
            } catch (e: Exception) {
                say("Export failed: ${e.message}")
            }
        }
    }

    fun importFrom(uri: Uri) {
        val pid = currentId.value ?: return
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: error("Cannot open file")
                }
                val rows = Csv.parse(text)
                rows.forEach { r ->
                    val existing = productDao.findByName(pid, r.name)
                    if (existing == null) {
                        productDao.insert(Product(profileId = pid, name = r.name, rate = r.rate, cc = r.cc, pot = r.pot, soldUnit = r.unit, quantity = r.qty))
                    } else {
                        productDao.update(existing.copy(rate = r.rate, cc = r.cc, pot = r.pot, soldUnit = r.unit, quantity = r.qty))
                    }
                }
                say("Imported ${rows.size} products")
            } catch (e: Exception) {
                say("Import failed: ${e.message}")
            }
        }
    }
}
