package com.jatri.core.database.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A search the user ran. Locations are stored by id and resolved through the location repository,
 * so a renamed station still shows correctly. Same route + date + mode is stored once.
 */
@Entity(
    tableName = "recent_searches",
    indices = [Index(value = ["mode", "from_id", "to_id", "date"], unique = true)],
)
data class RecentSearchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String,
    @androidx.room.ColumnInfo(name = "from_id") val fromId: String,
    @androidx.room.ColumnInfo(name = "to_id") val toId: String,
    /** ISO-8601 date, e.g. 2026-12-01. */
    val date: String,
    val adults: Int,
    val children: Int,
    val infants: Int,
    @androidx.room.ColumnInfo(name = "searched_at") val searchedAt: Long,
)
