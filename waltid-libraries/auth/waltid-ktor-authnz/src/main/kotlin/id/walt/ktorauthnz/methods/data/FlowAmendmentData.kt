package id.walt.ktorauthnz.methods.data

import id.walt.ktorauthnz.flows.AuthFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("flowamendment-data")
data class FlowAmendmentData(
    val appendFlow: Set<AuthFlow>? = null,
    var replaceFlow: Set<AuthFlow>? = null,
) : AuthMethodStoredData
