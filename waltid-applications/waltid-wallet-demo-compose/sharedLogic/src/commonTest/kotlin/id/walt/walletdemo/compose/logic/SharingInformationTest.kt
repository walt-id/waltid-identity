package id.walt.walletdemo.compose.logic

import kotlin.test.*

class SharingInformationTest {
    private fun disclosure(path: String, value: String, required: Boolean = true, selective: Boolean = true,
        requested: Boolean = true) =
        WalletDemoPresentationDisclosure(CredentialDisplayVocabulary.disclosureLabel(null, path), path, "\"$value\"", value, selective, required,
            selectable = selective && !required, requested = requested)

    private fun option(id: String = "one", query: String = "pid", disclosures: List<WalletDemoPresentationDisclosure>) =
        WalletDemoPresentationCredentialOption(query, id, label = "Identity", issuer = "Issuer",
            format = "dc+sd-jwt", credentialDataJson = """{"given_name":"Ada","family_name":"Lovelace","birth_date":"1815-12-10"}""",
            disclosures = disclosures)

    @Test fun changingCredentialChangesValuesAndDoesNotIncludeUnselectedCredential() {
        val first = option(disclosures = listOf(disclosure("given_name", "Ada")))
        val second = option("two", disclosures = listOf(disclosure("given_name", "Grace")))
        val review = WalletDemoSharingReview(WalletDemoSharingRequest(null), listOf(first, second))
        assertEquals(listOf("Ada"), review.informationToShare(setOf(first.selection), emptySet()).single().fields.map { it.item.rawValue?.trim('"') })
        assertEquals(listOf("Grace"), review.informationToShare(setOf(second.selection), emptySet()).single().fields.map { it.item.rawValue?.trim('"') })
        assertTrue(review.informationToShare(emptySet(), emptySet()).isEmpty())
    }

    @Test fun sameCredentialUnionIsShownOnceAndRequiredDisclosureCannotBeDeselectedThroughAnotherQuery() {
        val required = option(query = "name", disclosures = listOf(disclosure("given_name", "Ada")))
        val optional = option(query = "optional-name", disclosures = listOf(disclosure("birth_date", "1815-12-10"),
            disclosure("$.given_name", "Ada", required = false)))
        val review = WalletDemoSharingReview(WalletDemoSharingRequest(null), listOf(required, optional))
        val group = review.informationToShare(setOf(required.selection, optional.selection), emptySet()).single()
        assertEquals(2, group.options.size)
        assertEquals(2, group.fields.size)
        val name = group.fields.first { it.item.pathComponents == listOf("given_name") }
        assertTrue(name.included)
        assertTrue(name.optionalSelections.isEmpty())
        assertEquals(setOf("name", "optional-name"), name.selections.map { it.queryId }.toSet())
    }

    @Test fun unavoidablePersonalFieldsAreVisibleWithoutUnrequestedProtocolMetadata() {
        val option = option(disclosures = listOf(disclosure("given_name", "Ada"),
            disclosure("family_name", "Lovelace", required = false, selective = false, requested = false),
            disclosure("iss", "Issuer", required = false, selective = false, requested = false)))
        val fields = option.informationFields(emptySet())
        assertEquals(2, fields.size)
        assertTrue(fields.last().alwaysIncluded)
        assertTrue(fields.last().included)
        assertEquals(listOf("Family name"), option.additionalInformationLabels())

    }

    @Test fun nestedAndLiteralKeysWithTheSameLeafAreNotMerged() {
        val options = listOf(
            option(query = "nested", disclosures = listOf(disclosure("[\"address\",\"name\"]", "Vienna"))),
            option(query = "name", disclosures = listOf(disclosure("name", "Ada"))),
            option(query = "literal", disclosures = listOf(disclosure("[\"address.name\"]", "Literal"))),
        )
        val fields = WalletDemoSharingReview(WalletDemoSharingRequest(null), options)
            .informationToShare(options.map { it.selection }.toSet(), emptySet()).single().fields
        assertEquals(3, fields.size)
        assertEquals(setOf("Vienna", "Ada", "Literal"), fields.map { it.item.rawValue?.trim('"') }.toSet())
    }

    @Test fun optionalClaimValuesStayVisibleButAreExplicitlyExcludedUntilSelected() {
        val option = option(disclosures = listOf(disclosure("birth_date", "1815-12-10", required = false)))
        val initial = option.informationFields(emptySet()).single()
        assertFalse(initial.included)
        val choice = initial.optionalSelections.single()
        assertEquals(option.queryId, choice.queryId)
        assertTrue(option.informationFields(setOf(choice)).single().included)
    }

    @Test fun anOptionalRequestedClearTextClaimIsNotLabelledAsAdditionalData() {
        val option = option(disclosures = listOf(disclosure("given_name", "Ada", required = false, selective = false)))
        val field = option.informationFields(emptySet()).single()
        assertTrue(field.included)
        assertFalse(field.alwaysIncluded)
        assertTrue(option.additionalInformationLabels().isEmpty())
    }
}
