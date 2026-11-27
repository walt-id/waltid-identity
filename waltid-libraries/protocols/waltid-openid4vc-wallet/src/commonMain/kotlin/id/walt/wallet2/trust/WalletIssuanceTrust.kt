package id.walt.wallet2.trust

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.formats.SdJwtCredential
import id.walt.credentials.signatures.JwtBasedSignature
import id.walt.credentials.trust.IssuerTrustMaterial
import id.walt.credentials.trust.IssuerTrustMaterialException
import id.walt.credentials.trust.verifiedIssuerTrustMaterial
import id.walt.crypto2.jose.CompactJws
import id.walt.mdoc.verification.verifyIssuerSignedItemDigests
import id.walt.trust.decision.TrustCheck
import id.walt.trust.decision.TrustDecisionEvaluator
import id.walt.trust.decision.TrustDecisionRequest
import id.walt.trust.decision.TrustPurpose
import id.walt.trust.decision.TrustRequirement
import id.walt.trust.decision.TrustSubject
import id.walt.trust.decision.check
import id.walt.trust.model.TrustDecision
import id.walt.trust.model.TrustDecisionCode
import id.walt.trust.model.TrustedEntityType
import id.walt.wallet2.handlers.CredentialIssuanceFailureReason
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

/**
 * Issuer trust for credentials received through OpenID4VCI. Attach it with
 * [id.walt.wallet2.data.Wallet.attachIssuanceTrust]; without it the wallet stores what an issuer returns
 * without checking the issuer.
 *
 * With it, every credential of a response must pass before any is stored:
 * 1. The credential signature verifies with the issuer key, and an mdoc's value digests match its MSO.
 * 2. That key is certified by an X.509 chain. A DID- or HTTPS-key-signed credential has none and is rejected.
 * 3. [evaluator] trusts the chain for [TrustPurpose.WALLET_ISSUANCE_ISSUER_CERT] as one of the entity types
 *    the signed credential type maps to, from a fresh registry source ([TrustRequirement.RegistryFresh]).
 *
 * The expected entity types come only from this configuration, never from the offer, the issuer metadata or
 * the credential's own claims beyond its signed type.
 *
 * @property credentialTypes rules from a signed credential type to the registry roles allowed to issue it.
 *   The first rule naming the type applies; types match exactly.
 * @property otherCredentialTypes the roles for a credential no rule names, including a W3C VC (which has
 *   no `vct` or doctype). Empty rejects every credential that no rule names.
 * @property requireAuthenticatedSource additionally require an authenticated trust source.
 */
class WalletIssuanceTrust(
    val evaluator: TrustDecisionEvaluator,
    credentialTypes: List<IssuerEntityTypeRule> = DEFAULT_CREDENTIAL_TYPES,
    otherCredentialTypes: Set<TrustedEntityType> = DEFAULT_OTHER_CREDENTIAL_TYPES,
    val requireAuthenticatedSource: Boolean = false,
) {
    val credentialTypes: List<IssuerEntityTypeRule> = buildList { addAll(credentialTypes) }
    val otherCredentialTypes: Set<TrustedEntityType> = buildSet { addAll(otherCredentialTypes) }

    /** The registry roles that may issue a credential of this signed `vct` or doctype. */
    fun entityTypesFor(vct: String?, docType: String?): Set<TrustedEntityType> =
        credentialTypes.firstOrNull { rule -> (vct != null && vct in rule.vcts) || (docType != null && docType in rule.doctypes) }
            ?.entityTypes
            ?: otherCredentialTypes

    /**
     * Verifies the issuer of [credential] and asks [evaluator] about its certificate chain.
     *
     * @throws IssuerNotTrustedException if the signature does not verify, the issuer has no certificate chain,
     *   no registry role is configured for the type, the trust source cannot answer, or the decision rejects it.
     */
    suspend fun requireTrustedIssuer(credential: DigitalCredential): TrustDecision {
        // A JWT without x5c could only yield a public key, so reject it before its DID or HTTPS issuer key is fetched.
        val signature = credential.signature
        if (signature is JwtBasedSignature && signature.jwtHeader?.get("x5c") !is JsonArray) throw IssuerNotTrustedException(NO_CHAIN)
        val material = try {
            credential.verifiedIssuerTrustMaterial()
        } catch (e: IssuerTrustMaterialException) {
            throw IssuerNotTrustedException("Issuer signature could not be verified: ${e.message}", cause = e)
        }
        val chain = material as? IssuerTrustMaterial.CertificateChain ?: throw IssuerNotTrustedException(NO_CHAIN)
        val (vct, docType) = try {
            signedType(credential)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IssuerNotTrustedException) {
            throw e
        } catch (e: Exception) {
            throw IssuerNotTrustedException("The signed credential type could not be read: ${e.message}", cause = e)
        }
        val entityTypes = entityTypesFor(vct, docType)
        if (entityTypes.isEmpty()) {
            throw IssuerNotTrustedException(
                "No trusted issuer role is configured for credential type ${vct ?: docType ?: credential.format}"
            )
        }
        val request = TrustDecisionRequest(
            purpose = TrustPurpose.WALLET_ISSUANCE_ISSUER_CERT,
            subject = TrustSubject.CertificateChain(chain.pemChain),
            expectedEntityTypes = entityTypes,
        )
        val check = try {
            evaluator.check(request, TrustRequirement.RegistryFresh(requireAuthenticatedSource))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IssuerNotTrustedException(
                "Issuer trust could not be established, the trust source is unavailable: ${e.message}",
                cause = e, trustSourceUnavailable = true,
            )
        }
        return when (check) {
            is TrustCheck.Accepted -> check.decision
            is TrustCheck.Rejected -> {
                val noAnswer = check.decision?.decision in NO_ANSWER
                throw IssuerNotTrustedException(
                    if (noAnswer) "Issuer trust could not be established, the trust source gave no answer: ${check.reason}"
                    else "Issuer is not trusted as one of $entityTypes: ${check.reason}",
                    check.decision,
                    trustSourceUnavailable = noAnswer,
                )
            }
            TrustCheck.Skipped -> error("A registry trust requirement is never skipped")
        }
    }

    /**
     * The signed credential type: an SD-JWT VC's `vct` from its issuer-signed payload, which must equal the
     * parsed one, or an mdoc's MSO doctype after its value digests are checked. A W3C VC has neither.
     */
    private fun signedType(credential: DigitalCredential): Pair<String?, String?> = when (credential) {
        is SdJwtCredential -> {
            val signedPayload = Json.parseToJsonElement(
                CompactJws.decodeUnverified(requireNotNull(credential.signed).substringBefore('~')).payload.decodeToString()
            ).jsonObject
            val vct = (signedPayload["vct"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val parsedVct = credential.credentialData["vct"]?.jsonPrimitive?.content
            if (vct != parsedVct) throw IssuerNotTrustedException("The SD-JWT VC vct is not the issuer-signed vct")
            vct to null
        }

        is MdocsCredential -> {
            val mso = credential.documentMso
            try {
                verifyIssuerSignedItemDigests(credential.document, mso)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw IssuerNotTrustedException("mDoc issuer-signed data does not match its MSO: ${e.message}", cause = e)
            }
            null to mso.docType
        }

        else -> null to null
    }

    override fun toString(): String =
        "WalletIssuanceTrust(credentialTypes=$credentialTypes, otherCredentialTypes=$otherCredentialTypes, " +
            "requireAuthenticatedSource=$requireAuthenticatedSource)"

    companion object {
        /** EUDI PID, SD-JWT VC and mdoc, issued by a PID provider. */
        val PID: IssuerEntityTypeRule = IssuerEntityTypeRule(
            vcts = setOf("urn:eudi:pid:1"),
            doctypes = setOf("eu.europa.ec.eudi.pid.1"),
            entityTypes = setOf(TrustedEntityType.PID_PROVIDER),
        )

        val DEFAULT_CREDENTIAL_TYPES: List<IssuerEntityTypeRule> = listOf(PID)

        private const val NO_CHAIN =
            "The issuer key is not certified by an X.509 certificate chain, so a trust registry cannot vouch for it"

        /** Decisions in which the trust source could not judge the issuer, as opposed to judging it untrusted. */
        private val NO_ANSWER = setOf(TrustDecisionCode.UNKNOWN, TrustDecisionCode.PROCESSING_ERROR)

        /** Any other (Q)EAA is issued by an attestation provider. */
        val DEFAULT_OTHER_CREDENTIAL_TYPES: Set<TrustedEntityType> = setOf(TrustedEntityType.ATTESTATION_PROVIDER)
    }
}

/**
 * Credentials of these signed types must be issued by one of [entityTypes].
 *
 * @property vcts SD-JWT VC `vct` values, matched exactly.
 * @property doctypes mdoc doctypes, matched exactly.
 */
class IssuerEntityTypeRule(
    vcts: Set<String> = emptySet(),
    doctypes: Set<String> = emptySet(),
    entityTypes: Set<TrustedEntityType>,
) {
    val vcts: Set<String> = buildSet { addAll(vcts) }
    val doctypes: Set<String> = buildSet { addAll(doctypes) }
    val entityTypes: Set<TrustedEntityType> = buildSet { addAll(entityTypes) }

    init {
        require(this.vcts.isNotEmpty() || this.doctypes.isNotEmpty()) { "An issuer entity-type rule must name a vct or a doctype" }
        require(this.entityTypes.isNotEmpty()) { "An issuer entity-type rule must name at least one entity type" }
    }

    override fun equals(other: Any?): Boolean =
        other is IssuerEntityTypeRule && vcts == other.vcts && doctypes == other.doctypes && entityTypes == other.entityTypes

    override fun hashCode(): Int = (vcts.hashCode() * 31 + doctypes.hashCode()) * 31 + entityTypes.hashCode()

    override fun toString(): String = "IssuerEntityTypeRule(vcts=$vcts, doctypes=$doctypes, entityTypes=$entityTypes)"
}

/**
 * A received credential's issuer is not trusted, or its trust could not be established. Nothing is stored.
 *
 * @property trustSourceUnavailable the trust source or its configuration failed or gave no answer, so the issuer
 *   was not judged. Otherwise the credential or the decision rejected the issuer.
 */
class IssuerNotTrustedException(
    message: String,
    val decision: TrustDecision? = null,
    cause: Throwable? = null,
    val trustSourceUnavailable: Boolean = false,
) : Exception(message, cause) {
    /** The public reason of a receive that this rejection stopped. */
    val reason: CredentialIssuanceFailureReason
        get() = if (trustSourceUnavailable) CredentialIssuanceFailureReason.ISSUER_TRUST_UNAVAILABLE
        else CredentialIssuanceFailureReason.ISSUER_NOT_TRUSTED
}
