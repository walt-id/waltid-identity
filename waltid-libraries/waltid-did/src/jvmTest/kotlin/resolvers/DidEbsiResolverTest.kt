package resolvers

import id.walt.crypto.keys.Key
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.resolver.local.DidEbsiResolver
import id.walt.did.dids.resolver.local.LocalResolverMethod
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.security.cert.X509Certificate
import java.util.stream.Stream
import javax.net.ssl.X509TrustManager

class DidEbsiResolverTest : DidResolverTestBase() {
    override val resolver: LocalResolverMethod =
        DidEbsiResolver(HttpClient(CIO) {
            engine {
                https {
                    //disable https certificate verification
                    trustManager = object : X509TrustManager {
                        override fun checkClientTrusted(
                            chain: Array<out X509Certificate?>?,
                            authType: String?
                        ) {
                        }

                        override fun checkServerTrusted(
                            chain: Array<out X509Certificate?>?,
                            authType: String?
                        ) {
                        }

                        override fun getAcceptedIssuers(): Array<out X509Certificate?>? = null
                    }
                }
            }
        })


    // TODO: Include test in the scope of WAL-842
    @ParameterizedTest
    @MethodSource
    override fun `given a did String, when calling resolve, then the result is a valid did document`(
        did: String, key: JsonObject, resolverAssertion: resolverAssertion<DidDocument>
    ) {
        super.`given a did String, when calling resolve, then the result is a valid did document`(did, key, resolverAssertion)
    }


    // TODO: Include test in the scope of WAL-842
    @ParameterizedTest
    @MethodSource
    override fun `given a did String, when calling resolveToKey, then the result is valid key`(
        did: String,
        key: JsonObject,
        resolverAssertion: resolverAssertion<Key>
    ) {
        super.`given a did String, when calling resolveToKey, then the result is valid key`(did, key, resolverAssertion)
    }

    companion object {

        @JvmStatic
        fun `given a did String, when calling resolve, then the result is a valid did document`(): Stream<Arguments> =
            (conformanceEnvDidList + pilotEnvDidList).stream().flatMap {
                val curDid = it.did
                it.expectedDocValidAssertionList.map { innerIt ->
                    arguments(curDid, innerIt.first, innerIt.second)
                }.stream()
            }

        @JvmStatic
        fun `given a did String, when calling resolveToKey, then the result is valid key`(): Stream<Arguments> =
            (conformanceEnvDidList + pilotEnvDidList).stream()
                .map { arguments(it.did, it.expectedKeyValidAssertion.first, it.expectedKeyValidAssertion.second) }

        data class TestEntry(
            val did: String,
            val expectedDidDoc: JsonObject,
            val expectedDocValidAssertionList: List<Pair<JsonObject, resolverAssertion<DidDocument>>>,
            val expectedKeyValidAssertion: Pair<JsonObject, resolverAssertion<Key>>,
        )


        private val pilotEnvDidList: List<TestEntry> = listOf(
            TestEntry(
                did = "did:ebsi:zfkNjYrzvx3bQn2SRyDZDMC",
                expectedDidDoc = Json.decodeFromString<JsonObject>(
                    """
                                {
                                    "@context": [
                                        "https://www.w3.org/ns/did/v1",
                                        "https://w3id.org/security/suites/jws-2020/v1"
                                    ],
                                    "controller": [
                                        "did:ebsi:zfkNjYrzvx3bQn2SRyDZDMC"
                                    ],
                                    "id": "did:ebsi:zfkNjYrzvx3bQn2SRyDZDMC",
                                    "verificationMethod": [
                                        {
                                            "controller": "did:ebsi:zfkNjYrzvx3bQn2SRyDZDMC",
                                            "id": "did:ebsi:zfkNjYrzvx3bQn2SRyDZDMC#_1EonbYXjUuhLeCBZAqfdwGpHlfsKXSC63FCt4SBZGU",
                                            "publicKeyJwk": {
                                                "crv": "secp256k1",
                                                "kty": "EC",
                                                "x": "0CZIbkz6Z1f4r0rB1vRe1bhh6_cZM5hjvcOnsLo3k5w",
                                                "y": "t67sxzXan_wQE--9dQB83kdA3Q-BGOH35_KkHkm2LlQ"
                                            },
                                            "type": "JsonWebKey2020"
                                        }
                                    ],
                                    "authentication": [
                                        "did:ebsi:zfkNjYrzvx3bQn2SRyDZDMC#_1EonbYXjUuhLeCBZAqfdwGpHlfsKXSC63FCt4SBZGU"
                                    ],
                                    "capabilityInvocation": [
                                        "did:ebsi:zfkNjYrzvx3bQn2SRyDZDMC#_1EonbYXjUuhLeCBZAqfdwGpHlfsKXSC63FCt4SBZGU"
                                    ]
                                }
                                """
                ),
                expectedDocValidAssertionList = listOf(
                    Pair(
                        Json.decodeFromString<JsonObject>(
                            """
                                        {
                                            "kty": "EC",
                                            "crv": "secp256k1",
                                            "x": "0CZIbkz6Z1f4r0rB1vRe1bhh6_cZM5hjvcOnsLo3k5w",
                                            "y": "t67sxzXan_wQE--9dQB83kdA3Q-BGOH35_KkHkm2LlQ"
                                        }
                                    """
                        ),
                        secp256DidAssertions
                    )
                ),
                expectedKeyValidAssertion = Pair(
                    Json.decodeFromString<JsonObject>(
                        """
                                        {
                                            "kty": "EC",
                                            "crv": "secp256k1",
                                            "x": "0CZIbkz6Z1f4r0rB1vRe1bhh6_cZM5hjvcOnsLo3k5w",
                                            "y": "t67sxzXan_wQE--9dQB83kdA3Q-BGOH35_KkHkm2LlQ"
                                        }
                                    """
                    ),
                    secp256KeyAssertions
                )
            ),
            TestEntry(
                did = "did:ebsi:zbgTSaAUD6YY1jLXAuU8rsV",
                expectedDidDoc = Json.decodeFromString<JsonObject>(
                    """
                                {
                                    "@context": [
                                        "https://www.w3.org/ns/did/v1",
                                        "https://w3id.org/security/suites/jws-2020/v1"
                                    ],
                                    "controller": [
                                        "did:ebsi:zbgTSaAUD6YY1jLXAuU8rsV"
                                    ],
                                    "id": "did:ebsi:zbgTSaAUD6YY1jLXAuU8rsV",
                                    "verificationMethod": [
                                        {
                                            "controller": "did:ebsi:zbgTSaAUD6YY1jLXAuU8rsV",
                                            "id": "did:ebsi:zbgTSaAUD6YY1jLXAuU8rsV#rctPc88bcxyb0_dAQwn3GNPhKOdPyoVugqglMu-g2cw",
                                            "publicKeyJwk": {
                                                "crv": "secp256k1",
                                                "kty": "EC",
                                                "x": "cReVHVGbQAe-HwY7lmCWAge4nXMKU2uPMv8GH6kYqks",
                                                "y": "OMsUq84wMOlV5T_bHZTJ59spiPwWFdtj9BRAq7umNb8"
                                            },
                                            "type": "JsonWebKey2020"
                                        }
                                    ],
                                    "authentication": [
                                        "did:ebsi:zbgTSaAUD6YY1jLXAuU8rsV#rctPc88bcxyb0_dAQwn3GNPhKOdPyoVugqglMu-g2cw"
                                    ],
                                    "capabilityInvocation": [
                                        "did:ebsi:zbgTSaAUD6YY1jLXAuU8rsV#rctPc88bcxyb0_dAQwn3GNPhKOdPyoVugqglMu-g2cw"
                                    ]
                                }
                            """
                ),
                expectedDocValidAssertionList = listOf(
                    Pair(
                        Json.decodeFromString<JsonObject>(
                            """
                                        {
                                            "kty": "EC",
                                            "crv": "secp256k1",
                                            "x": "cReVHVGbQAe-HwY7lmCWAge4nXMKU2uPMv8GH6kYqks",
                                            "y": "OMsUq84wMOlV5T_bHZTJ59spiPwWFdtj9BRAq7umNb8"
                                        }
                                    """
                        ),
                        secp256DidAssertions
                    )
                ),
                expectedKeyValidAssertion = Pair(
                    Json.decodeFromString<JsonObject>(
                        """
                                    {
                                        "kty": "EC",
                                        "crv": "secp256k1",
                                        "x": "cReVHVGbQAe-HwY7lmCWAge4nXMKU2uPMv8GH6kYqks",
                                        "y": "OMsUq84wMOlV5T_bHZTJ59spiPwWFdtj9BRAq7umNb8"
                                    }
                                """
                    ),
                    secp256KeyAssertions
                )
            ),
            TestEntry(
                did = "did:ebsi:zxJUDi1QZP2pCEScZn8Rbpe",
                expectedDidDoc = Json.decodeFromString<JsonObject>(
                    """
                                {
                                    "@context": [
                                        "https://www.w3.org/ns/did/v1",
                                        "https://w3id.org/security/suites/jws-2020/v1"
                                    ],
                                    "controller": [
                                        "did:ebsi:zxJUDi1QZP2pCEScZn8Rbpe"
                                    ],
                                    "id": "did:ebsi:zxJUDi1QZP2pCEScZn8Rbpe",
                                    "verificationMethod": [
                                        {
                                            "controller": "did:ebsi:zxJUDi1QZP2pCEScZn8Rbpe",
                                            "id": "did:ebsi:zxJUDi1QZP2pCEScZn8Rbpe#Px_5vvTQ0jDqYn_uGcBQ3sigq6tCSjaFJTRSFSKRikI",
                                            "publicKeyJwk": {
                                                "crv": "secp256k1",
                                                "kty": "EC",
                                                "x": "feQMOw29vsMKNaFRf_ZsAHe8-k2o_wqL_38m_h0FqVQ",
                                                "y": "hLc735ZPGKc_WlgqT8r7iAbk-8dekL5pU6fHuhZjFOA"
                                            },
                                            "type": "JsonWebKey2020"
                                        }
                                    ],
                                    "authentication": [
                                        "did:ebsi:zxJUDi1QZP2pCEScZn8Rbpe#Px_5vvTQ0jDqYn_uGcBQ3sigq6tCSjaFJTRSFSKRikI"
                                    ],
                                    "capabilityInvocation": [
                                        "did:ebsi:zxJUDi1QZP2pCEScZn8Rbpe#Px_5vvTQ0jDqYn_uGcBQ3sigq6tCSjaFJTRSFSKRikI"
                                    ]
                                }
                            """
                ),
                expectedDocValidAssertionList = listOf(
                    Pair(
                        Json.decodeFromString<JsonObject>(
                            """
                                        {
                                            "kty": "EC",
                                            "crv": "secp256k1",
                                            "x": "feQMOw29vsMKNaFRf_ZsAHe8-k2o_wqL_38m_h0FqVQ",
                                            "y": "hLc735ZPGKc_WlgqT8r7iAbk-8dekL5pU6fHuhZjFOA"
                                        }
                                     """
                        ),
                        secp256DidAssertions
                    )
                ),
                expectedKeyValidAssertion = Pair(
                    Json.decodeFromString<JsonObject>(
                        """
                                    {
                                        "kty": "EC",
                                        "crv": "secp256k1",
                                        "x": "feQMOw29vsMKNaFRf_ZsAHe8-k2o_wqL_38m_h0FqVQ",
                                        "y": "hLc735ZPGKc_WlgqT8r7iAbk-8dekL5pU6fHuhZjFOA"
                                    }
                                 """
                    ),
                    secp256KeyAssertions
                )
            ),
            TestEntry(
                did = "did:ebsi:zreDnmXLU5HoQc7HWjkmNkw",
                expectedDidDoc = Json.decodeFromString<JsonObject>(
                    """
                                {
                                    "@context": [
                                        "https://www.w3.org/ns/did/v1",
                                        "https://w3id.org/security/suites/jws-2020/v1"
                                    ],
                                    "controller": [
                                        "did:ebsi:zreDnmXLU5HoQc7HWjkmNkw"
                                    ],
                                    "id": "did:ebsi:zreDnmXLU5HoQc7HWjkmNkw",
                                    "verificationMethod": [
                                        {
                                            "controller": "did:ebsi:zreDnmXLU5HoQc7HWjkmNkw",
                                            "id": "did:ebsi:zreDnmXLU5HoQc7HWjkmNkw#_uHNt0NoqJPtLKWhsLCN4ySHGH2V2VDcdJZmwgTmvJY",
                                            "publicKeyJwk": {
                                                "crv": "secp256k1",
                                                "kty": "EC",
                                                "x": "Itfz9Io7G0CglQFQnupi4CyFnF0rPfRtGLhUJkIrMkY",
                                                "y": "c7dEXQtdgvcxSv_O6sqWplznkNE5C4SjCyiVrlaA_xM"
                                            },
                                            "type": "JsonWebKey2020"
                                        }
                                    ],
                                    "authentication": [
                                        "did:ebsi:zreDnmXLU5HoQc7HWjkmNkw#_uHNt0NoqJPtLKWhsLCN4ySHGH2V2VDcdJZmwgTmvJY"
                                    ],
                                    "capabilityInvocation": [
                                        "did:ebsi:zreDnmXLU5HoQc7HWjkmNkw#_uHNt0NoqJPtLKWhsLCN4ySHGH2V2VDcdJZmwgTmvJY"
                                    ]
                                }
                            """
                ),
                expectedDocValidAssertionList = listOf(
                    Pair(
                        Json.decodeFromString<JsonObject>(
                            """
                                        {
                                            "kty": "EC",
                                            "crv": "secp256k1",
                                            "x": "Itfz9Io7G0CglQFQnupi4CyFnF0rPfRtGLhUJkIrMkY",
                                            "y": "c7dEXQtdgvcxSv_O6sqWplznkNE5C4SjCyiVrlaA_xM"
                                        }
                                      """
                        ),
                        secp256DidAssertions
                    )
                ),
                expectedKeyValidAssertion = Pair(
                    Json.decodeFromString<JsonObject>(
                        """
                                    {
                                        "kty": "EC",
                                        "crv": "secp256k1",
                                        "x": "Itfz9Io7G0CglQFQnupi4CyFnF0rPfRtGLhUJkIrMkY",
                                        "y": "c7dEXQtdgvcxSv_O6sqWplznkNE5C4SjCyiVrlaA_xM"
                                    }
                                 """
                    ),
                    secp256KeyAssertions
                )
            ),
            TestEntry(
                did = "did:ebsi:zjysjDAy9ejkpZ2HdkwNw4k",
                expectedDidDoc = Json.decodeFromString<JsonObject>(
                    """
                                {
                                    "@context": [
                                        "https://www.w3.org/ns/did/v1",
                                        "https://w3id.org/security/suites/jws-2020/v1"
                                    ],
                                    "controller": [
                                        "did:ebsi:zjysjDAy9ejkpZ2HdkwNw4k"
                                    ],
                                    "id": "did:ebsi:zjysjDAy9ejkpZ2HdkwNw4k",
                                    "verificationMethod": [
                                        {
                                            "controller": "did:ebsi:zjysjDAy9ejkpZ2HdkwNw4k",
                                            "id": "did:ebsi:zjysjDAy9ejkpZ2HdkwNw4k#Mh3r9LWuVCYDiJ20VEH6aVwaEv1uSi5NXVCJ6IbhfEE",
                                            "publicKeyJwk": {
                                                "crv": "secp256k1",
                                                "kty": "EC",
                                                "x": "BSj1V7lwxljxkj91hvLrGYj5_GY116tt3zbG8u1vhac",
                                                "y": "ZcFLzFzjRFbJz6sUniBfzWjx0k4vL012xIPISYCZGJA"
                                            },
                                            "type": "JsonWebKey2020"
                                        }
                                    ],
                                    "authentication": [
                                        "did:ebsi:zjysjDAy9ejkpZ2HdkwNw4k#Mh3r9LWuVCYDiJ20VEH6aVwaEv1uSi5NXVCJ6IbhfEE"
                                    ],
                                    "capabilityInvocation": [
                                        "did:ebsi:zjysjDAy9ejkpZ2HdkwNw4k#Mh3r9LWuVCYDiJ20VEH6aVwaEv1uSi5NXVCJ6IbhfEE"
                                    ]
                                }
                               """
                ),
                expectedDocValidAssertionList = listOf(
                    Pair(
                        Json.decodeFromString<JsonObject>(
                            """
                                        {
                                            "kty": "EC",
                                            "crv": "secp256k1",
                                            "x": "BSj1V7lwxljxkj91hvLrGYj5_GY116tt3zbG8u1vhac",
                                            "y": "ZcFLzFzjRFbJz6sUniBfzWjx0k4vL012xIPISYCZGJA"
                                        }
                                     """
                        ),
                        secp256DidAssertions
                    )
                ),
                expectedKeyValidAssertion = Pair(
                    Json.decodeFromString<JsonObject>(
                        """
                                    {
                                        "kty": "EC",
                                        "crv": "secp256k1",
                                        "x": "BSj1V7lwxljxkj91hvLrGYj5_GY116tt3zbG8u1vhac",
                                        "y": "ZcFLzFzjRFbJz6sUniBfzWjx0k4vL012xIPISYCZGJA"
                                    }
                                """
                    ),
                    secp256KeyAssertions
                )
            )
        )

        private val conformanceEnvDidList: List<TestEntry> = listOf(
            TestEntry(
                "did:ebsi:z21Bs13TqhZV7RY727hX22XF",
                Json.decodeFromString<JsonObject>(
                    "{\"@context\":[\"https://www.w3.org/ns/did/v1\",\"https://w3id.org/security/suites/jws-2020/v1\"],\"id\":\"did:ebsi:z21Bs13TqhZV7RY727hX22XF\",\"controller\":[\"did:ebsi:z21Bs13TqhZV7RY727hX22XF\"],\"verificationMethod\":[{\"id\":\"did:ebsi:z21Bs13TqhZV7RY727hX22XF#_bQu28sgqr1qnjSjJEKBGnRDilhlz7AtYYp5mMg83r0\",\"type\":\"JsonWebKey2020\",\"controller\":\"did:ebsi:z21Bs13TqhZV7RY727hX22XF\",\"publicKeyJwk\":{\"kty\":\"EC\",\"crv\":\"secp256k1\",\"x\":\"dHVH5LYmdPKXb0o9CqgUJIaqHe0qC1OWQKXb_6jg2Dg\",\"y\":\"BDsY_JM9UGaTfuHcXR9wD4j8ExEnh3J4_MFIuTyOyHU\"}}],\"authentication\":[\"did:ebsi:z21Bs13TqhZV7RY727hX22XF#_bQu28sgqr1qnjSjJEKBGnRDilhlz7AtYYp5mMg83r0\"],\"capabilityInvocation\":[\"did:ebsi:z21Bs13TqhZV7RY727hX22XF#_bQu28sgqr1qnjSjJEKBGnRDilhlz7AtYYp5mMg83r0\"]}"
                ),
                listOf(
                    Pair(
                        Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"secp256k1\",\"x\":\"dHVH5LYmdPKXb0o9CqgUJIaqHe0qC1OWQKXb_6jg2Dg\",\"y\":\"BDsY_JM9UGaTfuHcXR9wD4j8ExEnh3J4_MFIuTyOyHU\"}"),
                        secp256DidAssertions,
                    ),
                ),
                Pair(
                    Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"secp256k1\",\"x\":\"dHVH5LYmdPKXb0o9CqgUJIaqHe0qC1OWQKXb_6jg2Dg\",\"y\":\"BDsY_JM9UGaTfuHcXR9wD4j8ExEnh3J4_MFIuTyOyHU\"}"),
                    secp256KeyAssertions,
                ),
            ),
            TestEntry(
                "did:ebsi:zj46t2gXPgmdriraZG6mb5A",
                Json.decodeFromString<JsonObject>(
                    "{\"@context\":[\"https://www.w3.org/ns/did/v1\",\"https://w3id.org/security/suites/jws-2020/v1\"],\"id\":\"did:ebsi:zj46t2gXPgmdriraZG6mb5A\",\"controller\":[\"did:ebsi:zj46t2gXPgmdriraZG6mb5A\"],\"verificationMethod\":[{\"id\":\"did:ebsi:zj46t2gXPgmdriraZG6mb5A#7-1eNcG4MFkxahS1m85zcyu9ey_zOAl_fxvSPtI5Q2k\",\"type\":\"JsonWebKey2020\",\"controller\":\"did:ebsi:zj46t2gXPgmdriraZG6mb5A\",\"publicKeyJwk\":{\"kty\":\"EC\",\"crv\":\"secp256k1\",\"x\":\"u0LAULtFwOQwusN8esicYCz579culZW5htoPC_ADha4\",\"y\":\"mYnbYZH4iyvlyMWuXE-X2ssebVE7VC9aW3wPxkU9wdQ\"}},{\"id\":\"did:ebsi:zj46t2gXPgmdriraZG6mb5A#awSc2nG7WGNGw9YDtnVZ5HPOjeIhC8eb8UDOr3QV1Fc\",\"type\":\"JsonWebKey2020\",\"controller\":\"did:ebsi:zj46t2gXPgmdriraZG6mb5A\",\"publicKeyJwk\":{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"mDX7h2ZIOL1993wj4H83_jg67d_TaYk3dvVL447rRC0\",\"y\":\"Lr-efgLN1QPv-rb2sk-4gv7cVwXpzTY6FE8kdNHUUwU\"}}],\"authentication\":[\"did:ebsi:zj46t2gXPgmdriraZG6mb5A#7-1eNcG4MFkxahS1m85zcyu9ey_zOAl_fxvSPtI5Q2k\",\"did:ebsi:zj46t2gXPgmdriraZG6mb5A#awSc2nG7WGNGw9YDtnVZ5HPOjeIhC8eb8UDOr3QV1Fc\"],\"assertionMethod\":[\"did:ebsi:zj46t2gXPgmdriraZG6mb5A#awSc2nG7WGNGw9YDtnVZ5HPOjeIhC8eb8UDOr3QV1Fc\",\"did:ebsi:zj46t2gXPgmdriraZG6mb5A#7-1eNcG4MFkxahS1m85zcyu9ey_zOAl_fxvSPtI5Q2k\"],\"capabilityInvocation\":[\"did:ebsi:zj46t2gXPgmdriraZG6mb5A#7-1eNcG4MFkxahS1m85zcyu9ey_zOAl_fxvSPtI5Q2k\"]}"
                ),
                listOf(
                    Pair(
                        Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"secp256k1\",\"x\":\"u0LAULtFwOQwusN8esicYCz579culZW5htoPC_ADha4\",\"y\":\"mYnbYZH4iyvlyMWuXE-X2ssebVE7VC9aW3wPxkU9wdQ\"}"),
                        secp256DidAssertions,
                    ),
                    Pair(
                        Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"mDX7h2ZIOL1993wj4H83_jg67d_TaYk3dvVL447rRC0\",\"y\":\"Lr-efgLN1QPv-rb2sk-4gv7cVwXpzTY6FE8kdNHUUwU\"}"),
                        secp256DidAssertions
                    ),
                ),
                Pair(
                    Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"mDX7h2ZIOL1993wj4H83_jg67d_TaYk3dvVL447rRC0\",\"y\":\"Lr-efgLN1QPv-rb2sk-4gv7cVwXpzTY6FE8kdNHUUwU\"}"),
                    secp256KeyAssertions,
                ),
            ),
            TestEntry(
                "did:ebsi:zjHZjJ4Sy7r92BxXzFGs7qD",
                Json.decodeFromString<JsonObject>(
                    "{\"@context\":[\"https://www.w3.org/ns/did/v1\",\"https://w3id.org/security/suites/jws-2020/v1\"],\"id\":\"did:ebsi:zjHZjJ4Sy7r92BxXzFGs7qD\",\"controller\":[\"did:ebsi:zjHZjJ4Sy7r92BxXzFGs7qD\"],\"verificationMethod\":[{\"id\":\"did:ebsi:zjHZjJ4Sy7r92BxXzFGs7qD#zWNYsdkpxcq6wMfNGhsB7ns3sSOT5yCtrc4gBxHG-Mc\",\"type\":\"JsonWebKey2020\",\"controller\":\"did:ebsi:zjHZjJ4Sy7r92BxXzFGs7qD\",\"publicKeyJwk\":{\"kty\":\"EC\",\"crv\":\"secp256k1\",\"x\":\"4TgiV8Fk3nG4At4ija1KQU_1N_DrI7STwyHBYnXxk7U\",\"y\":\"8wrHJAN1hdVGmXIoGnuqOIhnFC4PzKgCsHIWM50Jxwc\"}},{\"id\":\"did:ebsi:zjHZjJ4Sy7r92BxXzFGs7qD#T6iPMW-k8O4uwZid29GwLe-Njg40E6jNT7hdLpJ3ZSg\",\"type\":\"JsonWebKey2020\",\"controller\":\"did:ebsi:zjHZjJ4Sy7r92BxXzFGs7qD\",\"publicKeyJwk\":{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"AvOFLJgwIftVMBmW0Adfw1vZ6_f5rKz2Q9Il8tMyYv4\",\"y\":\"ZGynk89Jz8qKKP3qTlVm-w6LWc895B6__JbS4ldAQio\"}}],\"authentication\":[\"did:ebsi:zjHZjJ4Sy7r92BxXzFGs7qD#zWNYsdkpxcq6wMfNGhsB7ns3sSOT5yCtrc4gBxHG-Mc\",\"did:ebsi:zjHZjJ4Sy7r92BxXzFGs7qD#T6iPMW-k8O4uwZid29GwLe-Njg40E6jNT7hdLpJ3ZSg\"],\"assertionMethod\":[\"did:ebsi:zjHZjJ4Sy7r92BxXzFGs7qD#T6iPMW-k8O4uwZid29GwLe-Njg40E6jNT7hdLpJ3ZSg\"],\"capabilityInvocation\":[\"did:ebsi:zjHZjJ4Sy7r92BxXzFGs7qD#zWNYsdkpxcq6wMfNGhsB7ns3sSOT5yCtrc4gBxHG-Mc\"]}"
                ),
                listOf(
                    Pair(
                        Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"secp256k1\",\"x\":\"4TgiV8Fk3nG4At4ija1KQU_1N_DrI7STwyHBYnXxk7U\",\"y\":\"8wrHJAN1hdVGmXIoGnuqOIhnFC4PzKgCsHIWM50Jxwc\"}"),
                        secp256DidAssertions,
                    ),
                    Pair(
                        Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"AvOFLJgwIftVMBmW0Adfw1vZ6_f5rKz2Q9Il8tMyYv4\",\"y\":\"ZGynk89Jz8qKKP3qTlVm-w6LWc895B6__JbS4ldAQio\"}"),
                        secp256DidAssertions
                    ),
                ),
                Pair(
                    Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"AvOFLJgwIftVMBmW0Adfw1vZ6_f5rKz2Q9Il8tMyYv4\",\"y\":\"ZGynk89Jz8qKKP3qTlVm-w6LWc895B6__JbS4ldAQio\"}"),
                    secp256KeyAssertions,
                ),
            ),
            TestEntry(
                "did:ebsi:zuzDMJqHyxtCEE6FRuCPTgD",
                Json.decodeFromString<JsonObject>(
                    "{\"@context\":[\"https://www.w3.org/ns/did/v1\",\"https://w3id.org/security/suites/jws-2020/v1\"],\"id\":\"did:ebsi:zuzDMJqHyxtCEE6FRuCPTgD\",\"controller\":[\"did:ebsi:zuzDMJqHyxtCEE6FRuCPTgD\"],\"verificationMethod\":[{\"id\":\"did:ebsi:zuzDMJqHyxtCEE6FRuCPTgD#pABLTNzSQAsmp_o9o-fHsZFDTdHg4qI7zIotD0q4BuM\",\"type\":\"JsonWebKey2020\",\"controller\":\"did:ebsi:zuzDMJqHyxtCEE6FRuCPTgD\",\"publicKeyJwk\":{\"kty\":\"EC\",\"crv\":\"secp256k1\",\"x\":\"cKWH4GgY2T4veJJZOgvu1AQo8ZtyXCyqelmoJLMD7tE\",\"y\":\"qasRE5xIHWhNSO0kQWG2ZGxc7NYMhuIMIF7kXACC-zQ\"}},{\"id\":\"did:ebsi:zuzDMJqHyxtCEE6FRuCPTgD#05tX5S5X6_tHSIBgBQQheNCWsqg16Pp9waiz4IdSCKI\",\"type\":\"JsonWebKey2020\",\"controller\":\"did:ebsi:zuzDMJqHyxtCEE6FRuCPTgD\",\"publicKeyJwk\":{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"mtkBFqZiXcm1qNyTfcATB5gjlhFUK42ElVw9ho1F5Rc\",\"y\":\"qc3QRI1_Jb1i_jQPk8rlJbtZHtcvZg6PNL6v_C9IucQ\"}}],\"authentication\":[\"did:ebsi:zuzDMJqHyxtCEE6FRuCPTgD#pABLTNzSQAsmp_o9o-fHsZFDTdHg4qI7zIotD0q4BuM\",\"did:ebsi:zuzDMJqHyxtCEE6FRuCPTgD#05tX5S5X6_tHSIBgBQQheNCWsqg16Pp9waiz4IdSCKI\"],\"assertionMethod\":[\"did:ebsi:zuzDMJqHyxtCEE6FRuCPTgD#05tX5S5X6_tHSIBgBQQheNCWsqg16Pp9waiz4IdSCKI\",\"did:ebsi:zuzDMJqHyxtCEE6FRuCPTgD#pABLTNzSQAsmp_o9o-fHsZFDTdHg4qI7zIotD0q4BuM\"],\"capabilityInvocation\":[\"did:ebsi:zuzDMJqHyxtCEE6FRuCPTgD#pABLTNzSQAsmp_o9o-fHsZFDTdHg4qI7zIotD0q4BuM\"]}"
                ),
                listOf(
                    Pair(
                        Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"secp256k1\",\"x\":\"cKWH4GgY2T4veJJZOgvu1AQo8ZtyXCyqelmoJLMD7tE\",\"y\":\"qasRE5xIHWhNSO0kQWG2ZGxc7NYMhuIMIF7kXACC-zQ\"}"),
                        secp256DidAssertions,
                    ),
                    Pair(
                        Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"mtkBFqZiXcm1qNyTfcATB5gjlhFUK42ElVw9ho1F5Rc\",\"y\":\"qc3QRI1_Jb1i_jQPk8rlJbtZHtcvZg6PNL6v_C9IucQ\"}"),
                        secp256DidAssertions,
                    ),
                ),
                Pair(
                    Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"mtkBFqZiXcm1qNyTfcATB5gjlhFUK42ElVw9ho1F5Rc\",\"y\":\"qc3QRI1_Jb1i_jQPk8rlJbtZHtcvZg6PNL6v_C9IucQ\"}"),
                    secp256KeyAssertions,
                ),
            ),
            TestEntry(
                "did:ebsi:zaA9cXrqiCD4n3AqX4yD7Gd",
                Json.decodeFromString<JsonObject>(
                    "{\"@context\":[\"https://www.w3.org/ns/did/v1\",\"https://w3id.org/security/suites/jws-2020/v1\"],\"id\":\"did:ebsi:zaA9cXrqiCD4n3AqX4yD7Gd\",\"controller\":[\"did:ebsi:zaA9cXrqiCD4n3AqX4yD7Gd\"],\"verificationMethod\":[{\"id\":\"did:ebsi:zaA9cXrqiCD4n3AqX4yD7Gd#GCocFFVY5IKotB3Gae70EveOa2XsVYorfOeRF9QMdkY\",\"type\":\"JsonWebKey2020\",\"controller\":\"did:ebsi:zaA9cXrqiCD4n3AqX4yD7Gd\",\"publicKeyJwk\":{\"kty\":\"EC\",\"crv\":\"secp256k1\",\"x\":\"TZmvrZc0oFEuZskm-M2yQLqdcjXQgoiWBIB6LUtUG4s\",\"y\":\"p6JdZyx81QoYAPNSLIiR0hWGBzFejs-GnKt6kKzBk9I\"}},{\"id\":\"did:ebsi:zaA9cXrqiCD4n3AqX4yD7Gd#q9YqiPX_jrQy8dIDLa9yzeuJQiTdjudD9sgbMAfqB2Q\",\"type\":\"JsonWebKey2020\",\"controller\":\"did:ebsi:zaA9cXrqiCD4n3AqX4yD7Gd\",\"publicKeyJwk\":{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"juTV0do_XCbGfOu9DyPbaXVv-_HfnQwv6rBRo1uLX00\",\"y\":\"Xn3GCn5H9SdP5r0zbGTE1JXVoporSw23CU0To3tibAk\"}}],\"authentication\":[\"did:ebsi:zaA9cXrqiCD4n3AqX4yD7Gd#GCocFFVY5IKotB3Gae70EveOa2XsVYorfOeRF9QMdkY\",\"did:ebsi:zaA9cXrqiCD4n3AqX4yD7Gd#q9YqiPX_jrQy8dIDLa9yzeuJQiTdjudD9sgbMAfqB2Q\"],\"assertionMethod\":[\"did:ebsi:zaA9cXrqiCD4n3AqX4yD7Gd#q9YqiPX_jrQy8dIDLa9yzeuJQiTdjudD9sgbMAfqB2Q\",\"did:ebsi:zaA9cXrqiCD4n3AqX4yD7Gd#GCocFFVY5IKotB3Gae70EveOa2XsVYorfOeRF9QMdkY\"],\"capabilityInvocation\":[\"did:ebsi:zaA9cXrqiCD4n3AqX4yD7Gd#GCocFFVY5IKotB3Gae70EveOa2XsVYorfOeRF9QMdkY\"]}"
                ),
                listOf(
                    Pair(
                        Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"secp256k1\",\"x\":\"TZmvrZc0oFEuZskm-M2yQLqdcjXQgoiWBIB6LUtUG4s\",\"y\":\"p6JdZyx81QoYAPNSLIiR0hWGBzFejs-GnKt6kKzBk9I\"}"),
                        secp256DidAssertions,
                    ),
                    Pair(
                        Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"juTV0do_XCbGfOu9DyPbaXVv-_HfnQwv6rBRo1uLX00\",\"y\":\"Xn3GCn5H9SdP5r0zbGTE1JXVoporSw23CU0To3tibAk\"}"),
                        secp256DidAssertions,
                    ),
                ),
                Pair(
                    Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"juTV0do_XCbGfOu9DyPbaXVv-_HfnQwv6rBRo1uLX00\",\"y\":\"Xn3GCn5H9SdP5r0zbGTE1JXVoporSw23CU0To3tibAk\"}"),
                    secp256KeyAssertions,
                ),
            ),
            TestEntry(
                "did:ebsi:z24Qnyqxd6cX5LeGRPdfZf3P",
                Json.decodeFromString<JsonObject>(
                    "{\"@context\":[\"https://www.w3.org/ns/did/v1\",\"https://w3id.org/security/suites/jws-2020/v1\"],\"id\":\"did:ebsi:z24Qnyqxd6cX5LeGRPdfZf3P\",\"controller\":[\"did:ebsi:z24Qnyqxd6cX5LeGRPdfZf3P\"],\"verificationMethod\":[{\"id\":\"did:ebsi:z24Qnyqxd6cX5LeGRPdfZf3P#p4k-BPfU08EvNJAfm3IL2SOXCnwG-zZY4BFKVWVoRog\",\"type\":\"JsonWebKey2020\",\"controller\":\"did:ebsi:z24Qnyqxd6cX5LeGRPdfZf3P\",\"publicKeyJwk\":{\"kty\":\"EC\",\"crv\":\"secp256k1\",\"x\":\"AfUb92zIDYfTQnBt4WWTu5C1_Br42R4nV2L65w-beRQ\",\"y\":\"D-iXTeL0-ib-eHmgIftHhoCfuyQUZwHpaug8zdoJwy8\"}},{\"id\":\"did:ebsi:z24Qnyqxd6cX5LeGRPdfZf3P#syImaaEUN-qism4UnsDaKEFZ1OiyR_R5TuY7fW4LXZ8\",\"type\":\"JsonWebKey2020\",\"controller\":\"did:ebsi:z24Qnyqxd6cX5LeGRPdfZf3P\",\"publicKeyJwk\":{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"-Nspjw4dCEGcxE5JeG-DCRmtoui3TiWPB9ijTXgRQTc\",\"y\":\"hY0P4EH_0IxjAcksz-9jHKU_SdadUqnpiwSFce4wSg0\"}}],\"authentication\":[\"did:ebsi:z24Qnyqxd6cX5LeGRPdfZf3P#p4k-BPfU08EvNJAfm3IL2SOXCnwG-zZY4BFKVWVoRog\",\"did:ebsi:z24Qnyqxd6cX5LeGRPdfZf3P#syImaaEUN-qism4UnsDaKEFZ1OiyR_R5TuY7fW4LXZ8\"],\"assertionMethod\":[\"did:ebsi:z24Qnyqxd6cX5LeGRPdfZf3P#syImaaEUN-qism4UnsDaKEFZ1OiyR_R5TuY7fW4LXZ8\"],\"capabilityInvocation\":[\"did:ebsi:z24Qnyqxd6cX5LeGRPdfZf3P#p4k-BPfU08EvNJAfm3IL2SOXCnwG-zZY4BFKVWVoRog\"]}"
                ),
                listOf(
                    Pair(
                        Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"secp256k1\",\"x\":\"AfUb92zIDYfTQnBt4WWTu5C1_Br42R4nV2L65w-beRQ\",\"y\":\"D-iXTeL0-ib-eHmgIftHhoCfuyQUZwHpaug8zdoJwy8\"}"),
                        secp256DidAssertions,
                    ),
                    Pair(
                        Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"-Nspjw4dCEGcxE5JeG-DCRmtoui3TiWPB9ijTXgRQTc\",\"y\":\"hY0P4EH_0IxjAcksz-9jHKU_SdadUqnpiwSFce4wSg0\"}"),
                        secp256DidAssertions,
                    ),
                ),
                Pair(
                    Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"-Nspjw4dCEGcxE5JeG-DCRmtoui3TiWPB9ijTXgRQTc\",\"y\":\"hY0P4EH_0IxjAcksz-9jHKU_SdadUqnpiwSFce4wSg0\"}"),
                    secp256KeyAssertions,
                ),
            ),
            TestEntry(
                "did:ebsi:zwPFzwurMARpaw7KfCWY3qK",
                Json.decodeFromString<JsonObject>(
                    "{\"@context\":[\"https://www.w3.org/ns/did/v1\",\"https://w3id.org/security/suites/jws-2020/v1\"],\"id\":\"did:ebsi:zwPFzwurMARpaw7KfCWY3qK\",\"controller\":[\"did:ebsi:zwPFzwurMARpaw7KfCWY3qK\"],\"verificationMethod\":[{\"id\":\"did:ebsi:zwPFzwurMARpaw7KfCWY3qK#WHg1oqWFmR7L7YrVozU0TVGb68_19yW_-c4Yw7DMLO0\",\"type\":\"JsonWebKey2020\",\"controller\":\"did:ebsi:zwPFzwurMARpaw7KfCWY3qK\",\"publicKeyJwk\":{\"kty\":\"EC\",\"crv\":\"secp256k1\",\"x\":\"eO7RXSY1A2iiyhvf7i2InIQFfZjT8izOIn0TMt0BptE\",\"y\":\"sLL8kmzPRFh9TLrQdj18rzkrZN4--R-gNkzrIjbRr-k\"}},{\"id\":\"did:ebsi:zwPFzwurMARpaw7KfCWY3qK#JRM0Eu1MTpADnpCvEcz7Nj0-Qpy4-lctgsOPBncBZ9c\",\"type\":\"JsonWebKey2020\",\"controller\":\"did:ebsi:zwPFzwurMARpaw7KfCWY3qK\",\"publicKeyJwk\":{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"0IFKfGudFz02SR0S-kYY_soqK5FLuWrjsU_L7nc1DNs\",\"y\":\"D-EpehGuos8W7qyV77t4w847T24s6Rez71lWuqihU2c\"}}],\"authentication\":[\"did:ebsi:zwPFzwurMARpaw7KfCWY3qK#WHg1oqWFmR7L7YrVozU0TVGb68_19yW_-c4Yw7DMLO0\",\"did:ebsi:zwPFzwurMARpaw7KfCWY3qK#JRM0Eu1MTpADnpCvEcz7Nj0-Qpy4-lctgsOPBncBZ9c\"],\"assertionMethod\":[\"did:ebsi:zwPFzwurMARpaw7KfCWY3qK#JRM0Eu1MTpADnpCvEcz7Nj0-Qpy4-lctgsOPBncBZ9c\"],\"capabilityInvocation\":[\"did:ebsi:zwPFzwurMARpaw7KfCWY3qK#WHg1oqWFmR7L7YrVozU0TVGb68_19yW_-c4Yw7DMLO0\"]}"
                ),
                listOf(
                    Pair(
                        Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"secp256k1\",\"x\":\"eO7RXSY1A2iiyhvf7i2InIQFfZjT8izOIn0TMt0BptE\",\"y\":\"sLL8kmzPRFh9TLrQdj18rzkrZN4--R-gNkzrIjbRr-k\"}"),
                        secp256DidAssertions,
                    ),
                    Pair(
                        Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"0IFKfGudFz02SR0S-kYY_soqK5FLuWrjsU_L7nc1DNs\",\"y\":\"D-EpehGuos8W7qyV77t4w847T24s6Rez71lWuqihU2c\"}"),
                        secp256DidAssertions,
                    ),
                ),
                Pair(
                    Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"0IFKfGudFz02SR0S-kYY_soqK5FLuWrjsU_L7nc1DNs\",\"y\":\"D-EpehGuos8W7qyV77t4w847T24s6Rez71lWuqihU2c\"}"),
                    secp256KeyAssertions,
                ),
            ),
            TestEntry(
                "did:ebsi:z24AVGY1vsRLc6ZcGUH6r2Ge",
                Json.decodeFromString<JsonObject>(
                    "{\"@context\":[\"https://www.w3.org/ns/did/v1\",\"https://w3id.org/security/suites/jws-2020/v1\"],\"id\":\"did:ebsi:z24AVGY1vsRLc6ZcGUH6r2Ge\",\"controller\":[\"did:ebsi:z24AVGY1vsRLc6ZcGUH6r2Ge\"],\"verificationMethod\":[{\"id\":\"did:ebsi:z24AVGY1vsRLc6ZcGUH6r2Ge#vut3AiXz8I-8MiMDNzJg3fBwrLiGPGHqIkFf11dLSrM\",\"type\":\"JsonWebKey2020\",\"controller\":\"did:ebsi:z24AVGY1vsRLc6ZcGUH6r2Ge\",\"publicKeyJwk\":{\"kty\":\"EC\",\"crv\":\"secp256k1\",\"x\":\"fcKROMFjLeVbBqC85a8CqdW0SuaFcjKo30ZQ3oc4g7Q\",\"y\":\"hmdWyHYSiJBHKpyrfXTuReNgNx8zBzNOJ05uld-0CwA\"}},{\"id\":\"did:ebsi:z24AVGY1vsRLc6ZcGUH6r2Ge#qnzNkpKXrn5B2TNFfqV28H50ai5NI9NeoTJgYQQyJkw\",\"type\":\"JsonWebKey2020\",\"controller\":\"did:ebsi:z24AVGY1vsRLc6ZcGUH6r2Ge\",\"publicKeyJwk\":{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"RSa9erH7Pe8eReZOzDk21lMo7900xTSo5XNrW5XxL9E\",\"y\":\"8Jpf0RmLngsz5ynDmIERV8PM61zK-U0l1scum9GgBJM\"}},{\"id\":\"did:ebsi:z24AVGY1vsRLc6ZcGUH6r2Ge#ZzZast3tPsTS9Avg1t21Et3d-4_j14sUR1bQg1tNH2s\",\"type\":\"JsonWebKey2020\",\"controller\":\"did:ebsi:z24AVGY1vsRLc6ZcGUH6r2Ge\",\"publicKeyJwk\":{\"kty\":\"RSA\",\"n\":\"v35Qi_SQ40YZOSa9Ruzh_c1JP4OQXxnFYaDPCSNhu6sbNWVsAc4LWR2mS3wDGQ1G93E6pg96aZR4JqiHoDoXs6qYbcNm1lZtdg2mpX1kGazO7PXgp7KuN4AEJmWAGyAqfjeb4jGevBCJondnBNtqmr_rvQa3Rqnij8KIzqn9Wr4Zu8d6qKbgUlZxYiZPIr8OVdopX5dgodtIagkW7IMLBsW4l22-2TYeD9hz4XQmHVyhvemKhwN4mWIgSgXF19jiD_wcAtKUgn5uokX45mi8H3_f0-WvRuu6Dl0q1ZnoPiyT07FjdBuLSNu7vxRPQWUtaqum9fEoqdyOrZKZNE7ooQ\",\"e\":\"AQAB\"}},{\"id\":\"did:ebsi:z24AVGY1vsRLc6ZcGUH6r2Ge#08upLhRl50l-A2XQ2L1ZQVWkArOHpAQviqSpwp4InEA\",\"type\":\"JsonWebKey2020\",\"controller\":\"did:ebsi:z24AVGY1vsRLc6ZcGUH6r2Ge\",\"publicKeyJwk\":{\"crv\":\"Ed25519\",\"x\":\"dQJ74l6r97Wo4Nuxwptv_EztdCgMkPcl6Lia8l6avzU\",\"kty\":\"OKP\"}}],\"authentication\":[\"did:ebsi:z24AVGY1vsRLc6ZcGUH6r2Ge#vut3AiXz8I-8MiMDNzJg3fBwrLiGPGHqIkFf11dLSrM\",\"did:ebsi:z24AVGY1vsRLc6ZcGUH6r2Ge#qnzNkpKXrn5B2TNFfqV28H50ai5NI9NeoTJgYQQyJkw\",\"did:ebsi:z24AVGY1vsRLc6ZcGUH6r2Ge#ZzZast3tPsTS9Avg1t21Et3d-4_j14sUR1bQg1tNH2s\",\"did:ebsi:z24AVGY1vsRLc6ZcGUH6r2Ge#08upLhRl50l-A2XQ2L1ZQVWkArOHpAQviqSpwp4InEA\"],\"assertionMethod\":[\"did:ebsi:z24AVGY1vsRLc6ZcGUH6r2Ge#qnzNkpKXrn5B2TNFfqV28H50ai5NI9NeoTJgYQQyJkw\",\"did:ebsi:z24AVGY1vsRLc6ZcGUH6r2Ge#vut3AiXz8I-8MiMDNzJg3fBwrLiGPGHqIkFf11dLSrM\",\"did:ebsi:z24AVGY1vsRLc6ZcGUH6r2Ge#ZzZast3tPsTS9Avg1t21Et3d-4_j14sUR1bQg1tNH2s\",\"did:ebsi:z24AVGY1vsRLc6ZcGUH6r2Ge#08upLhRl50l-A2XQ2L1ZQVWkArOHpAQviqSpwp4InEA\"],\"capabilityInvocation\":[\"did:ebsi:z24AVGY1vsRLc6ZcGUH6r2Ge#vut3AiXz8I-8MiMDNzJg3fBwrLiGPGHqIkFf11dLSrM\"]}"
                ),
                listOf(
                    Pair(
                        Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"secp256k1\",\"x\":\"fcKROMFjLeVbBqC85a8CqdW0SuaFcjKo30ZQ3oc4g7Q\",\"y\":\"hmdWyHYSiJBHKpyrfXTuReNgNx8zBzNOJ05uld-0CwA\"}"),
                        secp256DidAssertions,
                    ),
                    Pair(
                        Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"RSa9erH7Pe8eReZOzDk21lMo7900xTSo5XNrW5XxL9E\",\"y\":\"8Jpf0RmLngsz5ynDmIERV8PM61zK-U0l1scum9GgBJM\"}"),
                        secp256DidAssertions,
                    ),
                ),
                Pair(
                    Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"RSa9erH7Pe8eReZOzDk21lMo7900xTSo5XNrW5XxL9E\",\"y\":\"8Jpf0RmLngsz5ynDmIERV8PM61zK-U0l1scum9GgBJM\"}"),
                    secp256KeyAssertions,
                ),
            ),
            TestEntry(
                "did:ebsi:zbiABMoKYdMDcu56oXizxxB",
                Json.decodeFromString<JsonObject>(
                    "{\"@context\":[\"https://www.w3.org/ns/did/v1\",\"https://w3id.org/security/suites/jws-2020/v1\"],\"id\":\"did:ebsi:zbiABMoKYdMDcu56oXizxxB\",\"controller\":[\"did:ebsi:zbiABMoKYdMDcu56oXizxxB\"],\"verificationMethod\":[{\"id\":\"did:ebsi:zbiABMoKYdMDcu56oXizxxB#UWfgOq5Q1lt6HJlY-LrVlbl9S9k538_NYpx6rip_Mp0\",\"type\":\"JsonWebKey2020\",\"controller\":\"did:ebsi:zbiABMoKYdMDcu56oXizxxB\",\"publicKeyJwk\":{\"kty\":\"EC\",\"crv\":\"secp256k1\",\"x\":\"NpYbgWTX6TpeN3TC2tl49ULEk_qflBJ3THY1EladubI\",\"y\":\"AGocDIS_gpJN5zQBBFtgGvM2pKwopSjxZ2sBOqWzDxQ\"}},{\"id\":\"did:ebsi:zbiABMoKYdMDcu56oXizxxB#imFjgN0RrHNIQdudOCqrq_Y-7xkPZa53UtNQvMTm_6Y\",\"type\":\"JsonWebKey2020\",\"controller\":\"did:ebsi:zbiABMoKYdMDcu56oXizxxB\",\"publicKeyJwk\":{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"flk7PEpPB_D8HInoWUgL4fgh52iBGQ-H35qNL42lQ6U\",\"y\":\"ho4J0JQtKWOAVPtEyzb4LNWNPfEbrNtCPAiHUzOWgEQ\"}}],\"authentication\":[\"did:ebsi:zbiABMoKYdMDcu56oXizxxB#UWfgOq5Q1lt6HJlY-LrVlbl9S9k538_NYpx6rip_Mp0\",\"did:ebsi:zbiABMoKYdMDcu56oXizxxB#imFjgN0RrHNIQdudOCqrq_Y-7xkPZa53UtNQvMTm_6Y\"],\"assertionMethod\":[\"did:ebsi:zbiABMoKYdMDcu56oXizxxB#imFjgN0RrHNIQdudOCqrq_Y-7xkPZa53UtNQvMTm_6Y\"],\"capabilityInvocation\":[\"did:ebsi:zbiABMoKYdMDcu56oXizxxB#UWfgOq5Q1lt6HJlY-LrVlbl9S9k538_NYpx6rip_Mp0\"]}"
                ),
                listOf(
                    Pair(
                        Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"secp256k1\",\"x\":\"NpYbgWTX6TpeN3TC2tl49ULEk_qflBJ3THY1EladubI\",\"y\":\"AGocDIS_gpJN5zQBBFtgGvM2pKwopSjxZ2sBOqWzDxQ\"}"),
                        secp256DidAssertions,
                    ),
                    Pair(
                        Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"flk7PEpPB_D8HInoWUgL4fgh52iBGQ-H35qNL42lQ6U\",\"y\":\"ho4J0JQtKWOAVPtEyzb4LNWNPfEbrNtCPAiHUzOWgEQ\"}"),
                        secp256DidAssertions,
                    ),
                ),
                Pair(
                    Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"flk7PEpPB_D8HInoWUgL4fgh52iBGQ-H35qNL42lQ6U\",\"y\":\"ho4J0JQtKWOAVPtEyzb4LNWNPfEbrNtCPAiHUzOWgEQ\"}"),
                    secp256KeyAssertions,
                ),
            ),
            TestEntry(
                "did:ebsi:zr4QYv94jLekTJ5d3k2nVz1",
                Json.decodeFromString<JsonObject>(
                    "{\"@context\":[\"https://www.w3.org/ns/did/v1\",\"https://w3id.org/security/suites/jws-2020/v1\"],\"id\":\"did:ebsi:zr4QYv94jLekTJ5d3k2nVz1\",\"controller\":[\"did:ebsi:zr4QYv94jLekTJ5d3k2nVz1\"],\"verificationMethod\":[{\"id\":\"did:ebsi:zr4QYv94jLekTJ5d3k2nVz1#o2UCggyx3GBstYsYEg5fjnlyc1a0Lfw4gth83dDZcDY\",\"type\":\"JsonWebKey2020\",\"controller\":\"did:ebsi:zr4QYv94jLekTJ5d3k2nVz1\",\"publicKeyJwk\":{\"kty\":\"EC\",\"crv\":\"secp256k1\",\"x\":\"FzuNuBO4ThY6JcxiGlOqA7pysycp-JggEbsY3VTs7iA\",\"y\":\"mPh3a6N8-lwoCwUzpFIQQ0iHhhXrSyS5BQ-dymLg66E\"}},{\"id\":\"did:ebsi:zr4QYv94jLekTJ5d3k2nVz1#V1SMDlhcShsWDv-TB7oJTMjoRp0gJML_0Dh0mMgUvgc\",\"type\":\"JsonWebKey2020\",\"controller\":\"did:ebsi:zr4QYv94jLekTJ5d3k2nVz1\",\"publicKeyJwk\":{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"GDLvQXUiZNkT2ZtywzsLYdxaS7_VBAsJ_gAma87dwRw\",\"y\":\"KWMKMjTpUVGtfDSe_bgOEPisA_gpDJFRR-GAl0EuS_M\"}}],\"authentication\":[\"did:ebsi:zr4QYv94jLekTJ5d3k2nVz1#o2UCggyx3GBstYsYEg5fjnlyc1a0Lfw4gth83dDZcDY\",\"did:ebsi:zr4QYv94jLekTJ5d3k2nVz1#V1SMDlhcShsWDv-TB7oJTMjoRp0gJML_0Dh0mMgUvgc\"],\"assertionMethod\":[\"did:ebsi:zr4QYv94jLekTJ5d3k2nVz1#V1SMDlhcShsWDv-TB7oJTMjoRp0gJML_0Dh0mMgUvgc\"],\"capabilityInvocation\":[\"did:ebsi:zr4QYv94jLekTJ5d3k2nVz1#o2UCggyx3GBstYsYEg5fjnlyc1a0Lfw4gth83dDZcDY\"]}"
                ),
                listOf(
                    Pair(
                        Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"secp256k1\",\"x\":\"FzuNuBO4ThY6JcxiGlOqA7pysycp-JggEbsY3VTs7iA\",\"y\":\"mPh3a6N8-lwoCwUzpFIQQ0iHhhXrSyS5BQ-dymLg66E\"}"),
                        secp256DidAssertions,
                    ),
                    Pair(
                        Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"GDLvQXUiZNkT2ZtywzsLYdxaS7_VBAsJ_gAma87dwRw\",\"y\":\"KWMKMjTpUVGtfDSe_bgOEPisA_gpDJFRR-GAl0EuS_M\"}"),
                        secp256DidAssertions,
                    ),
                ),
                Pair(
                    Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"GDLvQXUiZNkT2ZtywzsLYdxaS7_VBAsJ_gAma87dwRw\",\"y\":\"KWMKMjTpUVGtfDSe_bgOEPisA_gpDJFRR-GAl0EuS_M\"}"),
                    secp256KeyAssertions,
                ),
            ),
        )
    }

}
