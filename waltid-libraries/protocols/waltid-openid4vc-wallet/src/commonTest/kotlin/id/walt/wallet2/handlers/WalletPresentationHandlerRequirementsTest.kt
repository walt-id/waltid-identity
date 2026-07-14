package id.walt.wallet2.handlers

import id.walt.dcql.DcqlMatcher
import id.walt.dcql.DcqlDisclosure
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.CredentialSetQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.NoMeta
import id.walt.wallet2.data.Wallet
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WalletPresentationHandlerRequirementsTest {

    @Test
    fun presentationRequirementsWithoutCredentialSetsRequireEveryQuery() {
        val query = DcqlQuery(
            credentials = listOf(
                credentialQuery("pid"),
                credentialQuery("age"),
            )
        )

        val requirements = WalletPresentationHandler.run { query.requiredCredentialRequirements() }

        assertEquals(listOf(PresentationCredentialRequirement(options = listOf(listOf("pid", "age")))), requirements)
        assertTrue(WalletPresentationHandler.run { requirements.satisfiedBy(setOf("pid", "age")) })
        assertFalse(WalletPresentationHandler.run { requirements.satisfiedBy(setOf("pid")) })
    }

    @Test
    fun presentationRequirementsHonorRequiredCredentialSetAlternatives() {
        val query = DcqlQuery(
            credentials = listOf(
                credentialQuery("mdl-id"),
                credentialQuery("photo-id"),
                credentialQuery("address"),
            ),
            credentialSets = listOf(
                CredentialSetQuery(
                    options = listOf(
                        listOf("mdl-id"),
                        listOf("photo-id"),
                    )
                ),
                CredentialSetQuery(
                    required = false,
                    options = listOf(listOf("address")),
                ),
            )
        )

        val requirements = WalletPresentationHandler.run { query.requiredCredentialRequirements() }

        assertEquals(
            listOf(
                PresentationCredentialRequirement(
                    options = listOf(
                        listOf("mdl-id"),
                        listOf("photo-id"),
                    )
                )
            ),
            requirements,
        )
        assertTrue(WalletPresentationHandler.run { requirements.satisfiedBy(setOf("photo-id")) })
        assertFalse(WalletPresentationHandler.run { requirements.satisfiedBy(setOf("address")) })
    }

    @Test
    fun presentationRequirementsIgnoreOptionalSetsWhenNoRequiredSetExists() {
        val query = DcqlQuery(
            credentials = listOf(
                credentialQuery("address"),
                credentialQuery("photo-id"),
            ),
            credentialSets = listOf(
                CredentialSetQuery(
                    required = false,
                    options = listOf(
                        listOf("address"),
                        listOf("photo-id"),
                    ),
                ),
            )
        )

        val requirements = WalletPresentationHandler.run { query.requiredCredentialRequirements() }

        assertEquals(emptyList(), requirements)
        assertTrue(WalletPresentationHandler.run { requirements.satisfiedBy(emptySet()) })
    }

    @Test
    fun presentationSelectionRequiresAtLeastOneCredentialOption() {
        assertFailsWith<IllegalArgumentException> {
            WalletPresentationHandler.run {
                emptyList<PresentationCredentialSelection>().requireValidPresentationCredentialSelection()
            }
        }
    }

    @Test
    fun submitPresentationRequiresMatchingPreview() = runTest {
        val error = assertFailsWith<MissingPresentationPreviewException> {
            WalletPresentationHandler.submitPresentation(
                wallet = Wallet(id = "wallet-without-preview"),
                request = SubmitPresentationRequest(
                    requestUrl = Url("openid4vp://authorize?request_uri=https%3A%2F%2Fverifier.example%2Frequest.jwt"),
                    selectedCredentialOptions = listOf(
                        PresentationCredentialSelection(queryId = "pid", credentialId = "cred-1"),
                    ),
                ),
            )
        }

        assertEquals(
            "Presentation request preview expired or was not found; preview the request again before submitting.",
            error.message,
        )
    }

    @Test
    fun presentationSelectionAllowsMultipleCredentialsForOneQueryWhenMatched() {
        val selections = listOf(
            PresentationCredentialSelection(queryId = "pid", credentialId = "cred-1"),
            PresentationCredentialSelection(queryId = "pid", credentialId = "cred-2"),
        )
        val matched = matchResult(
            "pid" to "cred-1",
            "pid" to "cred-2",
            query = { queryId -> credentialQuery(queryId, multiple = true) },
        )

        val selected = WalletPresentationHandler.run {
            matched.selectCredentialOptions(selections)
        }

        assertEquals(listOf("cred-1", "cred-2"), selected.getValue("pid").map { it.credential.id })
    }

    @Test
    fun presentationSelectionRejectsMultipleCredentialsForNonMultipleQuery() {
        val matched = matchResult(
            "pid" to "cred-1",
            "pid" to "cred-2",
        )

        assertFailsWith<IllegalArgumentException> {
            WalletPresentationHandler.run {
                matched.selectCredentialOptions(
                    listOf(
                        PresentationCredentialSelection(queryId = "pid", credentialId = "cred-1"),
                        PresentationCredentialSelection(queryId = "pid", credentialId = "cred-2"),
                    )
                )
            }
        }
    }

    @Test
    fun presentationSelectionRejectsBlankQueryOrCredentialIds() {
        val selections = listOf(
            PresentationCredentialSelection(queryId = "pid", credentialId = " "),
        )

        assertFailsWith<IllegalArgumentException> {
            WalletPresentationHandler.run {
                selections.requireValidPresentationCredentialSelection()
            }
        }
    }

    @Test
    fun presentationSelectionAllowsOneCredentialPerQuery() {
        val selections = listOf(
            PresentationCredentialSelection(queryId = "identity", credentialId = "cred-1"),
            PresentationCredentialSelection(queryId = "age", credentialId = "cred-1"),
        )

        WalletPresentationHandler.run {
            selections.requireValidPresentationCredentialSelection()
        }
    }

    @Test
    fun presentationSelectionRejectsUnknownQueryId() {
        val matched = matchResult("pid" to "cred-1")

        assertFailsWith<IllegalArgumentException> {
            WalletPresentationHandler.run {
                matched.selectCredentialOptions(
                    listOf(PresentationCredentialSelection(queryId = "unknown", credentialId = "cred-1"))
                )
            }
        }

        assertFailsWith<IllegalArgumentException> {
            WalletPresentationHandler.run {
                matched.selectCredentialOptions(
                    selectedCredentialOptions = listOf(PresentationCredentialSelection(queryId = "pid", credentialId = "cred-1")),
                    selectedDisclosureOptions = listOf(
                        PresentationDisclosureSelection(queryId = "pid", credentialId = "cred-1", path = "$.unknown"),
                    ),
                )
            }
        }

        assertFailsWith<IllegalArgumentException> {
            WalletPresentationHandler.run {
                matchResult(
                    "pid" to "cred-1",
                    "pid" to "cred-2",
                    selectedDisclosures = mapOf("$.given_name" to DcqlDisclosure("given_name", JsonPrimitive("Ada"))),
                ).selectCredentialOptions(
                    selectedCredentialOptions = listOf(PresentationCredentialSelection(queryId = "pid", credentialId = "cred-1")),
                    selectedDisclosureOptions = listOf(
                        PresentationDisclosureSelection(queryId = "pid", credentialId = "cred-2", path = "$.given_name"),
                    ),
                )
            }
        }

        assertFailsWith<IllegalArgumentException> {
            WalletPresentationHandler.run {
                matchResult("pid" to "cred-1").selectCredentialOptions(
                    selectedCredentialOptions = listOf(PresentationCredentialSelection(queryId = "pid", credentialId = "cred-1")),
                    selectedDisclosureOptions = listOf(
                        PresentationDisclosureSelection(queryId = "pid", credentialId = "cred-1", path = "$.given_name"),
                    ),
                )
            }
        }
    }

    @Test
    fun presentationSelectionRejectsUnknownCredentialIdForKnownQuery() {
        val matched = matchResult("pid" to "cred-1")

        assertFailsWith<IllegalArgumentException> {
            WalletPresentationHandler.run {
                matched.selectCredentialOptions(
                    listOf(PresentationCredentialSelection(queryId = "pid", credentialId = "missing-cred"))
                )
            }
        }
    }

    @Test
    fun presentationSelectionAllowsOneMatchedOptionalOnlyCredentialOption() {
        val matched = matchResult(
            "address" to "cred-address",
            "photo-id" to "cred-photo",
        )

        val selected = WalletPresentationHandler.run {
            matched.selectCredentialOptions(
                listOf(PresentationCredentialSelection(queryId = "photo-id", credentialId = "cred-photo"))
            )
        }

        assertEquals(setOf("photo-id"), selected.keys)
        assertEquals(listOf("cred-photo"), selected.getValue("photo-id").map { it.credential.id })
    }

    @Test
    fun disclosureSelectionKeepsRequiredClaimsWhenFilteringDisclosures() {
        val givenName = claimPath("$", "given_name")
        val ageOver18 = claimPath("$", "age_over_18")
        val matched = matchResult(
            "pid" to "cred-1",
            selectedDisclosures = mapOf(
                givenName to DcqlDisclosure("given_name", JsonPrimitive("Ada")),
                ageOver18 to DcqlDisclosure("age_over_18", JsonPrimitive(true)),
            ),
            query = { queryId ->
                credentialQuery(
                    queryId,
                    claims = listOf(
                        ClaimsQuery(id = "given_name", pathStrings = listOf("$", "given_name")),
                        ClaimsQuery(id = "age_over_18", pathStrings = listOf("$", "age_over_18")),
                    ),
                )
            },
        )

        val selected = WalletPresentationHandler.run {
            matched.selectCredentialOptions(
                selectedCredentialOptions = listOf(PresentationCredentialSelection(queryId = "pid", credentialId = "cred-1")),
                selectedDisclosureOptions = listOf(
                    PresentationDisclosureSelection(queryId = "pid", credentialId = "cred-1", path = givenName),
                ),
            )
        }

        assertEquals(
            setOf(givenName, ageOver18),
            selected.getValue("pid").single().selectedDisclosures.orEmpty().keys,
        )
    }

    @Test
    fun disclosureSelectionFiltersOptionalSelectivelyDisclosableClaimsOnly() {
        val givenName = claimPath("$", "given_name")
        val ageOver18 = claimPath("$", "age_over_18")
        val vct = claimPath("$", "vct")
        val matched = matchResult(
            "pid" to "cred-1",
            selectedDisclosures = mapOf(
                givenName to DcqlDisclosure("given_name", JsonPrimitive("Ada")),
                ageOver18 to DcqlDisclosure("age_over_18", JsonPrimitive(true)),
                vct to JsonPrimitive("https://issuer.example/pid"),
            ),
            query = { queryId ->
                credentialQuery(
                    queryId,
                    claims = listOf(
                        ClaimsQuery(id = "given_name", pathStrings = listOf("$", "given_name")),
                        ClaimsQuery(id = "age_over_18", pathStrings = listOf("$", "age_over_18")),
                        ClaimsQuery(id = "vct", pathStrings = listOf("$", "vct")),
                    ),
                    claimSets = listOf(
                        listOf("given_name", "vct"),
                        listOf("age_over_18", "vct"),
                    ),
                )
            },
        )

        val selected = WalletPresentationHandler.run {
            matched.selectCredentialOptions(
                selectedCredentialOptions = listOf(PresentationCredentialSelection(queryId = "pid", credentialId = "cred-1")),
                selectedDisclosureOptions = listOf(
                    PresentationDisclosureSelection(queryId = "pid", credentialId = "cred-1", path = givenName),
                ),
            )
        }

        val disclosures = selected.getValue("pid").single().selectedDisclosures.orEmpty()
        assertEquals(setOf(givenName, vct), disclosures.keys)
    }

    @Test
    fun disclosureSelectionOmitsSelectivelyDisclosableClaimsWhenNoClaimsAreRequested() {
        val givenName = claimPath("$", "given_name")
        val vct = claimPath("$", "vct")
        val matched = matchResult(
            "pid" to "cred-1",
            selectedDisclosures = mapOf(
                givenName to DcqlDisclosure("given_name", JsonPrimitive("Ada")),
                vct to JsonPrimitive("https://issuer.example/pid"),
            ),
        )

        val selected = WalletPresentationHandler.run {
            matched.selectCredentialOptions(
                selectedCredentialOptions = listOf(PresentationCredentialSelection(queryId = "pid", credentialId = "cred-1")),
                selectedDisclosureOptions = emptyList(),
            )
        }

        val disclosures = selected.getValue("pid").single().selectedDisclosures.orEmpty()
        assertEquals(setOf(vct), disclosures.keys)
    }

    @Test
    fun disclosureSelectionRejectsOptionalSubsetThatDoesNotSatisfyClaimSets() {
        val givenName = claimPath("$", "given_name")
        val ageOver18 = claimPath("$", "age_over_18")
        val portrait = claimPath("$", "portrait")
        val matched = matchResult(
            "pid" to "cred-1",
            selectedDisclosures = mapOf(
                givenName to DcqlDisclosure("given_name", JsonPrimitive("Ada")),
                ageOver18 to DcqlDisclosure("age_over_18", JsonPrimitive(true)),
                portrait to DcqlDisclosure("portrait", JsonPrimitive("image")),
            ),
            query = { queryId ->
                credentialQuery(
                    queryId,
                    claims = listOf(
                        ClaimsQuery(id = "given_name", pathStrings = listOf("$", "given_name")),
                        ClaimsQuery(id = "age_over_18", pathStrings = listOf("$", "age_over_18")),
                        ClaimsQuery(id = "portrait", pathStrings = listOf("$", "portrait")),
                    ),
                    claimSets = listOf(
                        listOf("given_name", "age_over_18"),
                        listOf("given_name", "portrait"),
                    ),
                )
            },
        )

        assertFailsWith<IllegalArgumentException> {
            WalletPresentationHandler.run {
                matched.selectCredentialOptions(
                    selectedCredentialOptions = listOf(PresentationCredentialSelection(queryId = "pid", credentialId = "cred-1")),
                    selectedDisclosureOptions = listOf(
                        PresentationDisclosureSelection(queryId = "pid", credentialId = "cred-1", path = givenName),
                    ),
                )
            }
        }
    }

    @Test
    fun disclosureSelectionRejectsUnknownOrNonSelectiveDisclosurePaths() {
        val matched = matchResult(
            "pid" to "cred-1",
            selectedDisclosures = mapOf(
                "$.given_name" to DcqlDisclosure("given_name", JsonPrimitive("Ada")),
                "$.vct" to JsonPrimitive("https://issuer.example/pid"),
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            WalletPresentationHandler.run {
                matched.selectCredentialOptions(
                    selectedCredentialOptions = listOf(PresentationCredentialSelection(queryId = "pid", credentialId = "cred-1")),
                    selectedDisclosureOptions = listOf(
                        PresentationDisclosureSelection(queryId = "pid", credentialId = "cred-1", path = "$.vct"),
                    ),
                )
            }
        }
    }

    @Test
    fun disclosureSelectionPreservesLegacyAllDisclosuresWhenUnset() {
        val matched = matchResult(
            "pid" to "cred-1",
            selectedDisclosures = mapOf(
                "$.given_name" to DcqlDisclosure("given_name", JsonPrimitive("Ada")),
                "$.age_over_18" to DcqlDisclosure("age_over_18", JsonPrimitive(true)),
            ),
        )

        val selected = WalletPresentationHandler.run {
            matched.selectCredentialOptions(
                selectedCredentialOptions = listOf(PresentationCredentialSelection(queryId = "pid", credentialId = "cred-1")),
            )
        }

        assertEquals(
            setOf("$.given_name", "$.age_over_18"),
            selected.getValue("pid").single().selectedDisclosures.orEmpty().keys,
        )
    }

    private fun credentialQuery(
        id: String,
        multiple: Boolean = false,
        claims: List<ClaimsQuery>? = null,
        claimSets: List<List<String>>? = null,
    ): CredentialQuery =
        CredentialQuery(
            id = id,
            format = CredentialFormat.JWT_VC_JSON,
            multiple = multiple,
            meta = NoMeta,
            claims = claims,
            claimSets = claimSets,
        )

    private fun claimPath(vararg parts: String): String =
        parts.map { JsonPrimitive(it) }.joinToString(".")

    private fun matchResult(
        vararg options: Pair<String, String>,
        selectedDisclosures: Map<String, Any>? = null,
        query: (String) -> CredentialQuery = ::credentialQuery,
    ): Map<String, List<DcqlMatcher.DcqlMatchResult>> =
        options
            .groupBy(
                keySelector = { (queryId, _) -> queryId },
                valueTransform = { (queryId, credentialId) ->
                    DcqlMatcher.DcqlMatchResult(
                        credential = RawDcqlCredential(
                            id = credentialId,
                            format = CredentialFormat.JWT_VC_JSON.id.first(),
                            data = JsonObject(emptyMap()),
                        ),
                        selectedDisclosures = selectedDisclosures,
                        originalQuery = query(queryId),
                    )
                }
            )
}
