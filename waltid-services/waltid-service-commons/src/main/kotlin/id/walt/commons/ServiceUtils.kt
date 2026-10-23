package id.walt.commons

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

private const val MAX_FETCH_BYTES = 10L * 1024 * 1024

// AWS instance metadata over IPv6: a unique-local address, so not covered by isLinkLocalAddress.
private val AWS_METADATA_IPV6: InetAddress = InetAddress.getByName("fd00:ec2::254")

private val fetchClient = OkHttpClient.Builder()
    .followRedirects(false)
    .followSslRedirects(false)
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)
    .build()

/**
 * Fetches a binary file (e.g. a VICAL) from an `http` or `https` URL supplied by an API caller.
 *
 * Other schemes are rejected before any I/O. Redirects are not followed, the response is capped
 * at 10 MiB, and loopback, link-local (including cloud metadata), wildcard and multicast
 * destinations are refused at DNS resolution time, so the checked addresses are the ones connected to.
 *
 * @throws IllegalArgumentException if the URL is not allowed or the fetch fails
 */
fun fetchBinaryFile(vicalUrl: String): ByteArray? =
    fetchBinaryFile(vicalUrl, ::isBlockedFetchDestination, MAX_FETCH_BYTES)

internal fun isBlockedFetchDestination(address: InetAddress): Boolean =
    address.isLoopbackAddress || address.isLinkLocalAddress || address.isAnyLocalAddress ||
        address.isMulticastAddress || address == AWS_METADATA_IPV6

internal fun fetchBinaryFile(url: String, isBlockedAddress: (InetAddress) -> Boolean, maxBytes: Long): ByteArray {
    // OkHttp's parser accepts only http and https, and it is the parser the request is made with.
    val httpUrl = url.toHttpUrlOrNull() ?: throw fetchFailed(url, "only http and https URLs are allowed")
    val dns = DestinationCheckingDns(isBlockedAddress)

    return try {
        // OkHttp may skip DNS for IP literals, so check the host here as well.
        dns.lookup(httpUrl.host)
        val client = fetchClient.newBuilder().dns(dns).build()
        client.newCall(Request.Builder().url(httpUrl).build()).execute().use { response ->
            if (!response.isSuccessful) throw fetchFailed(url, "HTTP ${response.code}")
            if (response.body.contentLength() > maxBytes) throw fetchFailed(url, "response too large")
            val bytes = response.body.byteStream().use { it.readNBytes((maxBytes + 1).toInt()) }
            if (bytes.size > maxBytes) throw fetchFailed(url, "response too large")
            bytes
        }
    } catch (e: BlockedDestinationException) {
        throw fetchFailed(url, "destination not allowed", e)
    } catch (e: IOException) {
        throw fetchFailed(url, "request failed", e)
    }
}

private fun fetchFailed(url: String, reason: String, cause: Throwable? = null) =
    IllegalArgumentException("Failed to fetch binary file from $url: $reason", cause)

private class BlockedDestinationException(hostname: String) : UnknownHostException("Destination not allowed: $hostname")

private class DestinationCheckingDns(private val isBlockedAddress: (InetAddress) -> Boolean) : Dns {
    override fun lookup(hostname: String): List<InetAddress> =
        Dns.SYSTEM.lookup(hostname).also { addresses ->
            if (addresses.any(isBlockedAddress)) throw BlockedDestinationException(hostname)
        }
}
