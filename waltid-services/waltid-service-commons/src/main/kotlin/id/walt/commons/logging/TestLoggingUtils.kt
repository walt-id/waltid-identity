package id.walt.logging

object TestLoggingUtils {

    fun setupTestLogging() {
        LoggingManager.useLoggingSetup("trace")
        LoggingManager.setup()
    }

}
