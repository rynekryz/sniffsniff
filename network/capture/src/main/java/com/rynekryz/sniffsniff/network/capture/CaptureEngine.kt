package com.rynekryz.sniffsniff.network.capture

import com.rynekryz.sniffsniff.core.common.model.AppTraffic
import com.rynekryz.sniffsniff.core.common.model.CaptureState
import com.rynekryz.sniffsniff.core.common.model.ConnectionRecord
import com.rynekryz.sniffsniff.core.common.model.ConnectionState
import com.rynekryz.sniffsniff.core.common.model.Direction
import com.rynekryz.sniffsniff.core.common.model.PacketRecord
import com.rynekryz.sniffsniff.core.common.model.RatePoint
import com.rynekryz.sniffsniff.core.common.model.TrafficStats
import com.rynekryz.sniffsniff.core.common.model.UNKNOWN_UID
import com.rynekryz.sniffsniff.core.common.repository.HistoryStore
import com.rynekryz.sniffsniff.core.common.repository.TrafficRepository
import com.rynekryz.sniffsniff.core.common.settings.AppSettings
import com.rynekryz.sniffsniff.core.common.settings.PayloadMode
import com.rynekryz.sniffsniff.network.flow.FlowEntry
import com.rynekryz.sniffsniff.network.flow.FlowTable
import com.rynekryz.sniffsniff.network.flow.TrafficMeter
import com.rynekryz.sniffsniff.network.forwarding.PacketObserver
import com.rynekryz.sniffsniff.network.parser.PacketDissector
import com.rynekryz.sniffsniff.network.parser.PacketParser
import com.rynekryz.sniffsniff.network.pcap.PcapWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.InetSocketAddress
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CaptureEngine @Inject constructor(
    private val resolver: AppResolver,
    private val history: HistoryStore,
) : TrafficRepository, PacketObserver {

    private class Captured(val data: ByteArray, val length: Int, val direction: Direction, val timestamp: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    private val _captureState = MutableStateFlow(CaptureState.IDLE)
    private val _captureError = MutableStateFlow<String?>(null)
    private val _stats = MutableStateFlow(TrafficStats())
    private val _rateHistory = MutableStateFlow<List<RatePoint>>(emptyList())
    private val _connections = MutableStateFlow<List<ConnectionRecord>>(emptyList())
    private val _packets = MutableStateFlow<List<PacketRecord>>(emptyList())
    private val _applications = MutableStateFlow<List<AppTraffic>>(emptyList())

    override val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()
    override val captureError: StateFlow<String?> = _captureError.asStateFlow()
    override val stats: StateFlow<TrafficStats> = _stats.asStateFlow()
    override val rateHistory: StateFlow<List<RatePoint>> = _rateHistory.asStateFlow()
    override val connections: StateFlow<List<ConnectionRecord>> = _connections.asStateFlow()
    override val packets: StateFlow<List<PacketRecord>> = _packets.asStateFlow()
    override val applications: StateFlow<List<AppTraffic>> = _applications.asStateFlow()

    @Volatile
    private var incoming = Channel<Captured>(QUEUE_CAPACITY, BufferOverflow.DROP_OLDEST)

    @Volatile
    private var accepting = false

    private var session: Job? = null
    private var settings = AppSettings()
    private var table = FlowTable()
    private var meter = TrafficMeter()
    private var store = PacketStore(0)
    private var startedAt = 0L
    private var nextPacketId = 1L
    private var dirty = false
    private var ticks = 0

    override fun onPacket(data: ByteArray, length: Int, direction: Direction) {
        if (accepting) incoming.trySend(Captured(data, length, direction, System.currentTimeMillis()))
    }

    fun onStarting() {
        _captureError.value = null
        _captureState.value = CaptureState.STARTING
    }

    fun onFailed(message: String) {
        accepting = false
        _captureError.value = message
        _captureState.value = CaptureState.IDLE
    }

    fun beginSession(newSettings: AppSettings) {
        val channel = Channel<Captured>(QUEUE_CAPACITY, BufferOverflow.DROP_OLDEST)
        incoming = channel
        accepting = true
        session = scope.launch {
            val now = System.currentTimeMillis()
            settings = newSettings
            startedAt = now
            table = FlowTable(idBase = now shl ID_SHIFT)
            meter = TrafficMeter().also { it.start(now) }
            store = PacketStore(if (newSettings.storePackets) newSettings.maxPackets else 0)
            nextPacketId = 1L
            ticks = 0
            dirty = true
            publish(now)
            _captureState.value = CaptureState.RUNNING
            launch { for (captured in channel) process(captured) }
            launch {
                while (true) {
                    delay(TICK_MILLIS)
                    tick()
                }
            }
        }
    }

    fun endSession() {
        accepting = false
        val channel = incoming
        val job = session
        session = null
        _captureState.value = CaptureState.STOPPING
        scope.launch {
            job?.cancelAndJoin()
            channel.close()
            table.closeAll()
            flushHistory()
            dirty = true
            publish(System.currentTimeMillis())
            _captureState.value = CaptureState.IDLE
        }
    }

    private fun process(captured: Captured) {
        val packet = PacketParser.parse(captured.data, captured.length) ?: return
        meter.record(captured.direction, packet.totalLength)
        val entry = table.update(packet, captured.data, captured.direction, captured.timestamp)
        if (entry.uid == UNKNOWN_UID && entry.uidAttempts < MAX_UID_ATTEMPTS && captured.direction == Direction.UPLOAD) {
            entry.uidAttempts++
            resolveOwner(entry)
        }
        if (settings.storePackets) {
            val keep = when (settings.payloadMode) {
                PayloadMode.HEADERS -> packet.payloadOffset
                PayloadMode.PREVIEW -> packet.payloadOffset + minOf(packet.payloadLength, PREVIEW_BYTES)
                PayloadMode.FULL -> captured.length
            }.coerceIn(0, captured.length)
            val data = if (keep == captured.data.size) captured.data else captured.data.copyOf(keep)
            store.add(
                PacketRecord(
                    id = nextPacketId++,
                    connectionId = entry.id,
                    timestamp = captured.timestamp,
                    direction = captured.direction,
                    protocol = packet.protocol,
                    sourceAddress = packet.source.hostAddress.orEmpty(),
                    sourcePort = packet.sourcePort,
                    destinationAddress = packet.destination.hostAddress.orEmpty(),
                    destinationPort = packet.destinationPort,
                    length = packet.totalLength,
                    info = PacketDissector.summarize(packet, captured.data),
                    packageName = entry.packageName,
                    appLabel = entry.appLabel,
                    data = data,
                ),
            )
        }
        dirty = true
    }

    private fun resolveOwner(entry: FlowEntry) {
        val key = entry.key
        val uid = resolver.ownerUid(
            key.protocolNumber,
            InetSocketAddress(key.localAddress, key.localPort),
            InetSocketAddress(key.remoteAddress, key.remotePort),
        )
        if (uid == UNKNOWN_UID) return
        val identity = resolver.identify(uid)
        entry.uid = uid
        entry.packageName = identity.packageName
        entry.appLabel = identity.label
    }

    private suspend fun tick() {
        val now = System.currentTimeMillis()
        meter.tick(now)
        table.sweep(now)
        publish(now)
        if (++ticks % HISTORY_EVERY_TICKS == 0) flushHistory()
    }

    private fun publish(now: Long) {
        val history = meter.history()
        val latest = history.lastOrNull()
        _rateHistory.value = history
        _stats.value = TrafficStats(
            uploadBytes = meter.uploadBytes,
            downloadBytes = meter.downloadBytes,
            uploadRate = latest?.upload ?: 0,
            downloadRate = latest?.download ?: 0,
            packets = meter.packets,
            activeConnections = table.activeCount,
            totalConnections = table.size,
            startedAt = startedAt,
        )
        if (!dirty) return
        dirty = false
        _connections.value = table.snapshot()
        _packets.value = store.snapshot()
        _applications.value = table.entries()
            .groupBy { it.packageName }
            .map { (packageName, flows) ->
                AppTraffic(
                    packageName = packageName,
                    label = flows.first().appLabel,
                    uid = flows.first().uid,
                    uploadBytes = flows.sumOf { it.uploadBytes },
                    downloadBytes = flows.sumOf { it.downloadBytes },
                    connections = flows.size,
                    activeConnections = flows.count { it.state == ConnectionState.ACTIVE },
                    lastActive = flows.maxOf { it.lastSeen },
                )
            }
            .sortedByDescending { it.totalBytes }
    }

    private suspend fun flushHistory() {
        val closed = table.drainClosed()
        val retention = settings.historyRetentionDays
        if (retention <= 0) return
        val deltas = HashMap<String, AppTraffic>()
        for (entry in table.entries()) {
            val up = entry.uploadBytes - entry.reportedUpload
            val down = entry.downloadBytes - entry.reportedDownload
            val isNew = !entry.counted
            if (up == 0L && down == 0L && !isNew) continue
            entry.reportedUpload = entry.uploadBytes
            entry.reportedDownload = entry.downloadBytes
            entry.counted = true
            val previous = deltas[entry.packageName]
            deltas[entry.packageName] = AppTraffic(
                packageName = entry.packageName,
                label = entry.appLabel,
                uploadBytes = (previous?.uploadBytes ?: 0) + up,
                downloadBytes = (previous?.downloadBytes ?: 0) + down,
                connections = (previous?.connections ?: 0) + if (isNew) 1 else 0,
            )
        }
        val now = System.currentTimeMillis()
        val today = startOfDay(now)
        withContext(Dispatchers.IO) {
            runCatching {
                history.saveConnections(closed)
                history.addAppUsage(today, deltas.values.toList())
                history.prune(startOfDay(now - retention * DAY_MILLIS))
            }
        }
    }

    override suspend fun exportPcap(output: OutputStream): Int {
        val snapshot = withContext(scope.coroutineContext) { store.snapshot() }
        return withContext(Dispatchers.IO) {
            PcapWriter(output).use { writer ->
                for (packet in snapshot) writer.write(packet.timestamp, packet.data, packet.data.size, packet.length)
                writer.packetCount
            }
        }
    }

    override fun clearSession() {
        scope.launch {
            val running = _captureState.value == CaptureState.RUNNING
            table = FlowTable(idBase = System.currentTimeMillis() shl ID_SHIFT)
            store = PacketStore(if (settings.storePackets) settings.maxPackets else 0)
            if (!running) meter = TrafficMeter()
            dirty = true
            publish(System.currentTimeMillis())
        }
    }

    override fun dismissError() {
        _captureError.value = null
    }

    private fun startOfDay(timestamp: Long): Long = Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private companion object {
        const val QUEUE_CAPACITY = 16384
        const val TICK_MILLIS = 1000L
        const val HISTORY_EVERY_TICKS = 5
        const val PREVIEW_BYTES = 128
        const val MAX_UID_ATTEMPTS = 3
        const val ID_SHIFT = 20
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}
