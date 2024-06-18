package id.walt.commons.web.modules

import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.HealthCheckResult
import id.walt.commons.config.ConfigManager
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
import kotlin.time.Duration.Companion.seconds

object ServiceHealthChecksDebugModule {

    private val logger = logger<ServiceHealthChecksDebugModule>()

    enum class KtorStatus {
        Unknown,
        ApplicationStarting,
        ApplicationStarted,
        ServerReady,
        ApplicationStopPreparing,
        ApplicationStopping,
        ApplicationStopped
    }

    object KtorStatusChecker {

        private val log = noCoLogger("Ktor status")

        var ktorStatus: KtorStatus = KtorStatus.Unknown
            set(value) {
                log.trace { "New ktor server status: ${value.name}" }
                field = value
            }

        fun Application.init() {
            environment.monitor.subscribe(ApplicationStarting) { ktorStatus = KtorStatus.ApplicationStarting }
            environment.monitor.subscribe(ApplicationStarted) { ktorStatus = KtorStatus.ApplicationStarted }
            environment.monitor.subscribe(ServerReady) { ktorStatus = KtorStatus.ServerReady }
            environment.monitor.subscribe(ApplicationStopPreparing) { ktorStatus = KtorStatus.ApplicationStopPreparing }
            environment.monitor.subscribe(ApplicationStopping) { ktorStatus = KtorStatus.ApplicationStopping }
            environment.monitor.subscribe(ApplicationStopped) { ktorStatus = KtorStatus.ApplicationStopped }
        }
    }

    val enableDebug = logger.isTraceEnabled()

    data class ServiceDebugModuleConfiguration(
        val endpointPrefix: String = "debug",
        val operatingSystem: Boolean = enableDebug,
        val memory: Boolean = enableDebug, // currently broken?
        val memoryPool: Boolean = enableDebug,
        val ram: Boolean = enableDebug,
        val cpu: Boolean = enableDebug,
        val jvm: Boolean = enableDebug,
        val sysprops: Boolean = enableDebug,
        val heapdump: Boolean = enableDebug,
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
                endpointPrefix = debugConfig.endpointPrefix
                operatingSystem = debugConfig.operatingSystem
                memory = debugConfig.memory
                jvmInfo = debugConfig.jvm
                sysprops = debugConfig.sysprops
                heapDump = debugConfig.heapdump
                threadDump = debugConfig.threaddump
                gc = debugConfig.gc
            }

            KtorStatusChecker.run { init() }

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
                        context.respond(buildJsonObject {
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
                        context.respond(buildJsonObject {
                            put("loadAverage", ManagementFactory.getOperatingSystemMXBean().systemLoadAverage)
                            put("processors", ManagementFactory.getOperatingSystemMXBean().availableProcessors)
                            put("threadCount", JsonPrimitive(thread.threadCount))
                            put("peakThreadCount", JsonPrimitive(thread.threadCount))
                            put("daemonThreadCount", JsonPrimitive(thread.daemonThreadCount))
                        })
                    }

                    if (debugConfig.memoryPool) get("memoryPool") {
                        context.respond(buildJsonObject {
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
