package com.jatri.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.jatri.core.database.dao.RecentSearchDao
import com.jatri.core.database.model.RecentSearchEntity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Local cache. Phase 1: recent searches. Later phases add cached tickets (offline access)
 * and saved travellers.
 */
@Database(entities = [RecentSearchEntity::class], version = 1, exportSchema = true)
abstract class JatriDatabase : RoomDatabase() {
    abstract fun recentSearchDao(): RecentSearchDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): JatriDatabase =
        Room.databaseBuilder(context, JatriDatabase::class.java, "jatri.db").build()

    @Provides
    fun providesRecentSearchDao(db: JatriDatabase): RecentSearchDao = db.recentSearchDao()
}
