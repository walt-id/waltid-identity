package id.walt.commons.logging

import id.walt.commons.logging.setups.TraceLoggingSetup

object TestLoggingUtils {

    fun setupTestLogging() {
        LoggingManager.useLoggingSetup(TraceLoggingSetup)
        LoggingManager.setup()
    }

}
