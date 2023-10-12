package id.walt.oid4vc.data

import io.ktor.http.*
import io.ktor.util.*

interface IHTTPDataObject {
    fun toHttpParameters(): Map<String, List<String>>
    fun toHttpQueryString() = URLBuilder().apply {
        toHttpParameters()
            .flatMap { param -> param.value.map { Pair(param.key, it) } }
            .forEach { param ->
                parameters.append(param.first, param.second)
            }
    }.build().encodedQuery

    fun toRedirectUri(redirectUri: String, responseMode: ResponseMode) = URLBuilder(redirectUri).apply {
        when (responseMode) {
            ResponseMode.query -> parameters.appendAll(parametersOf(toHttpParameters()))
            ResponseMode.fragment -> fragment = toHttpQueryString()
            else -> throw Exception("For response via redirect_uri, response mode must be query or fragment")
        }
    }.buildString()
}

abstract class HTTPDataObject : IHTTPDataObject {
    abstract val customParameters: Map<String, List<String>>
    abstract override fun toHttpParameters(): Map<String, List<String>>
}

abstract class HTTPDataObjectFactory<T : HTTPDataObject> {
    abstract fun fromHttpParameters(parameters: Map<String, List<String>>): T
    fun fromHttpQueryString(query: String) = fromHttpParameters(
        parseQueryString(query).toMap()
    )
}
