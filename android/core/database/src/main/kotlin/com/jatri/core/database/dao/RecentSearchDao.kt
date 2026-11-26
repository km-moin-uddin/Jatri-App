package com.jatri.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.jatri.core.database.model.RecentSearchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentSearchDao {
    @Query("SELECT * FROM recent_searches ORDER BY searched_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecentSearchEntity>>

    @Query(
        "SELECT id FROM recent_searches WHERE mode = :mode AND from_id = :fromId AND to_id = :toId AND date = :date",
    )
    suspend fun findId(mode: String, fromId: String, toId: String, date: String): Long?

    @Upsert
    suspend fun upsert(entity: RecentSearchEntity)

    /** Keep the table small: only the newest [keep] rows survive. */
    @Query(
        "DELETE FROM recent_searches WHERE id NOT IN (SELECT id FROM recent_searches ORDER BY searched_at DESC LIMIT :keep)",
    )
    suspend fun trim(keep: Int)

    @Query("DELETE FROM recent_searches")
    suspend fun clear()
}
