package id.walt.webwallet.performance

import id.walt.commons.featureflag.FeatureManager
import id.walt.webwallet.FeatureCatalog
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark
import kotlin.time.toDuration

val Stopwatch = when (FeatureManager.isFeatureEnabled(FeatureCatalog.stopwatchFeature)) {
    true -> DefaultStopwatch
    else -> VoidStopwatch
}


interface StopwatchInterface {
    fun startTimer(timerId: String)
    fun addTiming(timerId: String, timeMarkId: String)
    fun report(): Map<String, TimeMarkReport>

    data class TimeMarkReport(
        val min: Duration,
        val max: Duration,
        val average: Duration,
        val total: Duration,
        val count: Int
    )

}

private object VoidStopwatch : StopwatchInterface {
    override fun startTimer(timerId: String) {
    }

    override fun addTiming(timerId: String, timeMarkId: String) {
    }

    override fun report(): Map<String, StopwatchInterface.TimeMarkReport> {
        return emptyMap()
    }
}

/**
 * The stopwatch is not ready for production, restriction of maximum
 * time marks is mission -> memory leak. Use the stopwatch only
 * with integration tests
 */
private object DefaultStopwatch : StopwatchInterface {

    private val timings = mutableListOf<Timing>()

    override fun startTimer(timerId: String) {
        val mark = TimeSource.Monotonic.markNow()
        timings.add(Timing(timerId, "start", mark))
    }

    override fun addTiming(timerId: String, timeMarkId: String) {
        val mark = TimeSource.Monotonic.markNow()
        timings.add(Timing(timerId, timeMarkId, mark))
    }

    override fun report(): Map<String, StopwatchInterface.TimeMarkReport> {
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
            val totalDurationUs = it.value.map { d -> d.toLong(DurationUnit.MICROSECONDS) }.sum()
            StopwatchInterface.TimeMarkReport(
                it.value.min(),
                it.value.max(),
                (totalDurationUs / it.value.size).toDuration(DurationUnit.MICROSECONDS),
                totalDurationUs.toDuration(DurationUnit.MICROSECONDS),
                it.value.size
            )
        }
    }

    private data class Timing(
        val timerId: String,
        val timeMarkId: String,
        val timeMark: ValueTimeMark,
    )
}