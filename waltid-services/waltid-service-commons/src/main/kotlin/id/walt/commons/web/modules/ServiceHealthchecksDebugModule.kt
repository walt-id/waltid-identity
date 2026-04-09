package id.walt.commons.web.modules

import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.HealthCheckResult
import id.walt.commons.config.ConfigManager
import id.walt.commons.config.statics.RunConfiguration
import id.walt.commons.config.statics.ServiceConfig.config
import id.walt.commons.featureflag.CommonsFeatureCatalog
import id.walt.commons.featureflag.FeatureManager
import io.klogging.logger
import io.klogging.noCoLogger
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import java.lang.management.ManagementFactory
import java.lang.management.MemoryUsage
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

object ServiceHealthChecksDebugModule {

    private val logger = logger<ServiceHealthChecksDebugModule>()

    enum class KtorStatus {
        Unknown,
        ApplicationStarting,
        ApplicationModulesLoading,
        ApplicationModulesLoaded,
        ApplicationStarted,
        ServerReady,
        ApplicationStopPreparing,
        ApplicationStopping,
        ApplicationStopped
    }

    object KtorStatusChecker {

        private val logger = noCoLogger("EnterpriseStatus")

        @Volatile
        var ktorStatus: KtorStatus = KtorStatus.Unknown

        fun Application.init() {
            monitor.subscribe(ApplicationStarting) {
                ktorStatus = KtorStatus.ApplicationStarting
                logger.info("${config.vendor} ${config.name} - Starting up...")
            }
            monitor.subscribe(ApplicationModulesLoading) {
                ktorStatus = KtorStatus.ApplicationModulesLoading
                logger.info("${config.vendor} ${config.name} - Application modules loading...")
            }
            monitor.subscribe(ApplicationModulesLoaded) {
                ktorStatus = KtorStatus.ApplicationModulesLoaded
                logger.info("${config.vendor} ${config.name} - Application modules loaded, web server is starting...")
            }
            monitor.subscribe(ApplicationStarted) {
                ktorStatus = KtorStatus.ApplicationStarted
                logger.info("${config.vendor} ${config.name} - Web server started, the app will be ready soon...")
            }
            monitor.subscribe(ServerReady) {
                ktorStatus = KtorStatus.ServerReady
                val totalStartupTime = Clock.System.now() - RunConfiguration.serviceStartupTime
                logger.info("${config.vendor} ${config.name} - Web server ready! [total startup time: ${totalStartupTime.inWholeMilliseconds}ms]")
            }

            monitor.subscribe(ApplicationStopPreparing) {
                ktorStatus = KtorStatus.ApplicationStopPreparing
                logger.info("${config.vendor} ${config.name} - Web server - stop preparing...")
            }
            monitor.subscribe(ApplicationStopping) {
                ktorStatus = KtorStatus.ApplicationStopping
                logger.info("${config.vendor} ${config.name} - Web server - stopping...")
            }
            monitor.subscribe(ApplicationStopped) {
                ktorStatus = KtorStatus.ApplicationStopped
                logger.info("${config.vendor} ${config.name} - Web server is stopped.")
            }
        }
    }

    val enableDebug = logger.isDebugEnabled()
    val enableTrace = logger.isTraceEnabled()

    data class ServiceDebugModuleConfiguration(
        val endpointPrefix: String = "debug",
        val operatingSystem: Boolean = enableDebug,
        val memory: Boolean = enableTrace, // currently broken?
        val memoryPool: Boolean = enableDebug,
        val ram: Boolean = enableDebug,
        val cpu: Boolean = enableDebug,
        val jvm: Boolean = enableDebug,
        val sysprops: Boolean = enableDebug,
        val heapdump: Boolean = enableTrace,
        val threaddump: Boolean = enableDebug,
        val gc: Boolean = enableDebug,
    )

    private fun memoryStringToJson(memory: MemoryUsage): JsonObject = buildJsonObject {
        memory.toString().split(" = ").flatMap { it.split(" ") }
            .chunked(2) { it[0] to it[1] }.forEach {
                put(it.first, JsonPrimitive(it.second))
            }
    }

    fun Application.enable() {
        val debugConfig = if (FeatureManager.isFeatureEnabled(CommonsFeatureCatalog.debugEndpointsFeature))
            ConfigManager.getConfig<ServiceDebugModuleConfiguration>()
        else null

        install(Cohort) {
            if (debugConfig != null) {
                heapDump = debugConfig.heapdump
                operatingSystem = debugConfig.operatingSystem
                memory = debugConfig.memory
                jvmInfo = debugConfig.jvm
                gc = debugConfig.gc
                threadDump = debugConfig.threaddump
                sysprops = debugConfig.sysprops
                endpointPrefix = debugConfig.endpointPrefix
            }

            healthcheck("livez", HealthCheckRegistry {
                register("http", {
                    when (KtorStatusChecker.ktorStatus) {
                        KtorStatus.ServerReady -> HealthCheckResult.healthy("ktor ready")
                        else -> HealthCheckResult.unhealthy("ktor not ready; in status: ${KtorStatusChecker.ktorStatus}")
                    }
                }, 2.seconds, 1.seconds)
            })
        }

        // Custom debug endpoints
        routing {
            if (debugConfig != null) {
                route(debugConfig.endpointPrefix) {
                    if (debugConfig.ram) get("ram") {
                        val rt = Runtime.getRuntime()
                        val memory = ManagementFactory.getMemoryMXBean()
                        call.respond(buildJsonObject {
                            put("free", rt.freeMemory())
                            put("max", rt.maxMemory())
                            put("total", rt.totalMemory())
                            put("used", (rt.totalMemory() - rt.freeMemory()))

                            put("heap", memoryStringToJson(memory.heapMemoryUsage))
                            put("non_heap", memoryStringToJson(memory.nonHeapMemoryUsage))
                        })
                    }

                    if (debugConfig.cpu) get("cpu") {
                        val thread = ManagementFactory.getThreadMXBean()
                        call.respond(buildJsonObject {
                            put("loadAverage", ManagementFactory.getOperatingSystemMXBean().systemLoadAverage)
                            put("processors", ManagementFactory.getOperatingSystemMXBean().availableProcessors)
                            put("threadCount", JsonPrimitive(thread.threadCount))
                            put("peakThreadCount", JsonPrimitive(thread.threadCount))
                            put("daemonThreadCount", JsonPrimitive(thread.daemonThreadCount))
                        })
                    }

                    if (debugConfig.memoryPool) get("memoryPool") {
                        call.respond(buildJsonObject {
                            ManagementFactory.getMemoryPoolMXBeans().forEach {
                                putJsonObject(it.name) {
                                    put("type", JsonPrimitive(it.type.name))
                                    put("valid", JsonPrimitive(it.isValid))
                                    put("usage", memoryStringToJson(it.usage))
                                    putJsonArray("managerNames") { it.memoryManagerNames.forEach { name -> add(JsonPrimitive(name)) } }
                                }
                            }
                        })
                    }
                }
            }
        }
    }
}
