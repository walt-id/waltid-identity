package id.walt.issuer2.models

import id.walt.issuer2.domain.CredentialProfile
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal val REDACTED_ISSUER_KEY = buildJsonObject { put("type", "redacted") }

internal fun CredentialProfile.withRedactedIssuerKey(): CredentialProfile =
    copy(issuerKey = REDACTED_ISSUER_KEY)
