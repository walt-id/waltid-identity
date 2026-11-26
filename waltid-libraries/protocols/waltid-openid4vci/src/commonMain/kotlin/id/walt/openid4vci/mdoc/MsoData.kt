package id.walt.openid4vci.mdoc

import kotlinx.serialization.KSerializer

@Deprecated("Use id.walt.mdoc.issuance.MsoData", ReplaceWith("MsoData", "id.walt.mdoc.issuance.MsoData"))
typealias MsoData = id.walt.mdoc.issuance.MsoData

@Deprecated("Use id.walt.mdoc.issuance.MsoDataSerializer", ReplaceWith("MsoDataSerializer", "id.walt.mdoc.issuance.MsoDataSerializer"))
object MsoDataSerializer : KSerializer<id.walt.mdoc.issuance.MsoData> by id.walt.mdoc.issuance.MsoDataSerializer
