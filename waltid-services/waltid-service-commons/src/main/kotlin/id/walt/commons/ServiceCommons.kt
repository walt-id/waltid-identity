package id.walt.commons

import id.walt.commons.config.ConfigManager
import id.walt.commons.config.list.DevModeConfig
import id.walt.commons.config.statics.BuildConfig
import id.walt.commons.config.statics.ServiceConfig
import id.walt.commons.config.statics.ServiceConfig.serviceString
import id.walt.commons.featureflag.AbstractFeature
import id.walt.commons.featureflag.CommonsFeatureCatalog
import id.walt.commons.featureflag.FeatureManager
import id.walt.commons.featureflag.ServiceFeatureCatalog
import id.walt.did.dids.resolver.local.DidWebResolver
import io.klogging.Klogger
import io.klogging.logger
import kotlinx.coroutines.delay
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.time.measureTime
import java.lang.System.getProperty as p

enum class ProductType {
    OPEN_SOURCE,
    ENTERPRISE
}

private fun defaultSupportUrl(product: ProductType) = when (product) {
    ProductType.OPEN_SOURCE -> "https://walt.id/community"
    ProductType.ENTERPRISE -> "https://support.walt.id"
}

private fun defaultLicenseName(product: ProductType) = when (product) {
    ProductType.OPEN_SOURCE -> "Apache 2.0"
    ProductType.ENTERPRISE -> "Commercial License"
}

private fun defaultLicenseUrl(product: ProductType) = when (product) {
    ProductType.OPEN_SOURCE -> "https://www.apache.org/licenses/LICENSE-2.0.html"
    ProductType.ENTERPRISE -> "https://walt.id/enterprise-stack"
}

data class ServiceConfiguration(
    val name: String = "",
    val vendor: String = "walt.id",
    val version: String = BuildConfig.version,
    val product: ProductType = ProductType.OPEN_SOURCE,
    val supportUrl: String = defaultSupportUrl(product),
    val licenseName: String = defaultLicenseName(product),
    val licenseUrl: String = defaultLicenseUrl(product),
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
        "Running on ${p("os.arch")} ${p("os.name")} ${p("os.version")} with ${p("java.vm.name")} ${p("java.vm.version")} in path ${
            Path(
                "."
            ).absolutePathString()
        }"

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
            initDevMode(log)
        }.also {
            log.info { "Service initialization completed ($it)." }
        }
    }

    suspend fun initDevMode(log: Klogger) {
        if (FeatureManager.isFeatureEnabled("dev-mode")) {
            runCatching {
                DidWebResolver.enableHttps(ConfigManager.getConfig<DevModeConfig>().enableDidWebResolverHttps)
            }.onFailure {
                log.warn { "Feature `dev-mode` is enabled, but the configuration file: `dev-mode.conf` could not be loaded." }
            }
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
