package id.walt.openid4vci.proofs

import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.proofs.jwt.JwtCredentialProofHandler
import kotlin.time.Clock
import kotlin.time.Instant

/** A fixed registry. Duplicate identifiers cannot silently replace security behavior. */
class CredentialProofHandlers(handlers: List<CredentialProofHandler>) {
    private val byType = handlers.associateBy { it.proofType }

    init {
        require(byType.size == handlers.size) { "Duplicate credential proof handler" }
    }

    val supportedTypes: Set<ProofType> get() = byType.keys.toSet()
    operator fun get(type: ProofType): CredentialProofHandler? = byType[type]

    fun validateConfiguration(configurations: Iterable<CredentialConfiguration>, capabilities: CredentialProofCapabilities) {
        configurations.forEach { configuration ->
            configuration.proofTypesSupported?.forEach { (type, metadata) ->
                val proofType = ProofType.fromValue(type)
                val handler = requireNotNull(proofType?.let { byType[it] }) { "No credential proof handler registered for $type" }
                handler.validateConfiguration(metadata, configuration, capabilities)
            }
        }
    }

    companion object {
        fun defaults(
            proofMaxAgeSeconds: Long = 300,
            clockSkewSeconds: Long = 60,
            now: () -> Instant = { Clock.System.now() },
        ) = CredentialProofHandlers(listOf(
            JwtCredentialProofHandler(proofMaxAgeSeconds, clockSkewSeconds, now),
            AttestationCredentialProofHandler(clockSkewSeconds, now),
        ))
    }
}
