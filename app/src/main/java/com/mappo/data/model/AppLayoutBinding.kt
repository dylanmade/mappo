package com.mappo.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "app_layout_bindings",
    primaryKeys = ["packageName"],
    foreignKeys = [
        ForeignKey(
            entity = Layout::class,
            parentColumns = ["id"],
            childColumns = ["layoutId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("layoutId")]
)
data class AppLayoutBinding(
    val packageName: String,
    val layoutId: Long
)
