package id.walt.web.modules

import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.HealthCheckResult
import id.walt.config.ConfigManager
import io.klogging.logger
import io.ktor.server.application.*
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

    data class ServiceDebugModuleConfiguration(
        val endpointPrefix: String = "debug",
        val operatingSystem: Boolean = true,
        val memory: Boolean = false, // currently broken?
        val jvm: Boolean = true,
        val sysprops: Boolean = true,
        val heapdump: Boolean = true,
        val threaddump: Boolean = true,
        val gc: Boolean = true
    )

    fun Application.enable() {
        install(Cohort) {
            if (logger.isTraceEnabled()) {
                val config = ConfigManager.getConfig<ServiceDebugModuleConfiguration>()

                endpointPrefix = config.endpointPrefix
                operatingSystem = config.operatingSystem
                memory = config.memory
                jvmInfo = config.jvm
                sysprops = config.sysprops
                heapDump = config.heapdump
                threadDump = config.threaddump
                gc = config.gc
            }

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
    }
}
