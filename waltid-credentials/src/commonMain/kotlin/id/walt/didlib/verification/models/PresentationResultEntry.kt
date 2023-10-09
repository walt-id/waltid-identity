package id.walt.didlib.verification.models

data class PresentationResultEntry(val credential: String, val policyResults: ArrayList<PolicyResult> = ArrayList())
