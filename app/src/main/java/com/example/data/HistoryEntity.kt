package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val originalUri: String,
    val editedUri: String,
    val fileName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val originalSize: Long,
    val editedSize: Long,
    val format: String,
    val width: Int,
    val height: Int,
    val operation: String
) : Serializable
