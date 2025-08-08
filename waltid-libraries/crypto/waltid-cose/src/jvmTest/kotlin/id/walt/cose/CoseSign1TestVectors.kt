@file:OptIn(ExperimentalUuidApi::class, ExperimentalSerializationApi::class)

package id.walt.cose

import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class CoseSign1TestVectors {

    private val basePath = "test-vectors"

    companion object {
        private val outputHexFormat = HexFormat {
            bytes {
                bytesPerGroup = 1
            }
        }
    }

    @Serializable
    data class CoseTestVector(
        val uuid: Uuid,
        val title: String,
        val description: String,
        val key: JsonObject,
        val alg: String,
        @SerialName("sign1::sign")
        val signData: Sign1Sign? = null,
        @SerialName("sign1::verify")
        val verifyData: Sign1Verify? = null
    ) {
        val jwkKeyResult by lazy { runBlocking { JWKKey.importJWK(key.toString()) } }
        val jwkKey by lazy { jwkKeyResult.getOrThrow() }

        init {
            require(signData != null || verifyData != null) { "Test vector $uuid: Either sign or verify has to be given by the test vector" }
            require(signData == null || verifyData == null) { "Test vector $uuid: Only a single sign or a single verify can be given per test vector" }
            require(jwkKeyResult.isSuccess) { "Test vector $uuid: Invalid JWK key, cannot be imported." }
        }


        suspend fun checkTestVector(): Result<Unit> {
            val cbor = coseCompliantCbor
            when {
                signData != null -> {
                    println("Loading test vector data... -> $signData")

                    val protectedHeaders = cbor.decodeFromHexString<CoseHeaders>(signData.protectedHeaders.cborHex)
                    println("Protected headers: $protectedHeaders")
                    val unprotectedHeaders = cbor.decodeFromHexString<CoseHeaders>(signData.unprotectedHeaders.cborHex)
                    println("Unprotected headers: $protectedHeaders")
                    val payload = signData.payload.hexToByteArray()
                    println("Payload: ${payload.decodeToString()}")
                    val externalAad = signData.external?.hexToByteArray() ?: byteArrayOf()

                    println("External: ${externalAad.toHexString(outputHexFormat)}")
                    val signer = jwkKey.toCoseSigner(alg)

                    println()
                    println("Signing...")
                    val signed = CoseSign1.createAndSign(
                        protectedHeaders = protectedHeaders,
                        unprotectedHeaders = unprotectedHeaders,
                        payload = payload,
                        signer = signer,
                        externalAad = externalAad
                    )

                    val tbs = CoseSign1.makeToBeSigned(protectedHeaders, payload, externalAad)
                    println(
                        """
                        Generated TBS: ${tbs.dataToSign.toHexString()}
                        Expected  TBS: ${signData.tbsHex.cborHex} (= ${signData.tbsHex.cborDiag})
                    """.trimIndent()
                    )

                    val signedHex = signed.toTagged().toHexString()
                    val expectedHex = signData.expectedOutput.cborHex

                    println(
                        """
                        |
                        |Signed:   $signedHex
                        |Example:  $expectedHex (= ${signData.expectedOutput.cborDiag})
                        |
                    """.trimMargin()
                    )


                    val coseSelf = CoseSign1.fromTagged(signedHex)
                    val coseOther = CoseSign1.fromTagged(expectedHex)

                    println("""
                        Self: $coseSelf
                        Other: $coseOther
                    """.trimIndent())

                    val verifier = jwkKey.toCoseVerifier(alg)
                    val verifySelf = coseSelf.verify(verifier, externalAad)
                    val verifyOther = coseOther.verify(verifier, externalAad)

                    println("""
                        |Verify self:  $verifySelf
                        |Verify other: $verifyOther
                    """.trimMargin())

                    return when {
                        !verifyOther -> Result.failure(IllegalStateException("Could not verify other: $coseOther"))
                        !verifySelf -> Result.failure(IllegalStateException("Could not verify self: $coseSelf"))
                        else -> Result.success(Unit)
                    }
                }

                verifyData != null -> {
                    val tagged = verifyData.taggedCOSESign1.cborHex
                    println("Tagged: $tagged")

                    val signature = CoseSign1.fromTagged(tagged)
                    println("Parsed Signature: $signature")

                    val externalAad = verifyData.external?.hexToByteArray() ?: byteArrayOf()
                    println("External: ${externalAad.toHexString(outputHexFormat)}")

                    println("\nVerifying signature...")
                    val verified = signature.verify(jwkKey.toCoseVerifier(alg), externalAad)
                    println(
                        """
                        |
                        |Signature verified:   $verified
                        |Vector should verify: ${verifyData.shouldVerify}
                    """.trimMargin()
                    )

                    return if (verifyData.shouldVerify == verified) {
                        Result.success(Unit)
                    } else {
                        Result.failure(IllegalArgumentException("Vector should verify = ${verifyData.shouldVerify}, but signature was verified as = $verified!"))
                    }

                }
            }

            return Result.failure(IllegalArgumentException("Test vector $uuid: Either sign or verify is mandatory."))
        }


        @Serializable
        data class CborInputData(
            val cborHex: String,
            val cborDiag: String
        )

        @Serializable
        data class Sign1Sign(
            val payload: String,
            val protectedHeaders: CborInputData,
            val unprotectedHeaders: CborInputData,
            val tbsHex: CborInputData,
            val external: String? = null,
            val detached: Boolean,
            val expectedOutput: CborInputData,
            val fixedOutputLength: Int
        )

        @Serializable
        data class Sign1Verify(
            val taggedCOSESign1: CborInputData,
            val external: String? = null,
            val shouldVerify: Boolean
        )
    }

    fun loadTestVectors(): List<CoseTestVector> {
        val classLoader = this::class.java.classLoader

        return classLoader.getResourceAsStream(basePath)!!.bufferedReader().useLines { lines ->
            lines.filter { it.endsWith(".json") }
                .map { line -> classLoader.getResource("$basePath/$line")!!.readText() }
                .map { Json.decodeFromString<CoseTestVector>(it) }
                .toList()
        }
    }

    @Test
    fun checkTestVectors() = runTest {
        println("--- COSE Test vectors ---")

        println(
            """
            |The following test vectors used are provided by The Gluecose Community under the Apache 2 license.
            |> Gluecose is an open community nexus enabling COSE maintainers to automattically check for interoperability with other COSE libraries in a standardized fashion
            |https://github.com/gluecose/test-vectors
            |
        """.trimMargin()
        )

        println("Loading test vectors...")
        val testVectors = loadTestVectors()
        println("Loaded ${testVectors.size} test vectors.\n")
        require(testVectors.size >= 10) { "Some test vectors were not loaded?" }
        println("Test vector amount sane.")

        println("Testing ${testVectors.size} test vectors...")

        val results = testVectors.mapNumbered { idx, total, vector ->
            println(
                """
                |-----------------------------
                |TESTING COSE TEST VECTOR $idx/$total
                |Title:       ${vector.title}
                |UUID:        ${vector.uuid}
                |Description: ${vector.description}
                |Key:         ${vector.jwkKey}: ${vector.jwkKey.exportJWKPretty().replace("\n", " ").replace("    ", "")}
                |Alg:         ${vector.alg}
                |${if (vector.verifyData != null) "Verify:" else "Sign:  "}      ${vector.verifyData ?: vector.signData}
                |- - - - - - - - - - - - - -
            """.trimMargin()
            )
            val testVectorResult = vector.checkTestVector()

            println(
                """
                |- - - - - - - - - - - - - -
                | Test vector result: $testVectorResult
                |-----------------------------
                |
            """.trimMargin()
            )

            testVectorResult
        }
        println()

        val totalSuccess = results.count { it.isSuccess }
        val totalFailed = results.count { it.isFailure }

        println(
            """
            |====================
            |Test vector results:
            |Total test vectors success: $totalSuccess
            |Total test vectors failed:  $totalFailed
        """.trimMargin()
        )

        require(results.all { it.isSuccess }) { "Not all test vectors were successful." }
    }


}
