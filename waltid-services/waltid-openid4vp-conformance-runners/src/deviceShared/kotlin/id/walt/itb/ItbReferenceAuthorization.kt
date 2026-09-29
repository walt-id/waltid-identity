package id.walt.itb

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.Url

/** The deployed reference issuer authorizes synthetic test users with a direct redirect (VCI-001 verified). */
internal object ItbReferenceAuthorization {
    suspend fun resolve(client: HttpClient, origin: Url, authorization: Url, callback: Url): Url {
        require(authorization.protocol == origin.protocol && authorization.host == origin.host &&
            authorization.port == origin.port && authorization.user == null && authorization.password == null) {
            "Reference authorization endpoint is outside the configured ITB origin"
        }
        return client.config { followRedirects = false }.use { direct ->
            val response = direct.get(authorization)
            check(response.status.value in setOf(302, 303, 307, 308)) {
                "Reference authorization requires an unsupported interaction"
            }
            val destination = Url(requireNotNull(response.headers[HttpHeaders.Location]) {
                "Reference authorization has no redirect destination"
            })
            require(destination.protocol == callback.protocol && destination.host == callback.host &&
                destination.port == callback.port && destination.encodedPath.ifEmpty { "/" } == callback.encodedPath.ifEmpty { "/" } &&
                destination.user == null && destination.password == null && destination.fragment.isEmpty()) {
                "Reference authorization returned an unexpected callback destination"
            }
            // The wallet driver additionally validates the state and unique authorization code.
            destination
        }
    }
}
