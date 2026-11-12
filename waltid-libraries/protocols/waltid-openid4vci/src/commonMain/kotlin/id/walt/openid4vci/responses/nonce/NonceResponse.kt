package id.walt.openid4vci.responses.nonce

import id.walt.openid4vci.responses.OpenId4VciResponseHeaders
import kotlinx.serialization.json.JsonElement

data class NonceResponseHttp(
    val status: Int,
    val payload: Map<String, JsonElement>,
    val headers: Map<String, String> = OpenId4VciResponseHeaders.NO_STORE,
)
