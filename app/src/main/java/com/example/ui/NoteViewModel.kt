package com.example.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.GeminiClient
import com.example.data.Attachment
import com.example.data.Note
import com.example.data.NoteRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

enum class Screen {
    LIST, VIEW, EDIT
}

class NoteViewModel(private val repository: NoteRepository) : ViewModel() {

    // Main flows
    private val _currentScreen = MutableStateFlow(Screen.LIST)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _currentNote = MutableStateFlow<Note?>(null)
    val currentNote: StateFlow<Note?> = _currentNote.asStateFlow()

    // Editor fields
    private val _editorTitle = MutableStateFlow("")
    val editorTitle: StateFlow<String> = _editorTitle.asStateFlow()

    private val _editorContent = MutableStateFlow("")
    val editorContent: StateFlow<String> = _editorContent.asStateFlow()

    private val _editorAttachments = MutableStateFlow<List<Attachment>>(emptyList())
    val editorAttachments: StateFlow<List<Attachment>> = _editorAttachments.asStateFlow()

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // AI states
    private val _aiResult = MutableStateFlow<String?>(null)
    val aiResult: StateFlow<String?> = _aiResult.asStateFlow()

    private val _aiResultTitle = MutableStateFlow("")
    val aiResultTitle: StateFlow<String> = _aiResultTitle.asStateFlow()

    private val _aiLoading = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading.asStateFlow()

    // Notes collected reactive flow, filtered by search query
    val notesList: StateFlow<List<Note>> = repository.allNotes
        .combine(_searchQuery) { notes, query ->
            if (query.isBlank()) {
                notes
            } else {
                notes.filter {
                    it.title.contains(query, ignoreCase = true) ||
                            it.content.contains(query, ignoreCase = true)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Navigation and Action Handlers
    fun navigateToList() {
        _currentScreen.value = Screen.LIST
        _currentNote.value = null
        clearEditor()
    }

    fun navigateToView(note: Note) {
        _currentNote.value = note
        _currentScreen.value = Screen.VIEW
    }

    fun navigateToCreate() {
        clearEditor()
        _currentNote.value = null
        _currentScreen.value = Screen.EDIT
    }

    fun navigateToEdit(note: Note) {
        _currentNote.value = note
        _editorTitle.value = note.title
        _editorContent.value = note.content
        _editorAttachments.value = deserializeAttachments(note.attachmentsString)
        _currentScreen.value = Screen.EDIT
    }

    private fun clearEditor() {
        _editorTitle.value = ""
        _editorContent.value = ""
        _editorAttachments.value = emptyList()
        _aiResult.value = null
        _aiResultTitle.value = ""
    }

    fun updateEditorTitle(title: String) {
        _editorTitle.value = title
    }

    fun updateEditorContent(content: String) {
        _editorContent.value = content
    }

    // Attachments Handling
    fun addAttachmentFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val attachment = getAttachmentFromUri(context, uri)
            val currentList = _editorAttachments.value.toMutableList()
            currentList.add(attachment)
            _editorAttachments.value = currentList
        }
    }

    fun removeAttachment(attachment: Attachment) {
        val currentList = _editorAttachments.value.toMutableList()
        currentList.remove(attachment)
        _editorAttachments.value = currentList
    }

    private fun getAttachmentFromUri(context: Context, uri: Uri): Attachment {
        var name = "مستند_مرفق"
        var mimeType = "application/octet-stream"

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    name = cursor.getString(nameIndex)
                }
            }
            val type = context.contentResolver.getType(uri)
            if (type != null) {
                mimeType = type
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Attachment(uri.toString(), name, mimeType)
    }

    private fun serializeAttachments(list: List<Attachment>): String {
        return list.joinToString("\n") { it.toSerializedString() }
    }

    private fun deserializeAttachments(str: String): List<Attachment> {
        if (str.isBlank()) return emptyList()
        return str.split("\n")
            .mapNotNull { Attachment.fromSerializedString(it) }
    }

    // Save, Update, Delete Notes
    fun saveNote() {
        viewModelScope.launch {
            val title = _editorTitle.value.trim().ifBlank { 
                "ملاحظة ذكية " + System.currentTimeMillis().toString().takeLast(4)
            }
            val content = _editorContent.value
            val attachmentsStr = serializeAttachments(_editorAttachments.value)
            
            val noteToSave = _currentNote.value?.copy(
                title = title,
                content = content,
                attachmentsString = attachmentsStr,
                updatedAt = System.currentTimeMillis()
            ) ?: Note(
                title = title,
                content = content,
                attachmentsString = attachmentsStr
            )

            val savedId = repository.insertNote(noteToSave)
            
            // Navigate back appropriately
            _currentScreen.value = Screen.LIST
            _currentNote.value = null
            clearEditor()
        }
    }

    fun deleteNoteById(id: Int) {
        viewModelScope.launch {
            repository.deleteNoteById(id)
            navigateToList()
        }
    }

    // AI Features Core API Integration
    fun runAIFeature(context: Context, feature: GeminiClient.AIFeature, noteContent: String) {
        if (noteContent.isBlank()) {
            _aiResultTitle.value = "تنبيه"
            _aiResult.value = "يرجى كتابة بعض السطور في الملاحظة أولاً لكي يستطيع الذكاء الاصطناعي معالجتها وفهم ثناياها."
            return
        }

        viewModelScope.launch {
            _aiLoading.value = true
            _aiResultTitle.value = feature.titleArabic
            try {
                val response = GeminiClient.callGemini(context, feature, noteContent)
                _aiResult.value = response
            } catch (e: Exception) {
                _aiResult.value = "حدث خطأ غير متوقع أثناء المعالجة: ${e.localizedMessage}"
            } finally {
                _aiLoading.value = false
            }
        }
    }

    // Ask custom AI Question (100% interactive Q&A feature)
    fun askCustomQuestion(context: Context, noteContent: String, question: String) {
        if (noteContent.isBlank()) {
            _aiResultTitle.value = "سؤال ذكي"
            _aiResult.value = "الملاحظة فارغة! يرجى ملؤها ببعض البيانات ثم اسألني ما تريد."
            return
        }
        if (question.isBlank()) return

        viewModelScope.launch {
            _aiLoading.value = true
            _aiResultTitle.value = "رد الذكاء الاصطناعي على سؤالك"
            try {
                // Synthesize custom template
                val customFeature = GeminiClient.AIFeature.EXPLAIN_CONCEPTS
                val combinedPrompt = "بناءً على نص الملاحظة التالي تفضل بالإجابة الإحترافية على هذا السؤال: \n«$question»\n\nنص الملاحظ الموجه للتحليل والبحث:\n$noteContent"
                
                // If offline, simulate keyword match
                val response = if (GeminiClient.isOnline(context)) {
                    val apiKey = BuildConfig.GEMINI_API_KEY
                    if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                        simulateLocalSearch(noteContent, question) + "\n\n(💡 تم البحث محلياً لعدم إعداد مفتاح API الخاص بـ Gemini)"
                    } else {
                        GeminiClient.callGemini(context, customFeature, combinedPrompt)
                    }
                } else {
                    simulateLocalSearch(noteContent, question)
                }
                
                _aiResult.value = response
            } catch (e: Exception) {
                _aiResult.value = "فشل المعالجة المحلية للأسئلة: ${e.localizedMessage}"
            } finally {
                _aiLoading.value = false
            }
        }
    }

    private fun simulateLocalSearch(noteText: String, question: String): String {
        val normalizedQuestion = question.replace(Regex("[.,;:\"'?؟!«»]"), "").lowercase(Locale.ROOT)
        val queryWords = normalizedQuestion.split(" ").filter { it.length > 3 }
        
        val sentences = noteText.split(Regex("[.\n?!]")).filter { it.isNotBlank() }
        val matchedSentences = mutableListOf<String>()

        sentences.forEach { sentence ->
            val matched = queryWords.any { sentence.lowercase(Locale.ROOT).contains(it) }
            if (matched) {
                matchedSentences.add("• " + sentence.trim())
            }
        }

        return if (matchedSentences.isNotEmpty()) {
            "⚡ [مساعد البحث المحلي - العمل بدون إنترنت]\n\n" +
                    "وجدتُ هذه الجمل والسطور الأكثر ملاءمة لسؤالك داخل الملاحظة:\n\n" +
                    matchedSentences.distinct().joinToString("\n\n") +
                    "\n\n⚙️ ننصح بالاتصال بالإنترنت لتشغيل الموديل الكامل والحصول على رد صياغي معقد."
        } else {
            "⚡ [مساعد المحتوى المحلي - العمل بدون إنترنت]\n\n" +
                    "لم أعثر على مطابقة مباشرة في مذكرتك للمصطلحات: (${queryWords.joinToString(", ")}).\n\n" +
                    "إليك الجزء الأول من فكرتك للمساعدة:\n" +
                    "«" + noteText.take(150) + "...»"
        }
    }

    fun clearAiResult() {
        _aiResult.value = null
        _aiResultTitle.value = ""
    }
}

class NoteViewModelFactory(private val repository: NoteRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NoteViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NoteViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
