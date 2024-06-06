package id.walt.commons.logging

import id.walt.commons.logging.LoggingManager

object TestLoggingUtils {

    fun setupTestLogging() {
        LoggingManager.useLoggingSetup("trace")
        LoggingManager.setup()
    }

}
