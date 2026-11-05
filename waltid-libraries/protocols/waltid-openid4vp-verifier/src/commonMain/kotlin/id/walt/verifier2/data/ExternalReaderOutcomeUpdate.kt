package id.walt.verifier2.data

/** External readers learn the session id after its only update. Replay the stored outcome to late SSE subscribers. */
fun Verification2Session.externalReaderOutcomeUpdate(): Verifier2SessionUpdate? =
    takeIf {
        setup is ExternalReaderFlowSetup && status in setOf(
            Verification2Session.VerificationSessionStatus.SUCCESSFUL,
            Verification2Session.VerificationSessionStatus.FAILED,
        )
    }?.let {
        Verifier2SessionUpdate(
            id,
            if (policyResults == null) SessionEvent.presentation_validation_failed else SessionEvent.credential_policy_results_available,
            publicView(),
        )
    }
