package id.walt.did.dids.registrar.local.cheqd

import id.walt.core.crypto.keys.Key
import id.walt.core.crypto.keys.KeyType
import id.walt.core.crypto.utils.Base64Utils.base64toBase64Url
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.registrar.DidResult
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.did.dids.registrar.local.LocalRegistrarMethod
import id.walt.did.dids.registrar.local.cheqd.models.job.didstates.Secret
import id.walt.did.dids.registrar.local.cheqd.models.job.didstates.SigningResponse
import id.walt.did.dids.registrar.local.cheqd.models.job.didstates.action.ActionDidState
import id.walt.did.dids.registrar.local.cheqd.models.job.didstates.finished.FinishedDidState
import id.walt.did.dids.registrar.local.cheqd.models.job.request.JobCreateRequest
import id.walt.did.dids.registrar.local.cheqd.models.job.request.JobDeactivateRequest
import id.walt.did.dids.registrar.local.cheqd.models.job.request.JobSignRequest
import id.walt.did.dids.registrar.local.cheqd.models.job.response.JobActionResponse
import id.walt.did.dids.registrar.local.cheqd.models.job.response.didresponse.DidGetResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object CheqdService : LocalRegistrarMethod("cheqd") {

    private val log = KotlinLogging.logger { }
    private val client = HttpClient()

    private const val verificationMethod = "Ed25519VerificationKey2020"
    private const val methodSpecificIdAlgo = "uuid"

    //private const val registrarUrl = "https://registrar.walt.id/cheqd"
    private const val registrarUrl = "https://did-registrar.cheqd.net"
    private const val registrarApiVersion = "1.0"
    private const val didRegisterUrl = "$registrarUrl/$registrarApiVersion/create"
    private const val didDeactivateUrl = "$registrarUrl/$registrarApiVersion/deactivate"
    private const val didUpdateUrl = "$registrarUrl/$registrarApiVersion/update"

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json { explicitNulls = false }

    @OptIn(ExperimentalStdlibApi::class)
    suspend fun createDid(key: Key, network: String): DidDocument = let {
        if (key.keyType != KeyType.Ed25519) throw IllegalArgumentException("Key of type Ed25519 expected")
//        step#0. get public key hex
        val pubKeyHex = key.getPublicKeyRepresentation().toHexString()

//        step#1. fetch the did document from cheqd registrar
        val response = client.get(
            "$registrarUrl/$registrarApiVersion/did-document" +
                    "?verificationMethod=$verificationMethod" +
                    "&methodSpecificIdAlgo=$methodSpecificIdAlgo" +
                    "&network=$network" +
                    "&publicKeyHex=$pubKeyHex"
        ).bodyAsText()
//        step#2. onboard did with cheqd registrar
        Json { explicitNulls = false }.decodeFromString<DidGetResponse>(response)

        json.decodeFromString<DidGetResponse>(response).let {
//            step#2a. initialize
            val job = initiateDidJob(didRegisterUrl, json.encodeToJsonElement(JobCreateRequest(it.didDoc)))
                ?: throw Exception("Failed to initialize the did onboarding process")
//            step#2b. sign the serialized payload
            val signatures = signPayload(key, job)
//            step#2c. finalize
            val didDocument = (finalizeDidJob(
                didRegisterUrl,
                job.jobId,
                it.didDoc.verificationMethod.first().id, // TODO: associate verificationMethodId with signature
                signatures
            ).didState as? FinishedDidState)?.didDocument
                ?: throw IllegalArgumentException("Failed to finalize the did onboarding process")

            json.decodeFromString<DidDocument>(json.encodeToString(didDocument))
        }// ?: throw IllegalArgumentException("Failed to fetch the did document from cheqd registrar helper")
    }

    suspend fun deactivateDid(key: Key, did: String) {
        val job = initiateDidJob(didDeactivateUrl, json.encodeToJsonElement(JobDeactivateRequest(did)))
            ?: throw Exception("Failed to initialize the did onboarding process")
        val signatures = signPayload(key, job)
        val didDocument = (finalizeDidJob(
            didDeactivateUrl,
            job.jobId,
            "", // TODO: associate verificationMethodId with signature
            signatures
        ).didState as? FinishedDidState)?.didDocument
            ?: throw Exception("Failed to finalize the did onboarding process")
    }

    fun updateDid(did: String) {
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
            setBody(JobSignRequest(
                jobId = jobId,
                secret = Secret(
                    signingResponse = signatures.map {
                        SigningResponse(
                            signature = it.base64toBase64Url(),
                            verificationMethodId = verificationMethodId,
                        )
                    }
                )
            )
            )
        }.body<JobActionResponse>()
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun signPayload(key: Key, job: JobActionResponse): List<String> = let {
        val state = (job.didState as? ActionDidState) ?: throw IllegalArgumentException("Unexpected did state")
        val payloads = state.signingRequest.map {
            Base64.decode(it.serializedPayload)
        }
        // TODO: sign with key having alias from verification method

        //payloads.map { Base64.encode(key.sign(it)) }

        TODO("Raw signing")
        //payloads.map { Base64.encode(cryptoService.sign(keyId, it)) }
    }

    override suspend fun register(options: DidCreateOptions): DidResult {
        TODO("Not yet implemented")
    }

    override suspend fun registerByKey(key: Key, options: DidCreateOptions): DidResult {
        TODO("Not yet implemented")
    }

}
