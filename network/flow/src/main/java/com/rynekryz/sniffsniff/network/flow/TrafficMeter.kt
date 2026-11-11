package com.rynekryz.sniffsniff.network.flow

import com.rynekryz.sniffsniff.core.common.model.Direction
import com.rynekryz.sniffsniff.core.common.model.RatePoint

class TrafficMeter(private val historySize: Int = 60) {
    var uploadBytes = 0L
        private set
    var downloadBytes = 0L
        private set
    var packets = 0L
        private set

    private var lastUpload = 0L
    private var lastDownload = 0L
    private var lastTick = 0L
    private val points = ArrayDeque<RatePoint>()

    fun record(direction: Direction, bytes: Int) {
        packets++
        if (direction == Direction.UPLOAD) uploadBytes += bytes else downloadBytes += bytes
    }

    fun start(now: Long) {
        lastTick = now
    }

    fun tick(now: Long): RatePoint {
        val elapsed = (now - lastTick).coerceAtLeast(1)
        val point = RatePoint(
            timestamp = now,
            upload = (uploadBytes - lastUpload) * 1000 / elapsed,
            download = (downloadBytes - lastDownload) * 1000 / elapsed,
        )
        lastUpload = uploadBytes
        lastDownload = downloadBytes
        lastTick = now
        points.addLast(point)
        while (points.size > historySize) points.removeFirst()
        return point
    }

    fun history(): List<RatePoint> = points.toList()
}
