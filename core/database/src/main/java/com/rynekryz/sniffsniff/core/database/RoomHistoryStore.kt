package com.rynekryz.sniffsniff.core.database

import com.rynekryz.sniffsniff.core.common.model.AppTraffic
import com.rynekryz.sniffsniff.core.common.model.ConnectionRecord
import com.rynekryz.sniffsniff.core.common.model.ConnectionState
import com.rynekryz.sniffsniff.core.common.model.Protocol
import com.rynekryz.sniffsniff.core.common.repository.HistoryStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomHistoryStore @Inject constructor(database: SniffDatabase) : HistoryStore {
    private val connectionDao = database.connectionDao()
    private val usageDao = database.appUsageDao()

    override fun connections(limit: Int): Flow<List<ConnectionRecord>> =
        connectionDao.observe(limit).map { rows -> rows.map { it.toRecord() } }

    override fun connection(id: Long): Flow<ConnectionRecord?> =
        connectionDao.observeOne(id).map { it?.toRecord() }

    override fun appUsage(sinceDay: Long): Flow<List<AppTraffic>> =
        usageDao.observe(sinceDay).map { rows ->
            rows.map { AppTraffic(it.packageName, it.label, uploadBytes = it.uploadBytes, downloadBytes = it.downloadBytes, connections = it.connections) }
        }

    override suspend fun saveConnections(connections: List<ConnectionRecord>) {
        if (connections.isNotEmpty()) connectionDao.upsert(connections.map { it.toEntity() })
    }

    override suspend fun addAppUsage(day: Long, usage: List<AppTraffic>) {
        for (item in usage) {
            val current = usageDao.find(item.packageName, day)
            usageDao.upsert(
                AppUsageEntity(
                    packageName = item.packageName,
                    day = day,
                    label = item.label,
                    uploadBytes = (current?.uploadBytes ?: 0) + item.uploadBytes,
                    downloadBytes = (current?.downloadBytes ?: 0) + item.downloadBytes,
                    connections = (current?.connections ?: 0) + item.connections,
                ),
            )
        }
    }

    override suspend fun prune(olderThan: Long) {
        connectionDao.prune(olderThan)
        usageDao.prune(olderThan)
    }

    override suspend fun clear() {
        connectionDao.clear()
        usageDao.clear()
    }

    private fun ConnectionEntity.toRecord() = ConnectionRecord(
        id = id,
        protocol = Protocol.entries.firstOrNull { it.name == protocol } ?: Protocol.OTHER,
        localAddress = localAddress,
        localPort = localPort,
        remoteAddress = remoteAddress,
        remotePort = remotePort,
        uid = uid,
        packageName = packageName,
        appLabel = appLabel,
        host = host,
        state = ConnectionState.CLOSED,
        uploadBytes = uploadBytes,
        downloadBytes = downloadBytes,
        uploadPackets = uploadPackets,
        downloadPackets = downloadPackets,
        firstSeen = firstSeen,
        lastSeen = lastSeen,
    )

    private fun ConnectionRecord.toEntity() = ConnectionEntity(
        id = id,
        protocol = protocol.name,
        localAddress = localAddress,
        localPort = localPort,
        remoteAddress = remoteAddress,
        remotePort = remotePort,
        uid = uid,
        packageName = packageName,
        appLabel = appLabel,
        host = host,
        uploadBytes = uploadBytes,
        downloadBytes = downloadBytes,
        uploadPackets = uploadPackets,
        downloadPackets = downloadPackets,
        firstSeen = firstSeen,
        lastSeen = lastSeen,
    )
}
