package lol.dogon.gallery

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lol.dogon.gallery.data.FilterState
import lol.dogon.gallery.data.MediaGroup
import lol.dogon.gallery.data.MediaItem
import lol.dogon.gallery.data.MediaRepository
import lol.dogon.gallery.data.MediaTypeFilter
import lol.dogon.gallery.data.SortBy
import lol.dogon.gallery.data.TagIndexer
import lol.dogon.gallery.data.VaultStore

// ---- Renk paleti (dogongallery.html mockup ile birebir) ----
object DogonColors {
    val Bg = Color(0xFF000000)
    val Surface = Color(0xFF0C0C0E)
    val Surface2 = Color(0xFF141416)
    val Line = Color(0xFF1E1E21)
    val Text = Color(0xFFFDFCFF)
    val TextDim = Color(0xFF9B93AE)
    val TextFaint = Color(0xFF6B6B6F)
    val Accent = Color(0xFFA855F7)
    val Accent2 = Color(0xFFC9A6FF)
    val NavActive = Color(0xFF8A7BF0)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestHighestRefreshRate()
        setContent {
            DogonGalleryTheme {
                GalleryApp()
            }
        }
    }

    // Telefonun desteklediği en yüksek ekran yenileme hızını (ör. 120Hz/144Hz) talep eder.
    private fun requestHighestRefreshRate() {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }
        val bestMode = display?.supportedModes?.maxByOrNull { it.refreshRate }
        if (bestMode != null) {
            window.attributes = window.attributes.apply {
                preferredDisplayModeId = bestMode.modeId
            }
        }
    }
}

@Composable
fun DogonGalleryTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        background = DogonColors.Bg,
        surface = DogonColors.Surface,
        primary = DogonColors.Accent,
        secondary = DogonColors.Accent2,
        onBackground = DogonColors.Text,
        onSurface = DogonColors.Text
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}

private fun mediaPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_IMAGES
    else android.Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
fun GalleryApp() {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, mediaPermission()) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(mediaPermission())
    }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Zaman", "Albümler", "Klasörler", "Gizli", "Ara")

    // Tüm medya tek yerden yükleniyor, sekmeler arasında paylaşılıyor.
    var allItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var mediaLoading by remember { mutableStateOf(true) }
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            allItems = withContext(Dispatchers.IO) { MediaRepository.loadMediaItems(context) }
            mediaLoading = false
        }
    }

    var hiddenIds by remember { mutableStateOf(VaultStore.getHiddenIds(context)) }
    fun refreshHidden() { hiddenIds = VaultStore.getHiddenIds(context) }

    // Gizli Galeriye taşınmış öğeler diğer tüm sekmelerden (Zaman/Albümler/Klasörler/Ara) düşer.
    val visibleItems = remember(allItems, hiddenIds) {
        allItems.filter { it.id.toString() !in hiddenIds }
    }

    // Tür/Sırala/Yön filtresi — Zaman, Albümler ve Klasörler ekranlarının hepsi bunu paylaşır.
    var filterState by remember { mutableStateOf(FilterState()) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val filteredItems = remember(visibleItems, filterState) {
        MediaRepository.applyFilter(visibleItems, filterState)
    }

    Scaffold(
        containerColor = DogonColors.Bg,
        topBar = {
            DogonTopBar(
                showFilter = selectedTab in listOf(0, 1, 2),
                onFilterClick = { showFilterSheet = true }
            )
        },
        bottomBar = {
            DogonBottomNav(tabs = tabs, selected = selectedTab) { selectedTab = it }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DogonColors.Bg)
                .padding(padding)
        ) {
            when {
                !hasPermission -> PermissionRequest { launcher.launch(mediaPermission()) }
                mediaLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DogonColors.Accent)
                }
                else -> when (selectedTab) {
                    0 -> TimelineTab(filteredItems) { id ->
                        VaultStore.hideItem(context, id); refreshHidden()
                    }
                    1 -> AlbumsTab(filteredItems) { id ->
                        VaultStore.hideItem(context, id); refreshHidden()
                    }
                    2 -> FoldersTab(filteredItems) { id ->
                        VaultStore.hideItem(context, id); refreshHidden()
                    }
                    3 -> GizliTab(allItems, hiddenIds) { refreshHidden() }
                    4 -> SearchTab(visibleItems)
                }
            }

            if (showFilterSheet) {
                FilterSheet(
                    current = filterState,
                    onApply = { filterState = it; showFilterSheet = false },
                    onDismiss = { showFilterSheet = false }
                )
            }
        }
    }
}

@Composable
fun DogonTopBar(showFilter: Boolean, onFilterClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DogonColors.Bg)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "DogonGallery",
            color = DogonColors.Text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        if (showFilter) {
            IconButton(
                onClick = onFilterClick,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DogonColors.Surface)
            ) {
                Icon(Icons.Filled.FilterList, contentDescription = "Filtrele", tint = DogonColors.Text)
            }
        }
    }
}

@Composable
fun DogonBottomNav(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val icons = listOf(
        Icons.Filled.Schedule,
        Icons.Filled.PhotoLibrary,
        Icons.Filled.Folder,
        Icons.Filled.Lock,
        Icons.Filled.ManageSearch
    )
    NavigationBar(
        containerColor = DogonColors.Bg,
        tonalElevation = 0.dp
    ) {
        tabs.forEachIndexed { index, label ->
            val active = index == selected
            NavigationBarItem(
                selected = active,
                onClick = { onSelect(index) },
                icon = { Icon(icons[index], contentDescription = label) },
                label = { Text(label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = DogonColors.NavActive,
                    selectedTextColor = DogonColors.NavActive,
                    unselectedIconColor = DogonColors.TextFaint,
                    unselectedTextColor = DogonColors.TextFaint,
                    indicatorColor = DogonColors.Surface
                )
            )
        }
    }
}

@Composable
fun PermissionRequest(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = DogonColors.Accent, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            "Fotoğraf ve videoları görebilmek için galeri iznine ihtiyacımız var.",
            color = DogonColors.TextDim,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequest, colors = ButtonDefaults.buttonColors(containerColor = DogonColors.Accent)) {
            Text("İzin Ver")
        }
    }
}

// =====================================================================================
// ORTAK BİLEŞENLER
// =====================================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaGrid(items: List<MediaItem>, onLongPressHide: ((Long) -> Unit)? = null) {
    var pendingHideId by remember { mutableStateOf<Long?>(null) }

    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Fotoğraf/video bulunamadı", color = DogonColors.TextDim)
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(2.dp)
    ) {
        items(items, key = { it.id }) { item ->
            Box(
                modifier = Modifier
                    .padding(1.dp)
                    .aspectRatio(1f)
                    .background(DogonColors.Surface)
                    .combinedClickable(
                        onClick = { },
                        onLongClick = { if (onLongPressHide != null) pendingHideId = item.id }
                    )
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.uri)
                        .size(240)
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (item.isVideo) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(16.dp)
                    )
                }
            }
        }
    }

    val hideId = pendingHideId
    if (hideId != null && onLongPressHide != null) {
        AlertDialog(
            onDismissRequest = { pendingHideId = null },
            containerColor = DogonColors.Surface,
            title = { Text("Gizli Galeriye taşı", color = DogonColors.Text) },
            text = { Text("Bu öğe ana galeriden kaldırılıp Gizli Galeri'ye taşınacak.", color = DogonColors.TextDim) },
            confirmButton = {
                TextButton(onClick = { onLongPressHide(hideId); pendingHideId = null }) {
                    Text("Taşı", color = DogonColors.Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingHideId = null }) { Text("İptal", color = DogonColors.TextDim) }
            }
        )
    }
}

@Composable
fun BackHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Geri", tint = DogonColors.Text)
        }
        Spacer(Modifier.width(4.dp))
        Text(title, color = DogonColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

// =====================================================================================
// ZAMAN (TIMELINE)
// =====================================================================================

@Composable
fun TimelineTab(items: List<MediaItem>, onLongPressHide: (Long) -> Unit) {
    MediaGrid(items, onLongPressHide)
}

// =====================================================================================
// ALBÜMLER (kapak görselli grid)
// =====================================================================================

@Composable
fun AlbumsTab(items: List<MediaItem>, onLongPressHide: (Long) -> Unit) {
    val groups = remember(items) { MediaRepository.groupByFolder(items) }
    var opened by remember { mutableStateOf<MediaGroup?>(null) }

    val current = opened
    if (current == null) {
        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Albüm bulunamadı", color = DogonColors.TextDim)
            }
            return
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp)
        ) {
            items(groups, key = { it.name }) { group ->
                Column(
                    modifier = Modifier
                        .padding(6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(DogonColors.Surface)
                        .combinedClickable(onClick = { opened = group }, onLongClick = {})
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(group.cover.uri).size(300).crossfade(false).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)
                    )
                    Column(Modifier.padding(10.dp)) {
                        Text(group.name, color = DogonColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("${group.count} öğe", color = DogonColors.TextDim, fontSize = 11.sp)
                    }
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            BackHeader(current.name) { opened = null }
            MediaGrid(current.items, onLongPressHide)
        }
    }
}

// =====================================================================================
// KLASÖRLER (liste görünümü)
// =====================================================================================

@Composable
fun FoldersTab(items: List<MediaItem>, onLongPressHide: (Long) -> Unit) {
    val groups = remember(items) { MediaRepository.groupByFolder(items) }
    var opened by remember { mutableStateOf<MediaGroup?>(null) }

    val current = opened
    if (current == null) {
        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Klasör bulunamadı", color = DogonColors.TextDim)
            }
            return
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(groups, key = { it.name }) { group ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = { opened = group }, onLongClick = {})
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DogonColors.Surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = null, tint = DogonColors.Accent2)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(group.name, color = DogonColors.Text, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("${group.count} öğe", color = DogonColors.TextDim, fontSize = 12.sp)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = DogonColors.TextFaint)
                }
                Divider(color = DogonColors.Line, thickness = 0.5.dp)
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            BackHeader(current.name) { opened = null }
            MediaGrid(current.items, onLongPressHide)
        }
    }
}

// =====================================================================================
// GİZLİ GALERİ (PIN korumalı kasa)
// =====================================================================================

@Composable
fun GizliTab(allItems: List<MediaItem>, hiddenIds: Set<String>, onChanged: () -> Unit) {
    val context = LocalContext.current
    var hasPin by remember { mutableStateOf(VaultStore.hasPin(context)) }
    var unlocked by remember { mutableStateOf(false) }

    if (unlocked) {
        val hiddenItems = remember(allItems, hiddenIds) {
            allItems.filter { it.id.toString() in hiddenIds }
        }
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Gizli Galeri", color = DogonColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                TextButton(onClick = { unlocked = false }) {
                    Text("Kilitle", color = DogonColors.Accent)
                }
            }
            Text(
                "Bir öğeye uzun bas: ana galeriye geri al",
                color = DogonColors.TextFaint,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            MediaGrid(hiddenItems) { id -> VaultStore.unhideItem(context, id); onChanged() }
        }
        return
    }

    PinScreen(
        setupMode = !hasPin,
        onPinConfirmed = { pin ->
            if (!hasPin) {
                VaultStore.setPin(context, pin)
                hasPin = true
                unlocked = true
            }
        },
        onPinEntry = { pin ->
            VaultStore.checkPin(context, pin)
        },
        onUnlocked = { unlocked = true }
    )
}

@Composable
fun PinScreen(
    setupMode: Boolean,
    onPinConfirmed: (String) -> Unit,
    onPinEntry: (String) -> Boolean,
    onUnlocked: () -> Unit
) {
    // setup: 0 = ilk giriş, 1 = tekrar onay
    var stage by remember { mutableStateOf(0) }
    var firstPin by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    val title = when {
        setupMode && stage == 0 -> "Yeni PIN oluştur"
        setupMode && stage == 1 -> "PIN'i tekrar gir"
        else -> "Gizli Galeri"
    }
    val subtitle = when {
        error -> "PIN'ler uyuşmadı, tekrar dene"
        setupMode -> "4 haneli bir PIN belirle"
        else -> "Devam etmek için PIN'ini gir"
    }

    fun handleComplete() {
        if (setupMode) {
            if (stage == 0) {
                firstPin = pin
                pin = ""
                stage = 1
            } else {
                if (pin == firstPin) {
                    onPinConfirmed(pin)
                } else {
                    error = true
                    pin = ""
                    stage = 0
                    firstPin = ""
                }
            }
        } else {
            if (onPinEntry(pin)) {
                error = false
                onUnlocked()
            } else {
                error = true
            }
            pin = ""
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = DogonColors.Accent, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, color = DogonColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = if (error) Color(0xFFEF4444) else DogonColors.TextDim, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (i in 0 until 4) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (i < pin.length) DogonColors.Accent else DogonColors.Surface2)
                )
            }
        }
        Spacer(Modifier.height(32.dp))

        val keys = listOf("1","2","3","4","5","6","7","8","9","","0","⌫")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    row.forEach { key ->
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(if (key.isNotEmpty()) DogonColors.Surface else Color.Transparent)
                                .then(
                                    if (key.isNotEmpty()) Modifier.combinedClickable(onClick = {
                                        error = false
                                        when (key) {
                                            "⌫" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                            else -> if (pin.length < 4) {
                                                pin += key
                                                if (pin.length == 4) handleComplete()
                                            }
                                        }
                                    }, onLongClick = {}) else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(key, color = DogonColors.Text, fontSize = 20.sp)
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================================
// ARA (AI ile içerik bazlı arama)
// =====================================================================================

@Composable
fun SearchTab(items: List<MediaItem>) {
    val context = LocalContext.current
    var indexedCount by remember { mutableStateOf(0) }
    var totalToIndex by remember { mutableStateOf(0) }
    var indexingDone by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var tagMap by remember { mutableStateOf<Map<Long, List<String>>>(emptyMap()) }
    var knownTags by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(items) {
        withContext(Dispatchers.IO) {
            TagIndexer.indexMissing(context, items) { done, total ->
                indexedCount = done; totalToIndex = total
            }
        }
        tagMap = items.associate { it.id to TagIndexer.getTags(context, it.id) }
        knownTags = TagIndexer.getAllKnownTags(context)
        indexingDone = true
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("köpek, deniz, yemek...", color = DogonColors.TextFaint) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = DogonColors.TextDim) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DogonColors.Surface,
                unfocusedContainerColor = DogonColors.Surface,
                focusedBorderColor = DogonColors.Accent,
                unfocusedBorderColor = DogonColors.Line,
                focusedTextColor = DogonColors.Text,
                unfocusedTextColor = DogonColors.Text
            )
        )

        if (!indexingDone) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = DogonColors.Accent)
                Spacer(Modifier.height(16.dp))
                Text(
                    if (totalToIndex > 0) "Fotoğraflar taranıyor: $indexedCount / $totalToIndex" else "Fotoğraflar taranıyor...",
                    color = DogonColors.TextDim, fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Bu işlem sadece ilk seferde ve yeni fotoğraflar için yapılır",
                    color = DogonColors.TextFaint, fontSize = 11.sp, textAlign = TextAlign.Center
                )
            }
        } else {
            if (knownTags.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(knownTags.take(30)) { tag ->
                        val active = query.equals(tag, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (active) DogonColors.Accent else DogonColors.Surface)
                                .combinedClickable(onClick = { query = tag }, onLongClick = {})
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(tag, color = if (active) Color.White else DogonColors.TextDim, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            val results = remember(query, tagMap, items) {
                if (query.isBlank()) emptyList()
                else items.filter { item ->
                    tagMap[item.id]?.any { it.contains(query, ignoreCase = true) } == true
                }
            }

            when {
                query.isBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Aramak için bir kelime yaz ya da yukarıdaki etiketlerden seç",
                        color = DogonColors.TextDim, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
                results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Sonuç bulunamadı", color = DogonColors.TextDim)
                }
                else -> MediaGrid(results)
            }
        }
    }
}

// =====================================================================================
// FİLTRE / SIRALAMA (Tür, Sırala, Yön) — Zaman, Albümler, Klasörler ortak kullanır
// =====================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(current: FilterState, onApply: (FilterState) -> Unit, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(current.type) }
    var sortBy by remember { mutableStateOf(current.sortBy) }
    var ascending by remember { mutableStateOf(current.ascending) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DogonColors.Surface
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = 24.dp)) {
            Text("Filtrele", color = DogonColors.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(20.dp))

            FilterSection(
                title = "Tür",
                options = listOf(
                    "Tümü" to MediaTypeFilter.ALL,
                    "Fotoğraf" to MediaTypeFilter.PHOTO,
                    "Video" to MediaTypeFilter.VIDEO,
                    "GIF" to MediaTypeFilter.GIF
                ),
                selected = type,
                onSelect = { type = it }
            )
            Spacer(Modifier.height(20.dp))

            FilterSection(
                title = "Sırala",
                options = listOf(
                    "Tarih" to SortBy.DATE,
                    "Boyut" to SortBy.SIZE,
                    "Süre" to SortBy.DURATION,
                    "İsim" to SortBy.NAME
                ),
                selected = sortBy,
                onSelect = { sortBy = it }
            )
            Spacer(Modifier.height(20.dp))

            FilterSection(
                title = "Yön",
                options = listOf("Azalan" to false, "Artan" to true),
                selected = ascending,
                onSelect = { ascending = it }
            )
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { onApply(FilterState(type, sortBy, ascending)) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = DogonColors.Accent)
            ) {
                Text("Uygula")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> FilterSection(title: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Column {
        Text(title, color = DogonColors.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (label, value) ->
                val active = value == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (active) DogonColors.Accent else DogonColors.Surface2)
                        .combinedClickable(onClick = { onSelect(value) }, onLongClick = {})
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(label, color = if (active) Color.White else DogonColors.TextDim, fontSize = 12.sp)
                }
            }
        }
    }
}
