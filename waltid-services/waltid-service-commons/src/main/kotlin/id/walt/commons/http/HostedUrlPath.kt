package id.walt.commons.http

import java.net.URI

/** Decoded path below a hosted base, comparing origins with scheme/host case and default ports. */
fun URI.relativeHostedPath(base: URI): String? {
    if (host == null || !scheme.equals(base.scheme, ignoreCase = true) ||
        !host.equals(base.host, ignoreCase = true) || effectivePort() != base.effectivePort()
    ) return null
    val prefix = base.normalize().path.trimEnd('/') + "/"
    return normalize().path?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)
}

private fun URI.effectivePort(): Int =
    if (port >= 0) port else if (scheme.equals("https", ignoreCase = true)) 443 else 80
