package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val attachmentsString: String = "", // Format: uri|name|mimeType, separated by '\n' or ','
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val category: String = "عام",
    val color: Int = 0
)

data class Attachment(
    val uri: String,
    val name: String,
    val mimeType: String
) {
    fun toSerializedString(): String {
        return "$uri|||$name|||$mimeType"
    }

    companion object {
        fun fromSerializedString(str: String): Attachment? {
            val parts = str.split("|||")
            return if (parts.size >= 3) {
                Attachment(parts[0], parts[1], parts[2])
            } else null
        }
    }
}
