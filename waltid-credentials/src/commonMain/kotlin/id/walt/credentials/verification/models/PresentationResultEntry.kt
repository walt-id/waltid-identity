package id.walt.credentials.verification.models

data class PresentationResultEntry(val credential: String, val policyResults: ArrayList<PolicyResult> = ArrayList())
