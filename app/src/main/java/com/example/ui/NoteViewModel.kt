package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiClient
import com.example.data.Attachment
import com.example.data.Note
import com.example.data.NoteRepository
import com.example.data.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteViewModel(
    private val repository: NoteRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    val allNotes: StateFlow<List<Note>> = repository.allNotes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedNote = MutableStateFlow<Note?>(null)
    val selectedNote = _selectedNote.asStateFlow()

    // Editor states
    val editorTitle = MutableStateFlow("")
    val editorContent = MutableStateFlow("")
    val editorAttachments = MutableStateFlow<List<Attachment>>(emptyList())
    val editorIsPinned = MutableStateFlow(false)
    val editorCategory = MutableStateFlow("عام")
    val editorColor = MutableStateFlow(0)

    // AI states
    private val _aiResponseText = MutableStateFlow<String?>(null)
    val aiResponseText = _aiResponseText.asStateFlow()

    private val _aiLoading = MutableStateFlow(false)
    val aiLoading = _aiLoading.asStateFlow()

    // Chat / Q&A States
    private val _chatMessages = MutableStateFlow<List<Pair<String, Boolean>>>(emptyList())
    val chatMessages = _chatMessages.asStateFlow()

    private val _chatLoading = MutableStateFlow(false)
    val chatLoading = _chatLoading.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectNote(note: Note?) {
        _selectedNote.value = note
        note?.let {
            editorTitle.value = it.title
            editorContent.value = it.content
            editorAttachments.value = it.attachments
            editorIsPinned.value = it.isPinned
            editorCategory.value = it.category
            editorColor.value = it.color
            _chatMessages.value = emptyList()
            _aiResponseText.value = null
        }
    }

    fun resetEditor() {
        editorTitle.value = ""
        editorContent.value = ""
        editorAttachments.value = emptyList()
        editorIsPinned.value = false
        editorCategory.value = "عام"
        editorColor.value = 0
        _aiResponseText.value = null
        _chatMessages.value = emptyList()
    }

    fun updateEditorColor(colorIndex: Int) {
        editorColor.value = colorIndex
    }

    fun updateEditorCategory(categoryName: String) {
        editorCategory.value = categoryName
    }

    fun toggleEditorPinned() {
        editorIsPinned.value = !editorIsPinned.value
    }

    fun addAttachment(attachment: Attachment) {
        editorAttachments.value = editorAttachments.value + attachment
    }

    fun removeAttachment(attachment: Attachment) {
        editorAttachments.value = editorAttachments.value.filter { it.uriString != attachment.uriString }
    }

    fun saveNote(onComplete: () -> Unit) {
        viewModelScope.launch {
            val title = editorTitle.value.trim().ifBlank { "ملاحظة بدون عنوان" }
            val content = editorContent.value
            val current = _selectedNote.value

            if (current == null) {
                val newNote = Note(
                    title = title,
                    content = content,
                    isPinned = editorIsPinned.value,
                    category = editorCategory.value,
                    color = editorColor.value,
                    attachments = editorAttachments.value
                )
                repository.insert(newNote)
            } else {
                val updatedNote = current.copy(
                    title = title,
                    content = content,
                    dateModified = System.currentTimeMillis(),
                    isPinned = editorIsPinned.value,
                    category = editorCategory.value,
                    color = editorColor.value,
                    attachments = editorAttachments.value
                )
                repository.update(updatedNote)
            }
            resetEditor()
            onComplete()
        }
    }

    fun deleteSelectedNote(onComplete: () -> Unit) {
        viewModelScope.launch {
            val current = _selectedNote.value
            if (current != null) {
                repository.delete(current)
                _selectedNote.value = null
                resetEditor()
            }
            onComplete()
        }
    }

    fun runAIFeature(context: Context, feature: GeminiClient.AIFeature, text: String) {
        if (text.isBlank()) {
            Toast.makeText(context, "الملاحظة فارغة! يرجى إضافة نص لمعالجته.", Toast.LENGTH_SHORT).show()
            return
        }
        val customKey = settingsManager.getCustomApiKey()
        viewModelScope.launch {
            _aiLoading.value = true
            _aiResponseText.value = "جاري تفعيل ذكاء المساعد لمعالجة الملاحظة..."
            val result = GeminiClient.runFeature(customKey, feature, text)
            _aiResponseText.value = result
            _aiLoading.value = false

            if (feature == GeminiClient.AIFeature.AUTO_CATEGORIZE) {
                val cleanedCat = result.trim().replace(".", "").replace("\"", "")
                if (cleanedCat.length < 20 && cleanedCat.isNotBlank()) {
                    editorCategory.value = cleanedCat
                }
            }
        }
    }

    fun askCustomQuestion(context: Context, contextContent: String, question: String) {
        if (question.isBlank()) return
        
        _chatMessages.value = _chatMessages.value + Pair(question, true)
        
        val customKey = settingsManager.getCustomApiKey()
        viewModelScope.launch {
            _chatLoading.value = true
            val reply = GeminiClient.askCustomQuestion(customKey, contextContent, question)
            _chatMessages.value = _chatMessages.value + Pair(reply, false)
            _chatLoading.value = false
        }
    }

    fun clearAiResponse() {
        _aiResponseText.value = null
    }

    fun clearChat() {
        _chatMessages.value = emptyList()
    }
}

class NoteViewModelFactory(
    private val repository: NoteRepository,
    private val settingsManager: SettingsManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NoteViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NoteViewModel(repository, settingsManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
