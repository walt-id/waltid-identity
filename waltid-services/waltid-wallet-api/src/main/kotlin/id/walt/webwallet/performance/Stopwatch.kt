package id.walt.webwallet.performance

import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark
import kotlin.time.toDuration

object Stopwatch {

    private val timings = mutableListOf<Timing>()

    fun startTimer(timerId: String) {
        val mark = TimeSource.Monotonic.markNow()
        timings.add(Timing(timerId, "start", mark))
    }

    fun addTiming(timerId: String, timeMarkId: String) {
        val mark = TimeSource.Monotonic.markNow()
        timings.add(Timing(timerId, timeMarkId, mark))
    }

    fun report(): Map<String, TimeMarkReport> {
        val timingPerTimer = mutableMapOf<String, MutableList<Timing>>()
        timings.forEach { timing ->
            val timeMarkCollection = timingPerTimer.getOrPut(timing.timerId, { mutableListOf() })
            timeMarkCollection.add(timing)
        }
        val timingPerMark = mutableMapOf<String, MutableList<Duration>>()
        timingPerTimer.forEach { timing ->
            timing.value.zipWithNext({ a, b ->
                val markName = "${a.timeMarkId}-${b.timeMarkId}"
                val timingsOfMark = timingPerMark.getOrPut(markName, { mutableListOf() })
                timingsOfMark.add(b.timeMark.minus(a.timeMark))
            })
        }
        return timingPerMark.mapValues {
            TimeMarkReport(
                it.value.min(),
                it.value.max(),
                it.value.map { d ->
                    d.toLong(DurationUnit.MICROSECONDS)
                }.average().toDuration(DurationUnit.MICROSECONDS),
                it.value.sumOf({ d -> d.toLong(DurationUnit.MICROSECONDS)})
                    .toDuration(DurationUnit.MICROSECONDS)
            )
        }
    }

    data class TimeMarkReport(
        val min: Duration,
        val max: Duration,
        val average: Duration,
        val total: Duration,
    )

    private data class Timing(
        val timerId: String,
        val timeMarkId: String,
        val timeMark: ValueTimeMark,
    )
}