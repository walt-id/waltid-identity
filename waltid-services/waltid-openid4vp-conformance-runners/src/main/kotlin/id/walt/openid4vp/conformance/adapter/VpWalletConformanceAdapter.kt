package id.walt.openid4vp.conformance.adapter

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*

/**
 * Wallet Conformance Test Adapter
 * 
 * Bridges the OpenID conformance suite (verifier) with the walt.id wallet API.
 * 
 * The conformance suite expects a wallet to:
 * 1. Receive authorization requests via URL scheme (openid4vp://)
 * 2. Fetch the request from request_uri
 * 3. Process and generate VP response
 * 4. POST response to verifier's response_uri
 * 
 * This adapter simulates a wallet app by:
 * - Exposing an HTTP endpoint that conformance suite can invoke
 * - Fetching authorization requests from conformance suite
 * - Calling wallet API programmatically to process requests
 * - Sending responses back to conformance suite
 * 
 * This is TEST INFRASTRUCTURE, not a production wallet implementation.
 * Production wallets use mobile URL schemes, deep links, or Digital Credentials API.
 */
class VpWalletConformanceAdapter(
    private val walletApiUrl: String = "http://127.0.0.1:7005",
    private val adapterPort: Int = 7006,
    private val walletId: String = "conformance-test-wallet"
) {
    
    private var server: EmbeddedServer<*, *>? = null
    
    /**
     * Start the adapter server
     */
    fun start(httpClient: HttpClient) {
        println("[Adapter] Starting Wallet Conformance Adapter on port $adapterPort")
        println("[Adapter] Wallet API URL: $walletApiUrl")
        println("[Adapter] Using wallet ID: $walletId")
        
        server = embeddedServer(CIO, port = adapterPort) {
            routing {
                // Health check
                get("/health") {
                    call.respondText("Wallet Conformance Adapter is running")
                }
                
                // OpenID4VP authorization endpoint
                // This is what the conformance suite will call
                get("/openid4vp/authorize") {
                    handleAuthorizationRequest(call, httpClient)
                }
                
                // Alternative: handle as POST (some verifiers might POST)
                post("/openid4vp/authorize") {
                    handleAuthorizationRequest(call, httpClient)
                }
            }
        }.start(wait = false)
        
        println("[Adapter] Started successfully")
        println("[Adapter] Authorization endpoint: http://127.0.0.1:$adapterPort/openid4vp/authorize")
    }
    
    /**
     * Stop the adapter server
     */
    fun stop() {
        println("[Adapter] Stopping...")
        server?.stop(1000, 2000)
        server = null
    }
    
    /**
     * Handle authorization request from conformance suite
     */
    private suspend fun handleAuthorizationRequest(call: ApplicationCall, httpClient: HttpClient) {
        try {
            println("[Adapter] Received authorization request")
            
            // Extract parameters
            val requestUri = call.parameters["request_uri"]
            val request = call.parameters["request"]
            val clientId = call.parameters["client_id"]
            val responseUri = call.parameters["response_uri"]
            
            println("[Adapter] Parameters: request_uri=$requestUri, client_id=$clientId")
            
            // Get the authorization request
            val authRequest = when {
                requestUri != null -> {
                    println("[Adapter] Fetching request from request_uri: $requestUri")
                    fetchRequestFromUri(httpClient, requestUri)
                }
                request != null -> {
                    println("[Adapter] Using inline request parameter")
                    request
                }
                else -> {
                    call.respond(HttpStatusCode.BadRequest, "Missing request or request_uri parameter")
                    return
                }
            }
            
            println("[Adapter] Authorization request obtained (${authRequest.length} chars)")
            
            // Step 1: Resolve the request using wallet API
            println("[Adapter] Step 1: Resolving request with wallet API")
            val resolveResponse = httpClient.post("$walletApiUrl/wallet/$walletId/credentials/present/resolve-request") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("request", authRequest)
                })
            }
            
            if (!resolveResponse.status.isSuccess()) {
                val error = resolveResponse.bodyAsText()
                println("[Adapter] ERROR: Failed to resolve request: ${resolveResponse.status}")
                println("[Adapter] ERROR: $error")
                call.respond(HttpStatusCode.InternalServerError, "Failed to resolve request: $error")
                return
            }
            
            val resolveResult = resolveResponse.bodyAsText()
            println("[Adapter] Request resolved successfully")
            
            val resolveJson = Json.parseToJsonElement(resolveResult).jsonObject
            
            // Step 2: Match credentials
            println("[Adapter] Step 2: Matching credentials")
            val matchResponse = httpClient.post("$walletApiUrl/wallet/$walletId/credentials/present/match-credentials-from-store") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("request", authRequest)
                })
            }
            
            if (!matchResponse.status.isSuccess()) {
                val error = matchResponse.bodyAsText()
                println("[Adapter] ERROR: Failed to match credentials: ${matchResponse.status}")
                call.respond(HttpStatusCode.InternalServerError, "Failed to match credentials: $error")
                return
            }
            
            val matchResult = matchResponse.bodyAsText()
            println("[Adapter] Credentials matched successfully")
            
            val matchJson = Json.parseToJsonElement(matchResult).jsonObject
            val matchedCredentials = matchJson["credentials"]?.jsonArray
            
            if (matchedCredentials == null || matchedCredentials.isEmpty()) {
                println("[Adapter] ERROR: No credentials matched the request")
                call.respond(HttpStatusCode.BadRequest, "No credentials available to satisfy the request")
                return
            }
            
            // Step 3: Generate presentation
            println("[Adapter] Step 3: Generating VP presentation")
            val presentResponse = httpClient.post("$walletApiUrl/wallet/$walletId/credentials/present") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("request", authRequest)
                    put("selectedCredentials", matchedCredentials)
                })
            }
            
            if (!presentResponse.status.isSuccess()) {
                val error = presentResponse.bodyAsText()
                println("[Adapter] ERROR: Failed to generate presentation: ${presentResponse.status}")
                call.respond(HttpStatusCode.InternalServerError, "Failed to generate presentation: $error")
                return
            }
            
            val presentResult = presentResponse.bodyAsText()
            println("[Adapter] VP presentation generated successfully")
            
            val presentJson = Json.parseToJsonElement(presentResult).jsonObject
            
            // Step 4: Send response to verifier's response_uri
            val finalResponseUri = presentJson["response_uri"]?.jsonPrimitive?.content 
                ?: responseUri 
                ?: resolveJson["response_uri"]?.jsonPrimitive?.content
            
            if (finalResponseUri == null) {
                println("[Adapter] ERROR: No response_uri found")
                call.respond(HttpStatusCode.InternalServerError, "No response_uri in request or response")
                return
            }
            
            println("[Adapter] Step 4: Sending VP response to: $finalResponseUri")
            
            // Extract the VP response
            val vpToken = presentJson["vp_token"]?.jsonPrimitive?.content
            val presentationSubmission = presentJson["presentation_submission"]
            val state = resolveJson["state"]?.jsonPrimitive?.content
            
            // Build response form data
            val responseParams = Parameters.build {
                if (vpToken != null) append("vp_token", vpToken)
                if (presentationSubmission != null) append("presentation_submission", presentationSubmission.toString())
                if (state != null) append("state", state)
            }
            
            println("[Adapter] Sending response with params: ${responseParams.names()}")
            
            val verifierResponse = httpClient.post(finalResponseUri) {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(responseParams.formUrlEncode())
            }
            
            if (verifierResponse.status.isSuccess()) {
                println("[Adapter] VP response sent successfully to verifier")
                call.respondText("Presentation complete", status = HttpStatusCode.OK)
            } else {
                val error = verifierResponse.bodyAsText()
                println("[Adapter] ERROR: Failed to send response to verifier: ${verifierResponse.status}")
                call.respond(HttpStatusCode.InternalServerError, "Failed to send response to verifier: $error")
            }
            
        } catch (e: Exception) {
            println("[Adapter] ERROR: ${e.message}")
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
        }
    }
    
    /**
     * Fetch authorization request from request_uri
     */
    private suspend fun fetchRequestFromUri(httpClient: HttpClient, requestUri: String): String {
        println("[Adapter] Fetching request from URI: $requestUri")
        
        val response = httpClient.get(requestUri) {
            accept(ContentType.Any)
        }
        
        if (!response.status.isSuccess()) {
            val error = response.bodyAsText()
            println("[Adapter] ERROR: Failed to fetch request: ${response.status}")
            throw IllegalStateException("Failed to fetch request from $requestUri: ${response.status}")
        }
        
        val request = response.bodyAsText()
        println("[Adapter] Request fetched successfully (${request.length} chars)")
        return request
    }
}
