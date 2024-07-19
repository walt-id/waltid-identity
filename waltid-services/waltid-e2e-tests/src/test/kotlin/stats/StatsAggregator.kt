package stats

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal

class StatsAggregator {
    private val testResults = ArrayList<Result<Any?>>()
    private val testNames = HashMap<Int, String>()
    private val t = Terminal(ansiLevel = AnsiLevel.TRUECOLOR)

    fun printStats() {
        t.println("\n" + TextColors.magenta("Test results:"))
        testResults.forEachIndexed { index, result ->
            val idx = index + 1
            val name = testNames[idx]!!
            t.println(TextColors.magenta("$idx. $name: ${result.toSuccessString()}"))
        }

        val testStats = getTestStats()
        if (testStats.failed > 0) {
            error("${testStats.failed} tests failed!")
        }

        if (testStats.overall == 0) {
            error("Error - no E2E tests were executed!")
        }
    }

    fun logTestResult(result: Result<Any>, id: String, name: String) {
        t.println("\n${TextColors.cyan(TextStyles.bold("---=== Start $id. test: $name === ---"))}")

        testResults.add(result)

        t.println(TextColors.blue("End result of test \"$name\": $result"))
        if (result.isFailure) {
            result.exceptionOrNull()!!.printStackTrace()
        }

        t.println(TextStyles.bold(TextColors.cyan("---===  End  ${id}. test: $name === ---") + " " + result.toSuccessString()) + "\n")

        val overallSuccess = testResults.count { it.isSuccess }
        val failed = testResults.size - overallSuccess
        val failedStr = if (failed == 0) "none failed ✅" else TextColors.red("$failed failed")
        t.println(TextColors.magenta("Current test stats: ${testResults.size} overall | $overallSuccess succeeded | $failedStr\n"))
    }

    private fun getTestStats(): TestStats {
        val succeeded = testResults.count { it.isSuccess }
        val failed = testResults.size - succeeded
        return TestStats(testResults.size, succeeded, failed)
    }

    private fun Result<*>.toSuccessString() = if (isSuccess) {
        val res = if (getOrNull() !is Unit) " (${getOrNull().toString()})" else ""
        TextColors.green("✅ SUCCESS$res")
    } else {
        val res = exceptionOrNull()!!.message?.let { " ($it)" } ?: ""
        TextColors.red("❌ FAILURE$res")
    }
}