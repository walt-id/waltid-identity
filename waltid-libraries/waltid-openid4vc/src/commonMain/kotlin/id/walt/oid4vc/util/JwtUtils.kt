package id.walt.oid4vc.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object JwtUtils {
  @OptIn(ExperimentalEncodingApi::class)
  fun parseJWTPayload(token: String): JsonObject {
    return token.substringAfter(".").substringBefore(".").let {
      Json.decodeFromString(Base64.UrlSafe.decode(it).decodeToString())
    }
  }

  @OptIn(ExperimentalEncodingApi::class)
  fun parseJWTHeader(token: String): JsonObject {
    return token.substringBefore(".").let {
      Json.decodeFromString(Base64.UrlSafe.decode(it).decodeToString())
    }
  }
}