package com.rynekryz.sniffsniff.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionDao {
    @Query("SELECT * FROM connections ORDER BY lastSeen DESC LIMIT :limit")
    fun observe(limit: Int): Flow<List<ConnectionEntity>>

    @Query("SELECT * FROM connections WHERE id = :id")
    fun observeOne(id: Long): Flow<ConnectionEntity?>

    @Upsert
    suspend fun upsert(connections: List<ConnectionEntity>)

    @Query("DELETE FROM connections WHERE lastSeen < :threshold")
    suspend fun prune(threshold: Long)

    @Query("DELETE FROM connections")
    suspend fun clear()
}

@Dao
interface AppUsageDao {
    @Query(
        "SELECT packageName, MAX(label) AS label, SUM(uploadBytes) AS uploadBytes, " +
            "SUM(downloadBytes) AS downloadBytes, SUM(connections) AS connections " +
            "FROM app_usage WHERE day >= :since GROUP BY packageName " +
            "ORDER BY SUM(uploadBytes) + SUM(downloadBytes) DESC"
    )
    fun observe(since: Long): Flow<List<UsageRow>>

    @Query("SELECT * FROM app_usage WHERE packageName = :packageName AND day = :day")
    suspend fun find(packageName: String, day: Long): AppUsageEntity?

    @Upsert
    suspend fun upsert(entity: AppUsageEntity)

    @Query("DELETE FROM app_usage WHERE day < :day")
    suspend fun prune(day: Long)

    @Query("DELETE FROM app_usage")
    suspend fun clear()
}
