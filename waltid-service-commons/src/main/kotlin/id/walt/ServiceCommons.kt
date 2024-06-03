package id.walt

import id.walt.config.ConfigManager
import id.walt.config.WaltConfig
import id.walt.config.buildconfig.BuildConfig
import id.walt.config.list.WebConfig
import id.walt.config.runconfig.RunConfiguration
import io.klogging.logger
import kotlinx.coroutines.delay
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName
import kotlin.time.measureTime
import java.lang.System.getProperty as p

data class ServiceConfiguration(
    val name: String = "",
    val vendor: String = "walt.id",
    val version: String = BuildConfig.version,
)

data class ConfigurationsList(
    val mandatory: List<Pair<String, KClass<out WaltConfig>>> = emptyList(),
    val optional: List<Pair<String, KClass<out WaltConfig>>> = emptyList(),

    val default: List<Pair<String, KClass<out WaltConfig>>> = listOf(
        "web" to WebConfig::class
    ),
)

data class ServiceInitialization(
    val configs: ConfigurationsList = ConfigurationsList(), // change to configs
    val init: suspend () -> Unit,
    val run: suspend () -> Unit,
)

object ServiceCommons {

    private fun debugLineString(): String =
        "Running on ${p("os.arch")} ${p("os.name")} ${p("os.version")} with ${p("java.vm.name")} ${p("java.vm.version")} in path ${Path(".").absolutePathString()}"

    suspend fun runService(
        config: ServiceConfiguration,
        init: ServiceInitialization,
    ) {
        val service = "${config.vendor} ${config.name} ${config.version}"

        val log = logger(config.vendor + " " + config.name)
//        val log = logger(config.name)
        log.info { "Starting $service..." }

        log.debug { debugLineString() }

        log.info { "Registering configurations..." }
        init.configs.default.forEach {
            log.debug { "Registering default mandatory \"${it.first}\" to config ${it.second.jvmName}..." }
            ConfigManager.registerRequiredConfig(it.first, it.second)
        }
        init.configs.mandatory.forEach {
            log.debug { "Registering mandatory \"${it.first}\" to config ${it.second.jvmName}..." }
            ConfigManager.registerRequiredConfig(it.first, it.second)
        }
        init.configs.optional.forEach {
            log.debug { "Registering optional \"${it.first}\" to config ${it.second.jvmName}..." }
            ConfigManager.registerConfig(it.first, it.second)
        }
        log.info { "Loading configurations..." }
        ConfigManager.loadConfigs(RunConfiguration.args)

        log.info { "Initializing $service..." }
        measureTime {
            init.init.invoke()
        }.also {
            log.info { "Initialization completed ($it)." }
        }

        log.info { "Running $service..." }
        measureTime {
            init.run.invoke()
        }.also {
            log.info { "Run completed ($it)." }
        }

        delay(50)
        log.info { "Service $service done." }
    }

}

fun main() {
    println(ServiceConfiguration().version)
}
