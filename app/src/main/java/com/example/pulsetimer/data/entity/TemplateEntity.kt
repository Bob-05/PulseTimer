package com.pulsetimer.data.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "'COLOR'")
    val backgroundType: String = "COLOR",
    @ColumnInfo(defaultValue = "''")
    val backgroundValue: String = "",
    val audioUri: String? = null,
    @ColumnInfo(defaultValue = "1")
    val vibrationPatternId: Int = 1
)