package id.walt.commons

import id.walt.commons.config.statics.BuildConfig
import id.walt.commons.config.statics.ServiceConfig
import id.walt.commons.config.statics.ServiceConfig.serviceString
import id.walt.commons.featureflag.AbstractFeature
import id.walt.commons.featureflag.CommonsFeatureCatalog
import id.walt.commons.featureflag.FeatureManager
import id.walt.commons.featureflag.ServiceFeatureCatalog
import io.klogging.logger
import kotlinx.coroutines.delay
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.time.measureTime
import java.lang.System.getProperty as p

data class ServiceConfiguration(
    val name: String = "",
    val vendor: String = "walt.id",
    val version: String = BuildConfig.version,
)

data class ServiceInitialization(
    val features: List<ServiceFeatureCatalog>,
    val featureAmendments: Map<AbstractFeature, suspend () -> Unit> = emptyMap(),
    val init: suspend () -> Unit,
    val run: suspend () -> Unit,
    val pre: (suspend () -> Unit)? = null
) {
    constructor(
        features: ServiceFeatureCatalog,
        featureAmendments: Map<AbstractFeature, suspend () -> Unit> = emptyMap(),
        init: suspend () -> Unit,
        run: suspend () -> Unit,
    ) : this(listOf(features), featureAmendments, init, run)
}

object ServiceCommons {

    private fun debugLineString(): String =
        "Running on ${p("os.arch")} ${p("os.name")} ${p("os.version")} with ${p("java.vm.name")} ${p("java.vm.version")} in path ${Path(".").absolutePathString()}"

    private suspend fun initFeatures(init: ServiceInitialization) {
        val log = logger("Feature-Init")

        log.info { "Initializing features..." }

        measureTime {
            log.info { "Registering common feature catalog..." }
            FeatureManager.registerCatalog(CommonsFeatureCatalog)
            log.info { "Registering service feature catalog..." }
            FeatureManager.registerCatalogs(init.features)

            log.info { "Loading features..." }
            FeatureManager.load(init.featureAmendments)
        }.also {
            log.info { "Feature initialization completed ($it)." }
        }
    }

    private suspend fun preloadService(init: ServiceInitialization) {
        if (init.pre != null) {
            val log = logger("Service-Preload")
            log.info { "Preloading $serviceString..." }

            measureTime {
                init.pre.invoke()
            }.also {
                log.info { "Service preloading completed ($it)." }
            }
        }
    }

    private suspend fun initService(init: ServiceInitialization) {
        val log = logger("Service-Init")

        log.info { "Initializing $serviceString..." }
        measureTime {
            init.init.invoke()
        }.also {
            log.info { "Service initialization completed ($it)." }
        }
    }

    private suspend fun runService(init: ServiceInitialization) {
        val log = logger("Service-Runner")

        log.info { "Running $serviceString..." }
        measureTime {
            init.run.invoke()
        }.also {
            log.info { "Service run completed ($it)." }
        }
    }

    suspend fun runService(
        config: ServiceConfiguration,
        init: ServiceInitialization,
    ) {
        ServiceConfig.config = config

        val log = logger(config.vendor + " " + config.name)
        log.info { "Starting $serviceString..." }

        log.debug { debugLineString() }

        measureTime {
            preloadService(init)
            initFeatures(init)
            initService(init)
            runService(init)
        }.also {
            log.info { "Service phases ended after $it." }
        }

        delay(50)
        log.info { "Service $serviceString done." }
    }

}
