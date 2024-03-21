package id.walt.did.dids.registrar.local.cheqd

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.base64toBase64Url
import id.walt.did.dids.document.DidCheqdDocument
import id.walt.did.dids.registrar.DidResult
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.did.dids.registrar.local.LocalRegistrarMethod
import id.walt.did.dids.registrar.local.cheqd.models.job.didstates.Secret
import id.walt.did.dids.registrar.local.cheqd.models.job.didstates.SigningResponse
import id.walt.did.dids.registrar.local.cheqd.models.job.didstates.action.ActionDidState
import id.walt.did.dids.registrar.local.cheqd.models.job.didstates.didStateSerializationModule
import id.walt.did.dids.registrar.local.cheqd.models.job.didstates.failed.FailedDidState
import id.walt.did.dids.registrar.local.cheqd.models.job.didstates.finished.DidDocument
import id.walt.did.dids.registrar.local.cheqd.models.job.didstates.finished.FinishedDidState
import id.walt.did.dids.registrar.local.cheqd.models.job.request.JobCreateRequest
import id.walt.did.dids.registrar.local.cheqd.models.job.request.JobDeactivateRequest
import id.walt.did.dids.registrar.local.cheqd.models.job.request.JobSignRequest
import id.walt.did.dids.registrar.local.cheqd.models.job.response.JobActionResponse
import id.walt.did.dids.registrar.local.cheqd.models.job.response.didresponse.DidGetResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class DidCheqdRegistrar : LocalRegistrarMethod("cheqd") {

    private val log = KotlinLogging.logger { }

    private val verificationMethod = "Ed25519VerificationKey2020"
    private val methodSpecificIdAlgo = "uuid"

    //private const val registrarUrl = "https://registrar.walt.id/cheqd"
    private val registrarUrl = "https://did-registrar.cheqd.net"
    private val registrarApiVersion = "1.0"
    private val didRegisterUrl = "$registrarUrl/$registrarApiVersion/create"
    private val didDeactivateUrl = "$registrarUrl/$registrarApiVersion/deactivate"
    private val didUpdateUrl = "$registrarUrl/$registrarApiVersion/update"

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        serializersModule = didStateSerializationModule
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
        explicitNulls = false
    }

    //TODO: inject
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun register(options: DidCreateOptions): DidResult =
        registerByKey(JWKKey.generate(KeyType.Ed25519), options)

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun registerByKey(key: Key, options: DidCreateOptions): DidResult =
        createDid(key, options.get<String>("network") ?: "testnet").let {
            DidResult(it.id, id.walt.did.dids.document.DidDocument(DidCheqdDocument(it, key.exportJWKObject()).toMap()))
        }

    @OptIn(ExperimentalStdlibApi::class)
    private suspend fun createDid(key: Key, network: String): DidDocument = let {
        if (key.keyType != KeyType.Ed25519) throw IllegalArgumentException("Key of type Ed25519 expected")
        // step#0. get public key hex
        val pubKeyHex = key.getPublicKeyRepresentation().toHexString()
        // step#1. fetch the did document from cheqd registrar
        val response = client.get(
            "$registrarUrl/$registrarApiVersion/did-document" +
                    "?verificationMethod=$verificationMethod" +
                    "&methodSpecificIdAlgo=$methodSpecificIdAlgo" +
                    "&network=$network" +
                    "&publicKeyHex=$pubKeyHex"
        ).bodyAsText()
        // step#2. onboard did with cheqd registrar
        //TODO: handle error responses (have only a 'message' field)
        json.decodeFromString<DidGetResponse>(response).let { did ->
            // step#2a. initialize
            val job = initiateDidJob(didRegisterUrl, json.encodeToJsonElement(JobCreateRequest(did.didDoc)))
            // step#2b. sign the serialized payload
            val signatures = signPayload(key, job)
            // step#2c. finalize
            job.jobId?.let {
                // TODO: associate verificationMethodId with signature
                val didState =
                    finalizeDidJob(didRegisterUrl, it, did.didDoc.verificationMethod.first().id, signatures).didState
                (didState as? FinishedDidState)?.didDocument
                    ?: throw IllegalArgumentException("Failed to finalize the did onboarding process.\nCheqd registrar returning \"${(didState as FailedDidState).description}\"")
            } ?: throw Exception("Initialize job didn't return any jobId.")
        }
    }

    // TODO: finish implementation
    private suspend fun deactivateDid(key: Key, did: String) {
        val job = initiateDidJob(didDeactivateUrl, json.encodeToJsonElement(JobDeactivateRequest(did)))
        val signatures = signPayload(key, job)
        job.jobId?.let {
            // TODO: associate verificationMethodId with signature
            (finalizeDidJob(didDeactivateUrl, job.jobId, "", signatures).didState as? FinishedDidState)?.didDocument
                ?: throw Exception("Failed to finalize the did onboarding process")
        } ?: throw Exception("Initialize job didn't return any jobId.")
    }

    private fun updateDid(did: String) {
        did
        TODO()
    }

    private suspend fun initiateDidJob(url: String, body: JsonElement) =
        client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body<JobActionResponse>()

    private suspend fun finalizeDidJob(url: String, jobId: String, verificationMethodId: String, signatures: List<String>) = let {
        client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(
                JobSignRequest(
                    jobId = jobId, secret = Secret(signingResponse = signatures.map {
                        SigningResponse(
                            signature = it.base64toBase64Url(),
                            kid = verificationMethodId,
                        )
                    })
                )
            )
        }.body<JobActionResponse>()
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun signPayload(key: Key, job: JobActionResponse): List<String> = let {
        val state = (job.didState as? ActionDidState) ?: error("Unexpected did state")
        if (!state.action.equals("signPayload", true)) error("Unexpected state action: ${state.action}")
        val payloads = state.signingRequest.map {
            Base64.decode(it.serializedPayload)
        }
        // TODO: sign with key having alias from verification method

        payloads.map {
            Base64.encode(key.signRaw(it) as ByteArray)
        }
    }
}
