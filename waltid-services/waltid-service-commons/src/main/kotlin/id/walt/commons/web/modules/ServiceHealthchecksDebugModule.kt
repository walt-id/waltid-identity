package id.walt.commons.web.modules

import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.HealthCheckResult
import id.walt.commons.config.ConfigManager
import io.klogging.logger
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

        var ktorStatus: KtorStatus = KtorStatus.Unknown

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
        val config = ConfigManager.getConfig<ServiceDebugModuleConfiguration>()

        install(Cohort) {
            endpointPrefix = config.endpointPrefix
            operatingSystem = config.operatingSystem
            memory = config.memory
            jvmInfo = config.jvm
            sysprops = config.sysprops
            heapDump = config.heapdump
            threadDump = config.threaddump
            gc = config.gc

            KtorStatusChecker.run { init() }

            healthcheck("livez", HealthCheckRegistry {
                register("http", {
                    when (KtorStatusChecker.ktorStatus) {
                        KtorStatus.ServerReady -> HealthCheckResult.healthy("ktor ready")
                        else -> HealthCheckResult.unhealthy("ktor not ready; in status: ${KtorStatusChecker.ktorStatus}")
                    }
                }, 1.seconds, 1.seconds)
            })
        }

        // Custom debug endpoints
        routing {
            route(config.endpointPrefix) {
                if (config.ram) get("ram") {
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

                if (config.cpu) get("cpu") {
                    val thread = ManagementFactory.getThreadMXBean()
                    context.respond(buildJsonObject {
                        put("loadAverage", ManagementFactory.getOperatingSystemMXBean().systemLoadAverage)
                        put("processors", ManagementFactory.getOperatingSystemMXBean().availableProcessors)
                        put("threadCount", JsonPrimitive(thread.threadCount))
                        put("peakThreadCount", JsonPrimitive(thread.threadCount))
                        put("daemonThreadCount", JsonPrimitive(thread.daemonThreadCount))
                    })
                }

                if (config.memoryPool) get("memoryPool") {
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
