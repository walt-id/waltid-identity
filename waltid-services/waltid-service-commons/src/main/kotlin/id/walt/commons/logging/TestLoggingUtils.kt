package id.walt.commons.logging

object TestLoggingUtils {

    fun setupTestLogging() {
        LoggingManager.useLoggingSetup("trace")
        LoggingManager.setup()
    }

}
