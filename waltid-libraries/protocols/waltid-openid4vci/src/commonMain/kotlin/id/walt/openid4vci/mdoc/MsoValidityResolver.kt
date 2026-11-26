package id.walt.openid4vci.mdoc

import id.walt.credentials.issuance.MsoDataMapping
import id.walt.mdoc.issuance.MsoData as MdocMsoData
import id.walt.mdoc.issuance.ResolvedMsoValidity as MdocResolvedMsoValidity
import kotlin.time.Clock
import kotlin.time.Instant

@Deprecated("Use id.walt.mdoc.issuance.MsoValidUntilSource", ReplaceWith("MsoValidUntilSource", "id.walt.mdoc.issuance.MsoValidUntilSource"))
typealias MsoValidUntilSource = id.walt.mdoc.issuance.MsoValidUntilSource

@Deprecated("Use id.walt.mdoc.issuance.ResolvedMsoValidity", ReplaceWith("ResolvedMsoValidity", "id.walt.mdoc.issuance.ResolvedMsoValidity"))
typealias ResolvedMsoValidity = id.walt.mdoc.issuance.ResolvedMsoValidity

@Deprecated("Use id.walt.credentials.issuance.MsoDataMapping", ReplaceWith("MsoDataMapping", "id.walt.credentials.issuance.MsoDataMapping"))
object MsoValidityResolver {
    suspend fun resolve(
        msoData: MdocMsoData?,
        signed: Instant = Clock.System.now(),
        fallbackValidUntil: Instant? = null,
        clock: Clock = Clock.System,
        requireExpectedUpdateWithinWindow: Boolean = false,
    ): MdocResolvedMsoValidity = MsoDataMapping.resolve(
        msoData, signed, fallbackValidUntil, clock, requireExpectedUpdateWithinWindow,
    )
}
