package com.rynekryz.sniffsniff.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connections")
data class ConnectionEntity(
    @PrimaryKey val id: Long,
    val protocol: String,
    val localAddress: String,
    val localPort: Int,
    val remoteAddress: String,
    val remotePort: Int,
    val uid: Int,
    val packageName: String,
    val appLabel: String,
    val host: String?,
    val uploadBytes: Long,
    val downloadBytes: Long,
    val uploadPackets: Long,
    val downloadPackets: Long,
    val firstSeen: Long,
    val lastSeen: Long,
)

@Entity(tableName = "app_usage", primaryKeys = ["packageName", "day"])
data class AppUsageEntity(
    val packageName: String,
    val day: Long,
    val label: String,
    val uploadBytes: Long,
    val downloadBytes: Long,
    val connections: Int,
)

data class UsageRow(
    val packageName: String,
    val label: String,
    val uploadBytes: Long,
    val downloadBytes: Long,
    val connections: Int,
)
