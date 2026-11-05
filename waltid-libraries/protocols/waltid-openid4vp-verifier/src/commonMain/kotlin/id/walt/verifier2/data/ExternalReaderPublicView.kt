package id.walt.verifier2.data

import id.walt.policies2.vc.VCPolicyList
import id.walt.policies2.vc.policies.WebhookPolicy
import id.walt.ktornotifications.core.KtorSessionNotifications

fun Verification2Session.publicView(): Verification2Session = copy(
    setup = setup.publicView(), notifications = notifications?.publicView(), ephemeralDecryptionKey = null, policies = policies.publicView(),
)

internal fun VerificationSessionSetup.publicView(): VerificationSessionSetup = when (this) {
    is ExternalReaderFlowSetup -> copy(core = core.publicView())
    is CrossDeviceFlowSetup -> copy(core = core.publicView())
    is SameDeviceFlowSetup -> copy(core = core.publicView())
    is DcApiAnnexDFlowSetup -> copy(core = core.publicView())
    is DcApiAnnexCFlowSetup -> DcApiAnnexCFlowSetup(coreInput = core.publicView(), requestedElements = requestedElements, origin = origin)
}

private fun GeneralFlowConfig.publicView(): GeneralFlowConfig = copy(
    notifications = notifications?.publicView(),
    key = null,
    policies = policies.publicView(),
)

internal fun KtorSessionNotifications.publicView(): KtorSessionNotifications = copy(
    webhook = webhook?.copy(
        basicAuthUser = null,
        basicAuthPass = null,
        bearerToken = null,
    )
)



internal fun Verification2Session.DefinedVerificationPolicies.publicView() = copy(
    vc_policies = vc_policies?.publicView(),
    specific_vc_policies = specific_vc_policies?.mapValues { (_, policies) -> policies.publicView() },
)

private fun VCPolicyList.publicView() = copy(
    policies = policies.map { policy ->
        if (policy is WebhookPolicy) {
            policy.copy(
                basicAuthUsername = null,
                basicAuthPassword = null,
                bearerAuthToken = null,
            )
        } else policy
    }
)
