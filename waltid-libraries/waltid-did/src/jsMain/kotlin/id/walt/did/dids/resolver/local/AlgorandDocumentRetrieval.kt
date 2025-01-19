package id.walt.did.dids.resolver.local

import id.walt.did.dids.document.DidDocument
import kotlin.js.Json
import kotlinx.serialization.*
import kotlinx.serialization.json.*

actual fun fetchDidDocumentFromAlgorand(didIdentifier: String): DidDocument {
    console.log("Fetching DID Document from Algorand (JS) for ID: $didIdentifier")
    // For mock, you can return a simple JsonObject

    val jsonObject = buildJsonObject {
        put("id", "did:alg:$didIdentifier")  // JsonPrimitive for simple values
        put("verificationMethod", JsonArray(listOf()) )   // JsonPrimitive for number
        put("authentication", JsonArray(listOf())) // JsonPrimitive for boolean
    }

    return DidDocument(jsonObject)
}
