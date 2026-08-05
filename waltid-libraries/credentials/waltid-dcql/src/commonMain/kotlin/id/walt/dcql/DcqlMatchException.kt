package id.walt.dcql

// Custom exception for match failures
open class DcqlMatchException(message: String) : Exception(message)

/** A well-formed request asks for a credential that is not available locally. */
class RequiredCredentialUnavailableException(
    val queryIds: List<String>,
    message: String,
) : DcqlMatchException(message)

/** The request contains a constraint that this matcher cannot evaluate safely. */
class UnsupportedDcqlConstraintException(message: String) : DcqlMatchException(message)
