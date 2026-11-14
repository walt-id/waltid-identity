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
        assertEquals(listOf("Ada"), review.credentialOptions.map { it.toSharingInformation() }.informationToShare(setOf(first.selection), emptySet()).single().fields.map { it.item.rawValue?.trim('"') })
        assertEquals(listOf("Grace"), review.credentialOptions.map { it.toSharingInformation() }.informationToShare(setOf(second.selection), emptySet()).single().fields.map { it.item.rawValue?.trim('"') })
        assertTrue(review.credentialOptions.map { it.toSharingInformation() }.informationToShare(emptySet(), emptySet()).isEmpty())
    }

    @Test fun sameCredentialUnionIsShownOnceAndRequiredDisclosureCannotBeDeselectedThroughAnotherQuery() {
        val required = option(query = "name", disclosures = listOf(disclosure("given_name", "Ada")))
        val optional = option(query = "optional-name", disclosures = listOf(disclosure("birth_date", "1815-12-10"),
            disclosure("$.given_name", "Ada", required = false)))
        val review = WalletDemoSharingReview(WalletDemoSharingRequest(null), listOf(required, optional))
        val group = review.credentialOptions.map { it.toSharingInformation() }.informationToShare(setOf(required.selection, optional.selection), emptySet()).single()
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
        val fields = option.toSharingInformation().fields
        assertEquals(2, fields.size)
        assertTrue(fields.last().alwaysIncluded)
        assertTrue(fields.last().included)
        assertEquals(listOf("Family name"), option.toSharingInformation().additionalInformationLabels)

    }

    @Test fun nestedAndLiteralKeysWithTheSameLeafAreNotMerged() {
        val options = listOf(
            option(query = "nested", disclosures = listOf(disclosure("[\"address\",\"name\"]", "Vienna"))),
            option(query = "name", disclosures = listOf(disclosure("name", "Ada"))),
            option(query = "literal", disclosures = listOf(disclosure("[\"address.name\"]", "Literal"))),
        )
        val fields = WalletDemoSharingReview(WalletDemoSharingRequest(null), options)
            .credentialOptions.map { it.toSharingInformation() }.informationToShare(options.map { it.selection }.toSet(), emptySet()).single().fields
        assertEquals(3, fields.size)
        assertEquals(3, fields.map { it.path }.distinct().size)
        val reversed = options.reversed().map { it.toSharingInformation() }
            .informationToShare(options.map { it.selection }.toSet(), emptySet()).single().fields
        assertEquals(fields.associate { it.item.rawValue to it.path }, reversed.associate { it.item.rawValue to it.path })
        assertEquals(setOf("Vienna", "Ada", "Literal"), fields.map { it.item.rawValue?.trim('"') }.toSet())
    }

    @Test fun optionalClaimValuesStayVisibleButAreExplicitlyExcludedUntilSelected() {
        val option = option(disclosures = listOf(disclosure("birth_date", "1815-12-10", required = false)))
        val initial = option.toSharingInformation().fields.single()
        assertFalse(initial.included)
        val choice = initial.optionalSelections.single()
        assertEquals(option.queryId, choice.queryId)
        assertTrue(listOf(option.toSharingInformation()).informationToShare(setOf(option.selection), setOf(choice)).single().fields.single().included)
    }

    @Test fun anOptionalRequestedClearTextClaimIsNotLabelledAsAdditionalData() {
        val option = option(disclosures = listOf(disclosure("given_name", "Ada", required = false, selective = false)))
        val field = option.toSharingInformation().fields.single()
        assertTrue(field.included)
        assertFalse(field.alwaysIncluded)
        assertTrue(option.toSharingInformation().additionalInformationLabels.isEmpty())
    }
    @Test fun effectiveTreeUsesOriginalMetadataIndexesAndNeverFillsWithheldSiblings() {
        val country = "\"nationalities\".1.\"country\""
        val base = option(disclosures = listOf(disclosure(country, "DE"))).copy(
            credentialDataJson = """{"nationalities":["IN",{"kind":"residency","country":"DE","since":2015}]}""",
            metadataJson = """{"credentialClaims":[
                {"path":["nationalities",0,"country"],"display":[{"name":"Wrong country"}]},
                {"path":["nationalities",1,"country"],"display":[{"name":"Resident country"}]}]}""",
        )
        fun location(source: String, received: String) = WalletDemoClaimLocation(source, received, listOf(country), false)
        val contribution = WalletDemoCredentialClaims("pid", "one", "dc+sd-jwt",
            """{"nationalities":[{"kind":"residency","country":"DE"}]}""", false, listOf(
                location("[]", "[]"), location("""["nationalities"]""", """["nationalities"]"""),
                location("""["nationalities",1]""", """["nationalities",0]"""),
                location("""["nationalities",1,"kind"]""", """["nationalities",0,"kind"]"""),
                location("""["nationalities",1,"country"]""", """["nationalities",0,"country"]"""),
            ))
        val projected = WalletDemoPreparedClaimReview(listOf(contribution))
        val array = projected.toEffectiveSharingInformation(listOf(base), emptySet()).single().contributions.single().nodes.single()
        assertEquals(SharingClaimNode.Kind.Array, array.kind)
        assertTrue(array.initiallyExpanded)
        val row = array.children.single()
        assertEquals("nationalities[1]", row.path.id)
        assertEquals(setOf("Kind", "Resident country"), row.children.map { it.label }.toSet())
        assertEquals(DisplayValue.Text("DE"), row.children.single { it.label == "Resident country" }.value)
        assertEquals("Shared with Resident country", row.children.single { it.label == "Kind" }.reason)
        assertTrue(row.children.none { it.label == "Since" })
        val optionalKind = base.copy(disclosures = base.disclosures + disclosure("\"nationalities\".1.\"kind\"", "residency", required = false))
        val choice = projected.toEffectiveSharingInformation(listOf(optionalKind), emptySet()).single().choices.single()
        assertFalse(choice.selected)
        assertTrue(choice.included)
        assertEquals("Included with Resident country", choice.forcedReason)
    }

    @Test fun partialArraysFromDifferentQueriesRemainDistinctAndMissingIdentitiesFailClosed() {
        val a = option(query = "a", disclosures = listOf(disclosure("\"nationalities\".0", "IN")))
        val b = option(query = "b", disclosures = listOf(disclosure("\"nationalities\".2", "FR")))
        fun contribution(query: String, value: String, sourceIndex: Int) = WalletDemoCredentialClaims(query, "one", "dc+sd-jwt",
            """{"nationalities":["$value"]}""", false, listOf(
                WalletDemoClaimLocation("[]", "[]", emptyList(), true),
                WalletDemoClaimLocation("""["nationalities"]""", """["nationalities"]""", emptyList(), true),
                WalletDemoClaimLocation("""["nationalities",$sourceIndex]""", """["nationalities",0]""", listOf("\"nationalities\".$sourceIndex"), false),
            ))
        val review = WalletDemoPreparedClaimReview(listOf(contribution("a", "IN", 0), contribution("b", "FR", 2)))
        val group = review.toEffectiveSharingInformation(listOf(a, b), emptySet()).single()
        assertEquals(2, group.contributions.size)
        assertEquals(listOf(DisplayValue.Text("IN"), DisplayValue.Text("FR")),
            group.contributions.map { it.nodes.single().children.single().value })
        assertFailsWith<IllegalArgumentException> {
            review.copy(credentials = listOf(review.credentials.first().copy(locations = emptyList())))
                .toEffectiveSharingInformation(listOf(a), emptySet())
        }
    }

    @Test fun sdkDisclosurePathsKeepQuotedLiteralKeysSeparateFromIndexesAndWildcards() {
        assertEquals(listOf(ClaimPathExpression.Segment.Key("0"), ClaimPathExpression.Segment.Index(0),
            ClaimPathExpression.Segment.Key(" "), ClaimPathExpression.Segment.Key("")),
            ClaimPathExpression.parseSdkDisclosure("\"0\".0.\" \".\"\"").segments)
        assertEquals(listOf(ClaimPathExpression.Segment.Key("nationalities"), ClaimPathExpression.Segment.Wildcard),
            ClaimPathExpression.parseSdkDisclosure("\"nationalities\".null").segments)
    }

    @Test fun optionalRequirementsSharingTheSameValueDoNotLockEachOtherOn() {
        val a = option(query = "a", disclosures = listOf(disclosure("name", "Ada", required = false)))
        val b = option(query = "b", disclosures = listOf(disclosure("name", "Ada", required = false)))
        fun contribution(query: String) = WalletDemoCredentialClaims(query, "one", "dc+sd-jwt", """{"name":"Ada"}""", false,
            listOf(WalletDemoClaimLocation("[]", "[]", emptyList(), true),
                WalletDemoClaimLocation("""["name"]""", """["name"]""", listOf("name"), false)))
        val selected = setOf(WalletDemoPresentationDisclosureSelection("a", "one", "name"),
            WalletDemoPresentationDisclosureSelection("b", "one", "name"))
        val review = WalletDemoPreparedClaimReview(listOf(contribution("a"), contribution("b")))
        val group = review.toEffectiveSharingInformation(listOf(a, b), selected).single()
        assertTrue(group.choices.all { it.forcedReason == null })
        assertEquals(2, group.contributions.size)
        assertEquals(setOf("a", "b"), group.contributions.map { it.nodes.single().choice!!.selection.queryId }.toSet())
        val offInA = review.copy(credentials = listOf(contribution("a").copy(claimsJson = "{}",
            locations = listOf(WalletDemoClaimLocation("[]", "[]", emptyList(), true))), contribution("b")))
            .toEffectiveSharingInformation(listOf(a, b), selected.filterNot { it.queryId == "a" }.toSet()).single()
        assertEquals("Included by another selected requirement", offInA.choices.single { it.selection.queryId == "a" }.forcedReason)
        assertNull(offInA.choices.single { it.selection.queryId == "b" }.forcedReason)
    }

    @Test fun protocolMetadataIsInspectableWithoutHidingSimilarlyNamedBusinessClaims() {
        val option = option(disclosures = listOf(disclosure("\"employment\".\"iat\"", "Business note", selective = false)))
        val paths = listOf("[]", """["iat"]""", """["_sd_alg"]""", """["employment"]""", """["employment","iat"]""")
        val contribution = WalletDemoCredentialClaims("pid", "one", "dc+sd-jwt",
            """{"iat":1700000000,"_sd_alg":"sha-256","employment":{"iat":"Business note"}}""", false,
            paths.map { WalletDemoClaimLocation(it, it, emptyList(), true) })
        val nodes = WalletDemoPreparedClaimReview(listOf(contribution))
            .toEffectiveSharingInformation(listOf(option), emptySet()).single().contributions.single().nodes
        assertTrue(nodes.single { it.path.id == "iat" }.technical)
        assertTrue(nodes.single { it.path.id == "_sd_alg" }.technical)
        val business = nodes.single { it.path.id == "employment" }.children.single()
        assertFalse(business.technical)
        assertEquals(DisplayValue.Text("Business note"), business.value)
    }

    @Test fun sharingSectionsPreserveParentsAndSeparateOnlyValuesOutsideDisclosures() {
        val city = "\"address\".\"city\""
        val postal = "\"address\".\"postal_code\""
        val option = option(disclosures = listOf(disclosure(city, "Bengaluru"), disclosure(postal, "560038", required = false)))
        val alwaysPaths = listOf("[]", "[\"address\"]", "[\"address\",\"country\"]", "[\"empty_object\"]", "[\"empty_array\"]")
        val conditionalPaths = listOf("[\"address\",\"city\"]", "[\"address\",\"verified\"]", "[\"address\",\"extra\"]")
        val projection = WalletDemoCredentialClaims("pid", "one", "dc+sd-jwt",
            """{"address":{"country":"IN","city":"Bengaluru","verified":false,"extra":{}},"empty_object":{},"empty_array":[]}""", false,
            alwaysPaths.map { WalletDemoClaimLocation(it, it, emptyList(), true) } +
                conditionalPaths.map { WalletDemoClaimLocation(it, it, listOf(city), false) })
        val contribution = WalletDemoPreparedClaimReview(listOf(projection))
            .toEffectiveSharingInformation(listOf(option), emptySet()).single().contributions.single()
        assertTrue(contribution.separatesAlwaysShared)
        val always = contribution.nodes.sharingSection(alwaysShared = true)
        val requested = contribution.nodes.sharingSection(alwaysShared = false)
        val alwaysAddress = always.single { it.path.id == "address" }
        val requestedAddress = requested.single()
        assertEquals("address", requestedAddress.path.id)
        assertEquals(listOf("address.country"), alwaysAddress.children.map { it.path.id })
        assertEquals(listOf("address.city", "address.verified", "address.extra", "address.postal_code"), requestedAddress.children.map { it.path.id })
        assertNull(alwaysAddress.children.single().reason)
        assertEquals("Required", requestedAddress.children.single { it.path.id == "address.city" }.reason)
        val collateral = requestedAddress.children.single { it.path.id == "address.verified" }
        assertEquals("Shared with City", collateral.reason)
        assertEquals(DisplayValue.BooleanValue(false), collateral.value)
        assertFalse(collateral.alwaysShared)
        assertEquals(emptyList(), requestedAddress.children.single { it.path.id == "address.extra" }.children)
        assertFalse(requestedAddress.children.single { it.path.id == "address.extra" }.contextOnly)
        assertTrue(requestedAddress.children.last().contextOnly)
        assertFalse(requestedAddress.children.last().choice!!.selected)
        assertEquals(setOf("empty_object", "empty_array"), always.filter { it.children.isEmpty() }.map { it.path.id }.toSet())
        assertTrue(always.all { it.reason == null })
        assertTrue(alwaysAddress.contextOnly)
        assertTrue(requestedAddress.contextOnly)
    }

    @Test fun allClearCredentialHasOnlyAlwaysSharedValuesAndSplitArraysKeepSourceIndexes() {
        fun node(path: ClaimItemPath, always: Boolean, children: List<SharingClaimNode> = emptyList(), kind: SharingClaimNode.Kind = SharingClaimNode.Kind.Scalar) =
            SharingClaimNode(path, path.id, kind, if (kind == SharingClaimNode.Kind.Scalar) DisplayValue.Text(path.id) else null,
                children, "Always included by this credential", false, false, true, alwaysShared = always)
        val allClear = listOf(node(ClaimItemPath.topLevel("address"), true, listOf(node(ClaimItemPath.topLevel("address").child("country"), true)), SharingClaimNode.Kind.Object))
        assertTrue(allClear.sharingSection(alwaysShared = false).isEmpty())
        assertEquals("address.country", allClear.sharingSection(alwaysShared = true).single().children.single().path.id)
        val mixed = listOf(node(ClaimItemPath.topLevel("nationalities"), true, listOf(node(ClaimItemPath.topLevel("nationalities").indexedChild(0), false), node(ClaimItemPath.topLevel("nationalities").indexedChild(2), true)), SharingClaimNode.Kind.Array))
        assertEquals("nationalities[0]", mixed.sharingSection(alwaysShared = false).single().children.single().path.id)
        assertEquals("nationalities[2]", mixed.sharingSection(alwaysShared = true).single().children.single().path.id)
    }

    @Test fun requestedContainerChildrenAreNotMislabelledAsAdditionalSharedClaims() {
        for (path in listOf("\"certifications\".1", "\"certifications\".null")) {
            val option = option(disclosures = listOf(WalletDemoPresentationDisclosure("Cloud security", path,
                """{"name":"Cloud security","issuer":"Example Training"}""", selectivelyDisclosable = true, required = true)))
            val paths = listOf("[]" to "[]", "[\"certifications\"]" to "[\"certifications\"]",
                "[\"certifications\",1]" to "[\"certifications\",0]",
                "[\"certifications\",1,\"name\"]" to "[\"certifications\",0,\"name\"]",
                "[\"certifications\",1,\"issuer\"]" to "[\"certifications\",0,\"issuer\"]")
            val projection = WalletDemoCredentialClaims("pid", "one", "dc+sd-jwt",
                """{"certifications":[{"name":"Cloud security","issuer":"Example Training"}]}""", false,
                paths.map { (source, received) -> WalletDemoClaimLocation(source, received, listOf(path), false) })
            val certificate = WalletDemoPreparedClaimReview(listOf(projection))
                .toEffectiveSharingInformation(listOf(option), emptySet()).single().contributions.single().nodes.single().children.single()
            assertEquals("Required", certificate.reason)
            assertTrue(certificate.children.all { !it.additional }, path)
            assertTrue(certificate.children.all { it.reason == null }, path)
        }
    }

    @Test fun optionalObjectsKeepTypedStructureAndSourcePathsWhenOffAndOn() {
        val path = "\"certifications\".2"
        val value = """{"name":"First aid","score":0,"valid":false,"note":null}"""
        val option = option(disclosures = listOf(WalletDemoPresentationDisclosure("First aid", path, value,
            selectivelyDisclosable = true, required = false, selectable = true))).copy(
                metadataJson = """{"credentialClaims":[{"path":["certifications",2],"display":[{"name":"First aid"}]}]}""")
        val selection = WalletDemoPresentationDisclosureSelection("pid", "one", path)
        fun view(on: Boolean): SharingClaimNode {
            val locations = mutableListOf(WalletDemoClaimLocation("[]", "[]", emptyList(), true))
            if (on) {
                locations += WalletDemoClaimLocation("[\"certifications\"]", "[\"certifications\"]", emptyList(), true)
                locations += WalletDemoClaimLocation("[\"certifications\",2]", "[\"certifications\",0]", listOf(path), false)
                for (key in listOf("name", "score", "valid", "note")) locations += WalletDemoClaimLocation(
                    "[\"certifications\",2,\"$key\"]", "[\"certifications\",0,\"$key\"]", listOf(path), false)
            }
            val projection = WalletDemoCredentialClaims("pid", "one", "dc+sd-jwt",
                if (on) "{\"certifications\":[$value]}" else "{}", false, locations)
            return WalletDemoPreparedClaimReview(listOf(projection)).toEffectiveSharingInformation(listOf(option),
                if (on) setOf(selection) else emptySet()).single().contributions.single().nodes
                .sharingSection(alwaysShared = false).single().children.single()
        }
        for (on in listOf(false, true, false)) {
            val node = view(on)
            assertEquals(SharingClaimNode.Kind.Object, node.kind)
            assertEquals("certifications[2]", node.path.id)
            assertEquals("First aid", node.label)
            assertEquals(!on, node.contextOnly)
            assertEquals(on, node.choice!!.selected)
            assertEquals(listOf("certifications[2].name", "certifications[2].score", "certifications[2].valid", "certifications[2].note"), node.children.map { it.path.id })
            assertEquals(DisplayValue.NumberValue("0"), node.children[1].value)
            assertEquals(DisplayValue.BooleanValue(false), node.children[2].value)
            assertEquals(DisplayValue.NullValue, node.children[3].value)
            assertTrue(node.children.all { it.reason == null })
        }
    }

}
