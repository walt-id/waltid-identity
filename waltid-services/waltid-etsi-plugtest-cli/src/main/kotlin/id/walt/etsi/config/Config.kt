package id.walt.etsi.config

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addFileSource
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import kotlin.reflect.KClass

private val log = KotlinLogging.logger {}

data class EtsiConfig(
    val issuer: IssuerConfig = IssuerConfig(),
    val holder: HolderConfig = HolderConfig(),
    val output: OutputConfig = OutputConfig(),
    val sdjwt: SdJwtConfig = SdJwtConfig(),
    val mdoc: MdocConfig = MdocConfig()
)

data class IssuerConfig(
    val keyFile: String? = null,
    val keyJwk: String? = null,
    val certificateFile: String? = null,
    val certificatePem: String? = null,
    val url: String = "https://issuer.example.com"
)

data class HolderConfig(
    val keyFile: String? = null,
    val keyJwk: String? = null
)

data class OutputConfig(
    val directory: String = "output",
    val fileNamePattern: String = "{testCaseId}"
)

data class SdJwtConfig(
    val vct: String = "urn:etsi:eaa:credential",
    val vctIntegrityAlgorithm: String = "sha256"
)

data class MdocConfig(
    val defaultDocType: String = "org.etsi.eaa.1",
    val defaultNamespace: String = "org.etsi.01947201.010101"
)

object ConfigManager {

    private var loadedConfig: EtsiConfig? = null

    @OptIn(ExperimentalHoplite::class)
    fun <T : Any> loadConfig(configFile: File?, type: KClass<T>): T {
        val configPaths = buildList {
            if (configFile != null) {
                add(configFile)
            }
            add(File("config/etsi.conf"))
            add(File("etsi.conf"))
            add(File(System.getProperty("user.home"), ".config/waltid/etsi.conf"))
        }

        val existingConfig = configPaths.firstOrNull { it.exists() }

        val builder = ConfigLoaderBuilder.default()
            .withExplicitSealedTypes()

        if (existingConfig != null) {
            log.info { "Loading configuration from: ${existingConfig.absolutePath}" }
            builder.addFileSource(existingConfig)
        } else {
            log.debug { "No configuration file found, using defaults" }
        }

        return try {
            builder.build().loadConfigOrThrow(type, emptyList())
        } catch (e: Exception) {
            log.debug { "Could not load config, using defaults: ${e.message}" }
            type.java.getDeclaredConstructor().newInstance()
        }
    }

    fun loadConfig(configFile: File? = null): EtsiConfig {
        if (loadedConfig == null || configFile != null) {
            loadedConfig = loadConfig(configFile, EtsiConfig::class)
        }
        return loadedConfig!!
    }

    fun getConfig(): EtsiConfig = loadedConfig ?: loadConfig()

    fun preloadConfig(config: EtsiConfig) {
        loadedConfig = config
    }

    fun clearConfig() {
        loadedConfig = null
    }
}
