package com.pulsetimer.data.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "intervals",
    foreignKeys = [
        ForeignKey(
            entity = TemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["templateId"])]
)
data class IntervalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val templateId: Long,
    val name: String,
    val durationSeconds: Int,
    val colorHex: String,
    val orderIndex: Int,
    @ColumnInfo(defaultValue = "'⏱️'")
    val iconEmoji: String = "⏱️",
    @ColumnInfo(defaultValue = "'COLOR'")
    val backgroundType: String = "COLOR",
    @ColumnInfo(defaultValue = "''")
    val backgroundValue: String = "",
    val audioUri: String? = null,
    @ColumnInfo(defaultValue = "1")
    val vibrationPatternId: Int = 1
)