package com.rynekryz.sniffsniff.core.database

import android.content.Context
import androidx.room.Room
import com.rynekryz.sniffsniff.core.common.repository.HistoryStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseBindings {
    @Binds
    abstract fun historyStore(impl: RoomHistoryStore): HistoryStore
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseProvider {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): SniffDatabase =
        Room.databaseBuilder(context, SniffDatabase::class.java, "sniffsniff.db").build()
}
