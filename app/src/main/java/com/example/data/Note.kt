package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Attachment(
    val uriString: String,
    val name: String,
    val mimeType: String,
    val size: Long = 0L
)

@Entity(tableName = "notes")
@Serializable
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val dateModified: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val category: String = "عام",
    val color: Int = 0, // Index: 0 = Default, 1 = Red, 2 = Green, 3 = Blue, 4 = Yellow, 5 = Purple
    val attachments: List<Attachment> = emptyList()
)

class Converters {
    @TypeConverter
    fun fromAttachmentList(value: List<Attachment>?): String {
        if (value == null) return "[]"
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toAttachmentList(value: String): List<Attachment> {
        return try {
            Json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
