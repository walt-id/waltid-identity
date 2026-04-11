package id.walt.verifier.openid.transactiondata

const val DEFAULT_HASH_ALGORITHM = "sha-256"
const val MDOC_DEVICE_SIGNED_NAMESPACE = "org.waltid.openid4vp.transaction_data"
const val DEMO_TRANSACTION_DATA_TYPE = "org.waltid.transaction-data.payment-authorization"

val SUPPORTED_TRANSACTION_DATA_TYPES = setOf(DEMO_TRANSACTION_DATA_TYPE)