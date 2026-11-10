package id.waltid.openid4vp.wallet.presentation

import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.dcql.DcqlDisclosure
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.DcqlParser
import id.walt.dcql.RawDcqlCredential
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals

class DisclosuresToPresentTest {

    private fun location(vararg segments: Any): List<JsonElement> =
        segments.map { if (it is Int) JsonPrimitive(it) else JsonPrimitive(it as String) }

    private val india = SdJwtSelectiveDisclosure("s1", null, JsonPrimitive("IN"))
    private val france = SdJwtSelectiveDisclosure("s2", null, JsonPrimitive("FR"))
    private val givenName = SdJwtSelectiveDisclosure("s3", "given_name", JsonPrimitive("John"))

    private val credential = object : SelectivelyDisclosableVerifiableCredential {
        override val disclosables: Map<String, Set<String>>? = null
        override val disclosures = listOf(
            india.copy(location = location("nationalities", 0)),
            france.copy(location = location("nationalities", 1)),
            givenName.copy(location = location("given_name")),
        )
        override val signedWithDisclosures: String? = null
        override val originalCredentialData: JsonObject? = null
    }

    private val query = DcqlParser.parse(
        """{"credentials": [{"id": "pid", "format": "dc+sd-jwt", "meta": {"vct_values": ["pid"]}}]}"""
    ).getOrThrow().credentials.single()

    private fun matchResult(
        selected: Map<String, Any>? = null,
        byPath: Map<String, List<DcqlDisclosure>> = emptyMap(),
    ) = DcqlMatcher.DcqlMatchResult(
        credential = RawDcqlCredential("pid", "dc+sd-jwt", JsonObject(emptyMap())),
        selectedDisclosures = selected,
        originalQuery = query,
        disclosuresByPath = byPath,
    )

    private fun SdJwtSelectiveDisclosure.toDcql(location: List<JsonElement>?) = DcqlDisclosure(name, value, location)

    @Test
    fun arrayElementIsResolvedByLocationNotByMissingName() {
        val presented = credential.disclosuresToPresent(
            matchResult(byPath = mapOf("nationalities.1" to listOf(france.toDcql(location("nationalities", 1)))))
        )

        assertEquals(listOf(france.encoded), presented.map { it.encoded })
    }

    @Test
    fun sharedDisclosureIsPresentedOnce() {
        val presented = credential.disclosuresToPresent(
            matchResult(
                byPath = mapOf(
                    "given_name" to listOf(givenName.toDcql(location("given_name"))),
                    "nationalities.1" to listOf(france.toDcql(location("nationalities", 1))),
                    "nationalities" to listOf(france.toDcql(location("nationalities", 1))),
                )
            )
        )

        assertEquals(listOf(givenName.encoded, france.encoded), presented.map { it.encoded })
    }

    @Test
    fun withoutLocationOnlyNamedClaimsResolve() {
        val presented = credential.disclosuresToPresent(
            matchResult(selected = mapOf("given_name" to givenName.toDcql(null), "nationalities.1" to france.toDcql(null)))
        )

        assertEquals(listOf(givenName.encoded), presented.map { it.encoded })
    }
}
