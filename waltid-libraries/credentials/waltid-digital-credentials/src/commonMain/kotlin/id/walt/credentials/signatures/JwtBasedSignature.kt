package id.walt.credentials.signatures

import kotlinx.serialization.json.JsonObject

interface JwtBasedSignature {
    val jwtHeader: JsonObject?
}
