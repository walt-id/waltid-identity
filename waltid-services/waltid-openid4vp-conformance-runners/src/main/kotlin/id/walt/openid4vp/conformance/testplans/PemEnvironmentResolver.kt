package id.walt.openid4vp.conformance.testplans

import java.nio.file.Files
import java.nio.file.Path

/**
 * Resolves PEM configuration supplied directly or through a file path.
 * Inline values take precedence to preserve existing local invocation behavior.
 */
internal fun resolvePemFromEnvironment(
    inlinePemEnvironmentVariable: String,
    pemFileEnvironmentVariable: String,
    environment: (String) -> String? = System::getenv,
): String? {
    environment(inlinePemEnvironmentVariable)?.ifBlank { null }?.let { return it }

    val pemFile = environment(pemFileEnvironmentVariable)?.ifBlank { null } ?: return null
    val path = Path.of(pemFile)
    require(Files.isRegularFile(path)) {
        "Cannot find PEM file configured by $pemFileEnvironmentVariable: $path"
    }

    return Files.readString(path).ifBlank {
        error("PEM file configured by $pemFileEnvironmentVariable is empty: $path")
    }
}
