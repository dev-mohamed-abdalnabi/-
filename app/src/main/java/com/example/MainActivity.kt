package com.example

import android.content.Context
import com.example.ui.theme.MyApplicationTheme
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.api.GeminiClient
import com.example.data.*
import com.example.ui.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup local database and repository
        val database = NoteDatabase.getDatabase(applicationContext)
        val repository = NoteRepository(database.noteDao())
        val viewModelFactory = NoteViewModelFactory(repository)
        val viewModel = ViewModelProvider(this, viewModelFactory)[NoteViewModel::class.java]

        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val settingsManager = remember { com.example.data.SettingsManager(context) }
            var themeMode by remember { mutableStateOf(settingsManager.themeMode) }

            MyApplicationTheme(themeMode = themeMode) {
                // Ensure layout fits RTL (Arabic standard right-to-left alignment)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainAppContent(
                            viewModel = viewModel,
                            themeMode = themeMode,
                            onThemeChange = { newMode ->
                                settingsManager.themeMode = newMode
                                themeMode = newMode
                            },
                            settingsManager = settingsManager
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    viewModel: NoteViewModel,
    themeMode: Int,
    onThemeChange: (Int) -> Unit,
    settingsManager: com.example.data.SettingsManager
) {
    val context = LocalContext.current
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val notesList by viewModel.notesList.collectAsStateWithLifecycle()
    val currentNote by viewModel.currentNote.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    // Editor bindings
    val editorTitle by viewModel.editorTitle.collectAsStateWithLifecycle()
    val editorContent by viewModel.editorContent.collectAsStateWithLifecycle()
    val editorAttachments by viewModel.editorAttachments.collectAsStateWithLifecycle()

    // AI States
    val aiResult by viewModel.aiResult.collectAsStateWithLifecycle()
    val aiResultTitle by viewModel.aiResultTitle.collectAsStateWithLifecycle()
    val aiLoading by viewModel.aiLoading.collectAsStateWithLifecycle()

    // State-driven screen handling to prevent Navigation glitches
    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            slideInHorizontally { width -> if (targetState > initialState) width else -width } togetherWith
                    slideOutHorizontally { width -> if (targetState > initialState) -width else width }
        },
        label = "ScreenTransition"
    ) { screen ->
        when (screen) {
            Screen.LIST -> {
                NotesListScreen(
                    viewModel = viewModel,
                    notesList = notesList,
                    searchQuery = searchQuery,
                    themeMode = themeMode,
                    onThemeChange = onThemeChange,
                    settingsManager = settingsManager,
                    onNoteClick = { viewModel.navigateToView(it) },
                    onCreateClick = { viewModel.navigateToCreate() }
                )
            }
            Screen.VIEW -> {
                currentNote?.let { note ->
                    NoteViewerScreen(
                        viewModel = viewModel,
                        note = note,
                        settingsManager = settingsManager,
                        onBack = { viewModel.navigateToList() },
                        onEdit = { viewModel.navigateToEdit(note) }
                    )
                } ?: viewModel.navigateToList()
            }
            Screen.EDIT -> {
                NoteEditorScreen(
                    viewModel = viewModel,
                    title = editorTitle,
                    content = editorContent,
                    attachments = editorAttachments,
                    onBack = { viewModel.navigateToList() },
                    onSave = { viewModel.saveNote() }
                )
            }
        }
    }

    // AI Results Dialog Card
    if (aiResult != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearAiResult() },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = "AI Action",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = aiResultTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .heightIn(max = 350.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    NoteContentViewer(content = aiResult ?: "")
                }
            },
            confirmButton = {
                val clipboardManager = LocalClipboardManager.current
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(aiResult ?: ""))
                            Toast.makeText(context, "تم نسخ النتيجة بنجاح في الحافظة!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "نسخ", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("نسخ النص")
                    }
                    TextButton(onClick = { viewModel.clearAiResult() }) {
                        Text("إغلاق")
                    }
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Modern Overlay Full Screen AI Loading Screen
    if (aiLoading) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.5f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "جاري التحليل والمعالجة الذكية...",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        val isOnlineStatus = GeminiClient.isOnline(context)
                        Text(
                            text = if (isOnlineStatus) "يتم الآن استخدام نموذج Gemini 3.5 لنتائج فائقة الدقة." else "تم كشف عدم الاتصال بالشبكة. يتم معالجة طلبك محلياً بشكل فوري!",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOnlineStatus) MaterialTheme.colorScheme.primary else Color(0xFFF59E0B),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 1. NOTES LIST SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    viewModel: NoteViewModel,
    notesList: List<Note>,
    searchQuery: String,
    themeMode: Int,
    onThemeChange: (Int) -> Unit,
    settingsManager: com.example.data.SettingsManager,
    onNoteClick: (Note) -> Unit,
    onCreateClick: () -> Unit
) {
    val context = LocalContext.current
    var showAiHubSheet by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Categories filter state (Defaults to "الكل" - All)
    var selectedCategoryFilter by remember { mutableStateOf("الكل") }

    // Reactively compute sorted and filtered list of notes
    val sortedAndFilteredNotes = remember(notesList, selectedCategoryFilter, settingsManager.sortOrder) {
        val filtered = if (selectedCategoryFilter == "الكل") {
            notesList
        } else {
            notesList.filter { it.category == selectedCategoryFilter }
        }

        filtered.sortedWith { n1, n2 ->
            // First: pins stay on top
            if (n1.isPinned && !n2.isPinned) return@sortedWith -1
            if (!n1.isPinned && n2.isPinned) return@sortedWith 1

            // Second: Sort according to user preferences
            when (settingsManager.sortOrder) {
                "created_at" -> n2.createdAt.compareTo(n1.createdAt) // Creation date descending
                "title" -> n1.title.compareTo(n2.title, ignoreCase = true) // Title ascending
                else -> n2.updatedAt.compareTo(n1.updatedAt) // Update date descending (default)
            }
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "الملاحظات الذكية",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "مفكرة ذكية مدعومة بميزات الذكاء الاصطناعي",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "ترس الإعدادات والتخصيص",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showAiHubSheet = true }) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = "مركز الذكاء الاصطناعي",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateClick,
                icon = { Icon(Icons.Filled.Add, contentDescription = "أضف") },
                text = { Text("ملاحظة جديدة") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Elegant search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("بحث في الملاحظات والسطور والأكواد...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "بحث") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "مسح")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                singleLine = true
            )

            // Categories Horizontal Filter Row
            val categoriesList = listOf("الكل", "عام", "عمل", "دراسة", "شخصي", "أفكار")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                items(categoriesList) { cat ->
                    val isSelected = selectedCategoryFilter == cat
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategoryFilter = cat },
                        label = { Text(cat) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            // Dynamic internet status indicator HUD banner
            val isOnline = GeminiClient.isOnline(context)
            if (!isOnline) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.CloudOff, contentDescription = "مفصول", tint = Color(0xFFD97706))
                        Text(
                            text = "أنت في وضع عدم الاتصال: ستعمل بعض الميزات محلياً.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF92400E)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (sortedAndFilteredNotes.isEmpty()) {
                // Polished illustrative empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MenuBook,
                            contentDescription = "دفتر فارغ",
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "لا توجد تطابقات لنتائج البحث" else "مفكرتك الذكية فارغة تماماً",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "استخدم كلمات مختلفة في البحث أو غير المجموعة المفلترة." else "ابدأ الآن بكتابة أفكارك وملاحظاتك لترتيبها باستخدام الذكاء الاصطناعي.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (searchQuery.isEmpty() && selectedCategoryFilter == "الكل") {
                            Button(onClick = onCreateClick) {
                                Icon(Icons.Filled.Create, contentDescription = "أول ملاحظة")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("كتابة ملاحظة جديدة")
                            }
                        }
                    }
                }
            } else {
                // Responsive adaptive Grid standard layout
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(sortedAndFilteredNotes, key = { it.id }) { note ->
                        NoteCard(note = note, onClick = { onNoteClick(note) })
                    }
                }
            }
        }
    }

    // Complete App Settings Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "الإعدادات",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "الإعدادات والتخصيص",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 1. Appearance Section
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "🎨 العرض والتخصيص",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                // Theme Option Selector
                                Text("مظهر التطبيق الحالي:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp))
                                val themeOptions = listOf("الوضع الفاتح ☀️", "الوضع الداكن 🌙", "وضع أموليد AMOLED 🖤", "اتباع النظام 📱")
                                themeOptions.forEachIndexed { index, op ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onThemeChange(index) }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        RadioButton(
                                            selected = (themeMode == index),
                                            onClick = { onThemeChange(index) }
                                        )
                                        Text(text = op, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }

                                Divider(modifier = Modifier.padding(vertical = 12.dp))

                                // Font Size settings
                                Text("حجم خط الملاحظة داخل التفاصيل:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp))
                                val fontSizeOptions = listOf("صغير جداً", "متوسط مناسب", "كبير وواضح", "كبير جداً")
                                val fontScaleValues = listOf(0.8f, 1.0f, 1.2f, 1.4f)
                                var tempFontSize by remember { mutableStateOf(settingsManager.fontSize) }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    fontSizeOptions.forEachIndexed { fIdx, fOp ->
                                        val scale = fontScaleValues[fIdx]
                                        val isSelectedF = tempFontSize == scale
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isSelectedF) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surfaceVariant
                                                )
                                                .clickable {
                                                    settingsManager.fontSize = scale
                                                    tempFontSize = scale
                                                }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = fOp,
                                                color = if (isSelectedF) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. AI Key Settings Section
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "🤖 إعدادات الذكاء الاصطناعي (Gemini)",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Text(
                                    text = "إذا واجهت بطئاً أو تعليقاً في الذكاء الاصطناعي عبر الشبكة، يمكنك إدخال مفتاح Gemini API الخاص بك لتشغيله بشكل فوري وحصري:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                var keyText by remember { mutableStateOf(settingsManager.geminiApiKey) }
                                OutlinedTextField(
                                    value = keyText,
                                    onValueChange = {
                                        keyText = it
                                        settingsManager.geminiApiKey = it
                                    },
                                    placeholder = { Text("أدخل مفتاح AI الخاص بك هنا...") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                    // 3. User Preferences Section
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "⚙️ تفضيلات المفكرة",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                 // Auto-save toggle
                                var autoSaveEnabled by remember { mutableStateOf(settingsManager.isAutoSaveEnabled) }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            autoSaveEnabled = !autoSaveEnabled
                                            settingsManager.isAutoSaveEnabled = autoSaveEnabled
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text("الحفظ التلقائي للمسودات والملاحظات", style = MaterialTheme.typography.bodyMedium)
                                    Switch(
                                        checked = autoSaveEnabled,
                                        onCheckedChange = {
                                            autoSaveEnabled = it
                                            settingsManager.isAutoSaveEnabled = it
                                        }
                                    )
                                }

                                Divider(modifier = Modifier.padding(vertical = 12.dp))

                                // Sorting Preferences
                                Text("ترتيب الملاحظات حسب المفضل:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp))
                                val sortKeys = listOf("updated_at", "created_at", "title")
                                val sortLabels = listOf("الأحدث تعديلاً (افتراضي)", "الأحدث إنشاءً وتدويناً", "الأبجدية (اسم العنوان)")
                                var tempSort by remember { mutableStateOf(settingsManager.sortOrder) }
                                sortKeys.forEachIndexed { sIdx, sKey ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                settingsManager.sortOrder = sKey
                                                tempSort = sKey
                                            }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        RadioButton(
                                            selected = (tempSort == sKey),
                                            onClick = {
                                                settingsManager.sortOrder = sKey
                                                tempSort = sKey
                                            }
                                        )
                                        Text(text = sortLabels[sIdx], style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }

                    // 4. About Developer Section
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "🌐 بطل التطبيق ومطوره",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Text(
                                    text = "اسم التطبيق الرسمي: Next",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "تم التطوير بواسطة flow studio",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 4.dp)
                                )

                                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .clickable {
                                            try {
                                                uriHandler.openUri("https://flow-com.vercel.app/")
                                            } catch (e: Exception) {
                                                // ignore
                                            }
                                        }
                                ) {
                                    Icon(Icons.Filled.Link, contentDescription = "رابط", tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = "https://flow-com.vercel.app/",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showSettingsDialog = false }) {
                    Text("تم وحفظ الإعدادات")
                }
            }
        )
    }

    // AI Hub Drawer Panel list
    if (showAiHubSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAiHubSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "ميزات الذكاء الاصطناعي",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showAiHubSheet = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "إغلاق")
                    }
                }
                Text(
                    text = "عند مطالعة أي ملاحظة، يمكنك استدعاء الميزات التالية لمعالجة النصوص:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Divider(modifier = Modifier.padding(vertical = 4.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 350.dp)
                ) {
                    items(GeminiClient.AIFeature.values()) { feat ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = getIconForName(feat.iconName),
                                        contentDescription = feat.titleArabic,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = feat.titleArabic,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = feat.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                Button(
                    onClick = { showAiHubSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("فهمت الأمر، دعنا نبدأ")
                }
            }
        }
    }
}

// Elegant individual note card representation
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NoteCard(note: Note, onClick: () -> Unit) {
    val attachments = remember(note.attachmentsString) {
        if (note.attachmentsString.isBlank()) emptyList()
        else note.attachmentsString.split("\n").mapNotNull { Attachment.fromSerializedString(it) }
    }
    val previewImage = remember(attachments) {
        attachments.firstOrNull { it.mimeType.startsWith("image/") }
    }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp)
    ) {
        Column {
            // If card contains image attachment, display it beautifully at the top
            if (previewImage != null) {
                AsyncImage(
                    model = previewImage.uri,
                    contentDescription = "معاينة",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                )
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = note.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (note.isPinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "مُثبّتة",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                
                // Strip HTML formatting on card snippet for a cleaner look
                val plainContent = remember(note.content) {
                    note.content.replace(Regex("<[^>]*>"), " ").trim()
                }
                Text(
                    text = plainContent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dateFormatted = remember(note.updatedAt) {
                        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale("ar"))
                        sdf.format(Date(note.updatedAt))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = dateFormatted,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        // Mini category badge capsule
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = note.category.ifBlank { "عام" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Attachments indicators
                    if (attachments.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (attachments.any { it.mimeType.startsWith("image/") }) Icons.Filled.Image else Icons.Filled.AttachFile,
                                contentDescription = "مرفق",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = attachments.size.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 2. NOTE VIEWER SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteViewerScreen(
    viewModel: NoteViewModel,
    note: Note,
    settingsManager: com.example.data.SettingsManager,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    val context = LocalContext.current
    var customQuestionText by remember { mutableStateOf("") }
    var chatExpanded by remember { mutableStateOf(false) }

    val attachments = remember(note.attachmentsString) {
        if (note.attachmentsString.isBlank()) emptyList()
        else note.attachmentsString.split("\n").mapNotNull { Attachment.fromSerializedString(it) }
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = note.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "الرجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "حذف الملاحظة", tint = MaterialTheme.colorScheme.error)
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "تعديل الملاحظة")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Attachments List in Details
                if (attachments.isNotEmpty()) {
                    item {
                        Text(
                            text = "المرفقات والملفات المضافة (${attachments.size}):",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(attachments) { item ->
                                AttachmentDetailChip(context = context, attachment = item)
                            }
                        }
                    }
                }

                // Note Content with Markdown parsing
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Render Content using custom parsed code block system
                            NoteContentViewer(content = note.content, fontSizeScale = settingsManager.fontSize)
                        }
                    }
                }

                // Scrollable AI Pills tray with all 15 Features
                item {
                    Text(
                        text = "أدوات الذكاء الاصطناعي:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(GeminiClient.AIFeature.values()) { feat ->
                            InputChip(
                                selected = false,
                                onClick = { viewModel.runAIFeature(context, feat, note.content) },
                                label = { Text(feat.titleArabic) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = getIconForName(feat.iconName),
                                        contentDescription = feat.titleArabic,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = InputChipDefaults.inputChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    labelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }

                // Interactive Q&A chat terminal with transition animations
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { chatExpanded = !chatExpanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.QuestionAnswer,
                                        contentDescription = "اسأل",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = "ميزة الدردشة والامتحانات: اسأل ملاحظتك!",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                Icon(
                                    imageVector = if (chatExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = "توسيع",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }

                            if (chatExpanded) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "اكتب أي سؤال حول محتوى هذه الملاحظة وسيجيب المساعد الذكي عن تفاصيلها:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = customQuestionText,
                                    onValueChange = { customQuestionText = it },
                                    placeholder = { Text("مثال: ما خلاصة هذا الموضوع؟ أو ما هي بطاقاتي؟") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                if (customQuestionText.isNotBlank()) {
                                                    viewModel.askCustomQuestion(context, note.content, customQuestionText)
                                                    customQuestionText = ""
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Filled.Send, contentDescription = "أرسل", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation alert dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف الملاحظة") },
            text = { Text("هل أنت متأكد من حذف هذه الملاحظة نهائياً؟ لا يمكن التراجع عن الحذف.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteNoteById(note.id)
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف نهائي")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

// Attachment details design inside details
@Composable
fun AttachmentDetailChip(context: Context, attachment: Attachment) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .width(180.dp)
            .height(64.dp)
            .clickable {
                try {
                    // Start quick viewing action if possible or generic toast
                    Toast.makeText(context, "تم تحديد الملف: " + attachment.name, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (attachment.mimeType.startsWith("image/")) {
                AsyncImage(
                    model = attachment.uri,
                    contentDescription = attachment.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Article,
                        contentDescription = "مستند",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = attachment.mimeType.substringAfter("/").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

// ==========================================
// 3. NOTE EDITOR SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    viewModel: NoteViewModel,
    title: String,
    content: String,
    attachments: List<Attachment>,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val editorIsPinned by viewModel.editorIsPinned.collectAsStateWithLifecycle()
    val editorCategory by viewModel.editorCategory.collectAsStateWithLifecycle()

    // Launch Document/File Pickers
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.addAttachmentFromUri(context, it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تحرير الملاحظة") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "الرجوع")
                    }
                },
                actions = {
                    Button(
                        onClick = onSave,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("حفظ")
                    }
                }
            )
        },
        floatingActionButton = {
            // Creative quick-formatting and AI Rephrase tool tray inside editor
            FloatingActionButton(
                onClick = {
                    viewModel.runAIFeature(
                        context,
                        GeminiClient.AIFeature.GRAMMAR,
                        content
                    )
                },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = CircleShape
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp)) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = "تعديل الصياغة")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تدقيق هجائي ذكي")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Outlined Custom Title field
            OutlinedTextField(
                value = title,
                onValueChange = { viewModel.updateEditorTitle(it) },
                label = { Text("عنوان الملاحظة") },
                placeholder = { Text("اكتب عنوان الملاحظة هنا...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                maxLines = 1,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )

            // Pin & Category Selector Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Pin toggle chip
                FilterChip(
                    selected = editorIsPinned,
                    onClick = { viewModel.toggleEditorPinned() },
                    label = { Text(if (editorIsPinned) "مُثبّتة 📌" else "تثبيت الملاحظة") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Text label info
                Text("المجموعة:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                // Dropdown Category Selector
                val expanded = remember { mutableStateOf(false) }
                Box {
                    Button(
                        onClick = { expanded.value = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(editorCategory, style = MaterialTheme.typography.bodyMedium)
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    
                    DropdownMenu(
                        expanded = expanded.value,
                        onDismissRequest = { expanded.value = false }
                    ) {
                        val editorCategories = listOf("عام", "عمل", "دراسة", "شخصي", "أفكار")
                        editorCategories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    viewModel.updateEditorCategory(cat)
                                    expanded.value = false
                                }
                            )
                        }
                    }
                }
            }

            // Writing and media utilities toolbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Attachments launcher clickable
                OutlinedButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = "إرفاق")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إرفاق صور أو ملفات")
                }

                // Word counter indicators
                val wordCount = remember(content) {
                    content.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "عدد الكلمات: $wordCount",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Horizontal strip of temporary editor attachments
            if (attachments.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(attachments) { item ->
                        AttachmentEditorCard(attachment = item, onRemove = { viewModel.removeAttachment(item) })
                    }
                }
            }

            // Expanded Writing Editor supporting extreme lines
            OutlinedTextField(
                value = content,
                onValueChange = { viewModel.updateEditorContent(it) },
                label = { Text("المحتوى") },
                placeholder = { Text("اكتب محتوى الملاحظة هنا...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

// Temporary file attachment chip inside Editor with Delete badge (X)
@Composable
fun AttachmentEditorCard(attachment: Attachment, onRemove: () -> Unit) {
    Box(modifier = Modifier.padding(end = 4.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .width(100.dp)
                .height(100.dp)
        ) {
            Column(
                modifier = Modifier.padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                if (attachment.mimeType.startsWith("image/")) {
                    AsyncImage(
                        model = attachment.uri,
                        contentDescription = attachment.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Article,
                            contentDescription = "مستند",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = attachment.name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // Small floating delete icon badge
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .size(24.dp)
                .background(Color.Red, CircleShape)
                .align(Alignment.TopEnd)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "إزالة", tint = Color.White, modifier = Modifier.size(12.dp))
        }
    }
}

// ==========================================
// 4. RICH SYNTAX-HIGHLIGHTED CONTENT VIEWER
// ==========================================
@Composable
fun NoteContentViewer(content: String, fontSizeScale: Float = 1.0f) {
    val parts = remember(content) { parseContentWithCodeBlocks(content) }
    val textSize = (15 * fontSizeScale).sp
    val itemLineHeight = (24 * fontSizeScale).sp

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        parts.forEach { part ->
            if (part.isCode) {
                CodeBlockDisplay(code = part.text, language = part.language, fontSizeScale = fontSizeScale)
            } else {
                Text(
                    text = part.text,
                    fontSize = textSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = itemLineHeight
                )
            }
        }
    }
}

data class ContentPart(val text: String, val isCode: Boolean, val language: String = "")

// Splits content safely based on triple-backtick delimiters
fun parseContentWithCodeBlocks(text: String): List<ContentPart> {
    if (text.isBlank()) return emptyList()
    
    val parts = mutableListOf<ContentPart>()
    val codeBlockRegex = Regex("(?s)```(\\w*)\\n(.*?)```")
    var lastIndex = 0

    codeBlockRegex.findAll(text).forEach { match ->
        val textBefore = text.substring(lastIndex, match.range.first)
        if (textBefore.isNotBlank()) {
            parts.add(ContentPart(textBefore, isCode = false))
        }
        val language = match.groupValues[1]
        val codeText = match.groupValues[2]
        parts.add(ContentPart(codeText, isCode = true, language = language))
        lastIndex = match.range.last + 1
    }

    if (lastIndex < text.length) {
        val remainingText = text.substring(lastIndex)
        if (remainingText.isNotBlank()) {
            parts.add(ContentPart(remainingText, isCode = false))
        }
    }

    if (parts.isEmpty()) {
        parts.add(ContentPart(text, isCode = false))
    }

    return parts
}

// Syntax-Highlighted rounded Code box
@Composable
fun CodeBlockDisplay(code: String, language: String, fontSizeScale: Float = 1.0f) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isDark = isSystemInDarkTheme()
    val codeTextSize = (14 * fontSizeScale).sp
    val codeLineHeight = (21 * fontSizeScale).sp

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF1E1E2E) else Color(0xFF282A36)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" }.uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF79C6),
                    style = MaterialTheme.typography.labelSmall
                )
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(code))
                        Toast.makeText(context, "تم كود النسخ للحافظة!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "نسخ الكود",
                        tint = Color(0xFFF8F8F2),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Text layout with Mononspaced display
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFF8F8F2),
                fontSize = codeTextSize,
                lineHeight = codeLineHeight,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .fillMaxWidth()
            )
        }
    }
}

// Helper mapper connecting custom icons strictly
fun getIconForName(name: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (name) {
        "compress" -> Icons.Filled.Compress
        "school" -> Icons.Filled.School
        "tips_and_updates" -> Icons.Filled.TipsAndUpdates
        "playlist_add_check" -> Icons.Filled.PlaylistAddCheck
        "title" -> Icons.Filled.Title
        "business_center" -> Icons.Filled.BusinessCenter
        "spellcheck" -> Icons.Filled.Spellcheck
        "zoom_in" -> Icons.Filled.ZoomIn
        "g_translate" -> Icons.Filled.Translate
        "quiz" -> Icons.Filled.Quiz
        "account_tree" -> Icons.Filled.AccountTree
        "local_offer" -> Icons.Filled.LocalOffer
        "child_care" -> Icons.Filled.ChildCare
        "shortcut" -> Icons.Filled.Shortcut
        "code" -> Icons.Filled.Code
        else -> Icons.Filled.Bolt
    }
}
