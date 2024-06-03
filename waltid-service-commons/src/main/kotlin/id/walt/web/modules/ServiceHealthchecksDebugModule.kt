package id.walt.web.modules

import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.HealthCheckResult
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

    fun Application.enable() {
        install(Cohort) {
            endpointPrefix = "debug"
            if (logger.isTraceEnabled()) {
                operatingSystem = true
                //memory = true
                jvmInfo = true
                sysprops = true
                heapDump = true
                threadDump = true
                gc = true
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
