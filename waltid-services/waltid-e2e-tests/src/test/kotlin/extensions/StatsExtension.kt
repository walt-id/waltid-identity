package extensions

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import stats.StatsAggregator

class StatsExtension : BeforeEachCallback, AfterEachCallback, AfterAllCallback {
    private val stats = StatsAggregator()

    override fun beforeEach(context: ExtensionContext?) {
        context?.let {
            stats.logTestStart(it.displayName)
        }
    }

    override fun afterEach(context: ExtensionContext?) {
        context?.let {
            stats.logTestResult(it.executionException, it.displayName)
        }
    }

    override fun afterAll(context: ExtensionContext?) {
        stats.printStats()
    }
}