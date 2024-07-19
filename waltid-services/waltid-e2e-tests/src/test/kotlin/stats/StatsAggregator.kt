package stats

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal
import java.util.*

class StatsAggregator {
    private val testStats = ArrayList<TestStatIdentifier>()
    private val t = Terminal(ansiLevel = AnsiLevel.TRUECOLOR)

    fun printStats() {
        t.println("\n" + TextColors.magenta("Test results:"))
        testStats.forEachIndexed { index, stat ->
            val name = testStats[index].name
            t.println(TextColors.magenta("{$index+1}. $name: ${stat.result.toSuccessString()}"))
        }

        val testStats = getTestStats()
        if (testStats.failed > 0) {
            error("${testStats.failed} tests failed!")
        }

        if (testStats.overall == 0) {
            error("Error - no E2E tests were executed!")
        }
    }

    fun logTestStart(name: String) {
        val id = testStats.size + 1
        t.println("\n${TextColors.cyan(TextStyles.bold("---=== Start $id. test: $name === ---"))}")
    }

    fun logTestResult(option: Optional<Throwable>?, name: String) = let {
        option.takeIf { it?.isPresent ?: false }?.let {
            Result.failure<Any>(it.get())
        } ?: Result.success<Any?>(null)
    }.run { logTestResult(this, name) }

    private fun logTestResult(result: Result<*>, name: String) {
        testStats.add(TestStatIdentifier(name, result))
        val id = testStats.size

        t.println(TextColors.blue("End result of test \"$name\": $result"))
        if (result.isFailure) {
            result.exceptionOrNull()!!.printStackTrace()
        }

        t.println(TextStyles.bold(TextColors.cyan("---===  End  ${id}. test: $name === ---") + " " + result.toSuccessString()) + "\n")

        val overallSuccess = testStats.count { it.result.isSuccess }
        val failed = testStats.size - overallSuccess
        val failedStr = if (failed == 0) "none failed ✅" else TextColors.red("$failed failed")
        t.println(TextColors.magenta("Current test stats: ${testStats.size} overall | $overallSuccess succeeded | $failedStr\n"))
    }

    private fun getTestStats(): TestStats {
        val succeeded = testStats.count { it.result.isSuccess }
        val failed = testStats.size - succeeded
        return TestStats(testStats.size, succeeded, failed)
    }

    private fun Result<*>.toSuccessString() = if (isSuccess) {
        val res = if (getOrNull() !is Unit) " (${getOrNull().toString()})" else ""
        TextColors.green("✅ SUCCESS$res")
    } else {
        val res = exceptionOrNull()!!.message?.let { " ($it)" } ?: ""
        TextColors.red("❌ FAILURE$res")
    }

    data class TestStatIdentifier(
        val name: String,
        val result: Result<*>,
    )
}