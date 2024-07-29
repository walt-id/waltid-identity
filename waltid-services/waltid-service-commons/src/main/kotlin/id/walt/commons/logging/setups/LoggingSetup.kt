package id.walt.commons.logging.setups

import io.klogging.config.KloggingConfiguration

sealed class LoggingSetup(val name: String, val config: KloggingConfiguration.() -> Unit)
