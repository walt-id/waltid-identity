package id.walt.holderpolicies

import id.walt.credentials.CredentialDetectorTypes.SDJWTVCSubType
import id.walt.credentials.formats.SdJwtCredential
import id.walt.holderpolicies.HolderPolicy.HolderPolicyAction
import id.walt.holderpolicies.HolderPolicy.HolderPolicyAction.ALLOW
import id.walt.holderpolicies.HolderPolicy.HolderPolicyAction.BLOCK
import id.walt.holderpolicies.checks.BasicHolderPolicyCheck
import id.walt.holderpolicies.checks.DisclosedClaimsHolderPolicyCheck
import id.walt.holderpolicies.checks.HolderPolicyCheck
import id.walt.holderpolicies.checks.HolderPolicyMatchQuantifier.ALL
import id.walt.holderpolicies.checks.HolderPolicyMatchQuantifier.ANY
import id.walt.holderpolicies.checks.TransactionDataHolderPolicyCheck
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PresentationHolderPolicyTest {
    @Test
    fun `disclosed checks scope and quantify the claims of each selected credential`() = runTest {
        val block = Json.decodeFromString<HolderPolicy>(
            """
            {
              "priority":1,
              "direction":"PRESENT",
              "check":{
                "type":"disclosed_claims",
                "match":"any",
                "credential_id":"identity",
                "claims_present":["$.address.locality"],
                "claims_values":{"$.identity.age":42}
              },
              "action":"BLOCK"
            }
            """.trimIndent(),
        )
        val selected = listOf(
            disclosed("identity", """{"address":{"locality":"Vienna"}}"""),
            disclosed("identity", """{"identity":{"age":42}}"""),
            disclosed("other", """{"address":{"locality":"Vienna"},"identity":{"age":42}}"""),
        )
        val originalCredential = SdJwtCredential(
            dmtype = SDJWTVCSubType.sdjwtvc,
            credentialData = selected.last().claims!!,
            signature = null,
            signed = null,
        )
        val claimsMatch = disclosed("identity", """{"address":{"locality":"Vienna"},"identity":{"age":42}}""")

        val policies = flowOf(block, fallback(ALLOW))
        assertEquals(ALLOW, HolderPolicyEngine.evaluate(policies, flowOf(originalCredential), evidence(selected)))
        assertEquals(BLOCK, HolderPolicyEngine.evaluate(policies, flowOf(originalCredential), evidence(selected + claimsMatch)))
        val allowAll = block.copy(check = (block.check as DisclosedClaimsHolderPolicyCheck).copy(match = ALL), action = ALLOW)
        assertEquals(BLOCK, HolderPolicyEngine.evaluate(flowOf(allowAll, fallback(BLOCK)), emptyFlow(), evidence(selected + claimsMatch)))
        assertEquals(ALLOW, HolderPolicyEngine.evaluate(flowOf(allowAll, fallback(BLOCK)), emptyFlow(), evidence(listOf(claimsMatch))))
    }

    @Test
    fun `transaction checks quantify all items in binding scope and preserve JSON value types`() = runTest {
        val allow = Json.decodeFromString<HolderPolicy>(
            """
            {
              "priority":1,
              "direction":"PRESENT",
              "check":{
                "type":"transaction_data",
                "match":"all",
                "credential_id":"payment_credential",
                "transaction_type":"org.waltid.transaction-data.payment-authorization",
                "fields_present":["$.payment.payee"],
                "fields_values":{"$.payment.amount":42,"$.currency":"EUR"}
              },
              "action":"ALLOW"
            }
            """.trimIndent(),
        )
        val approvedFields = """{"payment":{"payee":"ACME Corp","amount":42},"currency":"EUR"}"""
        val wrongValueType = """{"payment":{"payee":"ACME Corp","amount":"42"},"currency":"EUR"}"""
        val approved = transaction(approvedFields)
        val wrongQuery = transaction(wrongValueType, credentialId = "other")
        val wrongType = transaction(approvedFields, type = "com.example.other-transaction")
        val wrongAmount = transaction(wrongValueType)
        val allowPolicies = flowOf(allow, fallback(BLOCK))

        assertEquals(ALLOW, evaluate(allowPolicies, transactions = listOf(approved, wrongQuery)))
        assertEquals(BLOCK, evaluate(allowPolicies, transactions = listOf(approved, wrongType)))
        assertEquals(BLOCK, evaluate(allowPolicies, transactions = listOf(approved, wrongAmount)))
        assertEquals(BLOCK, evaluate(allowPolicies, transactions = emptyList()))

        val condition = allow.check as TransactionDataHolderPolicyCheck
        val block = allow.copy(check = condition.copy(match = ANY), action = BLOCK)
        val blockPolicies = flowOf(block, fallback(ALLOW))
        assertEquals(BLOCK, evaluate(blockPolicies, transactions = listOf(wrongAmount, approved)))
        assertEquals(ALLOW, evaluate(blockPolicies, transactions = listOf(
            transaction("""{"payment":{"payee":"ACME Corp"},"currency":"EUR"}"""),
            transaction("""{"payment":{"amount":42},"currency":"EUR"}"""),
        )))
    }

    @Test
    fun `unavailable scoped disclosures fail closed and known absence remains a nonmatch`() = runTest {
        val check = DisclosedClaimsHolderPolicyCheck(
            match = ANY, credentialId = "identity", claimsPresent = listOf("$.secret"),
        )
        val block = HolderPolicy(priority = 1, action = BLOCK, apply = check)
        val allow = HolderPolicy(priority = 1, action = ALLOW, check = check)
        val unknownIdentity = HolderPolicyDisclosedCredential("identity", claims = null)
        val knownAbsent = disclosed("identity", """{"given_name":"Alice"}""")
        val disclosedSecret = disclosed("identity", """{"secret":"shown"}""")

        val blockPolicies = flowOf(block, fallback(ALLOW))
        assertEquals(BLOCK, evaluate(blockPolicies))
        assertEquals(BLOCK, evaluate(blockPolicies, listOf(knownAbsent, unknownIdentity)))
        assertEquals(ALLOW, evaluate(blockPolicies, listOf(knownAbsent)))
        assertEquals(ALLOW, evaluate(blockPolicies, emptyList()))
        assertEquals(ALLOW, evaluate(blockPolicies, listOf(unknownIdentity.copy(credentialId = "other"))))
        assertEquals(BLOCK, evaluate(flowOf(allow, fallback(BLOCK)), listOf(unknownIdentity)))

        val all = block.copy(apply = check.copy(match = ALL))
        assertEquals(BLOCK, evaluate(flowOf(all, fallback(ALLOW)), listOf(disclosedSecret, unknownIdentity)))
        assertEquals(ALLOW, evaluate(flowOf(all, fallback(ALLOW)), listOf(disclosedSecret, unknownIdentity, knownAbsent)))
        assertEquals(ALLOW, evaluate(flowOf(all, fallback(ALLOW)), emptyList()))

        assertEquals(BLOCK, HolderPolicyEngine.evaluate(blockPolicies, emptyFlow()))
    }

    @Test
    fun `unavailable transaction evidence respects apply and check conditions`() = runTest {
        val check = TransactionDataHolderPolicyCheck(match = ANY, fieldsPresent = listOf("$.amount"))
        val block = HolderPolicy(priority = 1, check = check, action = BLOCK)
        val allow = block.copy(action = ALLOW)
        val policies = flowOf(block, fallback(ALLOW))

        assertEquals(BLOCK, evaluate(policies))
        assertEquals(ALLOW, evaluate(policies, transactions = emptyList()))
        assertEquals(BLOCK, evaluate(flowOf(allow, fallback(BLOCK))))
        val outOfScope = block.copy(apply = DisclosedClaimsHolderPolicyCheck(ANY, claimsPresent = listOf("$.other")))
        assertEquals(ALLOW, evaluate(flowOf(outOfScope, fallback(ALLOW)), disclosures = emptyList()))
    }

    @Test
    fun `credential only evaluation keeps contextual blocks and skips unavailable allows`() = runTest {
        val check = TransactionDataHolderPolicyCheck(match = ANY, fieldsPresent = listOf("$.amount"))
        val block = HolderPolicy(priority = 1, check = check, action = BLOCK)
        val allow = block.copy(action = ALLOW)

        assertEquals(BLOCK, HolderPolicyEngine.evaluate(flowOf(block, fallback(ALLOW)), emptyFlow()))
        assertEquals(BLOCK, HolderPolicyEngine.evaluate(flowOf(allow, fallback(BLOCK)), emptyFlow()))
    }

    @Test
    fun `whole credential checks avoid projection and contextual checks share one projection`() = runTest {
        val credential = SdJwtCredential(
            dmtype = SDJWTVCSubType.sdjwtvc,
            credentialData = json("""{"secret":"stored-only"}"""),
            signature = null,
            signed = null,
        )
        var projections = 0
        val presentation = HolderPolicyPresentationContext(
            disclosures = {
                projections++
                listOf(disclosed("identity", """{"given_name":"Alice"}"""))
            },
        )
        val legacyRule = HolderPolicy(priority = 1, check = BasicHolderPolicyCheck(claimsPresent = listOf("$.secret")), action = BLOCK)
        assertEquals(BLOCK, HolderPolicyEngine.evaluate(flowOf(legacyRule), flowOf(credential), presentation))
        assertEquals(0, projections)

        val contextRule = HolderPolicy(
            priority = 1,
            apply = DisclosedClaimsHolderPolicyCheck(ANY, claimsPresent = listOf("$.given_name")),
            check = DisclosedClaimsHolderPolicyCheck(ANY, claimsValues = mapOf("$.given_name" to JsonPrimitive("Alice"))),
            action = ALLOW,
        )
        assertEquals(ALLOW, HolderPolicyEngine.evaluate(flowOf(contextRule), flowOf(credential), presentation))
        assertEquals(1, projections)
    }

    @Test
    fun `contextual rules require a quantifier and meaningful disclosure paths`() {
        assertFailsWith<SerializationException> {
            Json.decodeFromString<HolderPolicyCheck>("""{"type":"transaction_data","fields_present":["$.amount"]}""")
        }
        assertFailsWith<IllegalArgumentException> {
            Json.decodeFromString<HolderPolicyCheck>("""{"type":"disclosed_claims","match":"any"}""")
        }
        assertFailsWith<IllegalArgumentException> {
            DisclosedClaimsHolderPolicyCheck(ANY, claimsPresent = listOf(" "))
        }
    }

    private suspend fun evaluate(
        policies: Flow<HolderPolicy>,
        disclosures: List<HolderPolicyDisclosedCredential>? = null,
        transactions: List<HolderPolicyTransactionData>? = null,
    ): HolderPolicyAction? = HolderPolicyEngine.evaluate(policies, emptyFlow(), evidence(disclosures, transactions))

    private fun evidence(
        disclosures: List<HolderPolicyDisclosedCredential>?,
        transactions: List<HolderPolicyTransactionData>? = null,
    ) = HolderPolicyPresentationContext(disclosures = { disclosures }, transactionData = transactions)

    private fun disclosed(credentialId: String, claims: String) = HolderPolicyDisclosedCredential(credentialId, json(claims))

    private fun transaction(
        data: String,
        credentialId: String = "payment_credential",
        type: String = "org.waltid.transaction-data.payment-authorization",
    ) = HolderPolicyTransactionData(type, setOf(credentialId), json(data))

    private fun fallback(action: HolderPolicyAction) = HolderPolicy(priority = 99, action = action)

    private fun json(value: String): JsonObject = Json.parseToJsonElement(value).jsonObject
}
