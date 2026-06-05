package id.walt.openid4vp.conformance.testplans.plans

import id.walt.openid4vp.conformance.testplans.runner.req.IssuerTestPlanConfiguration

/**
 * Interface for OpenID4VCI Issuer test plans.
 * 
 * These test plans configure the conformance suite to act as a wallet
 * and test an Issuer implementation.
 */
interface IssuerTestPlan {
    val config: IssuerTestPlanConfiguration
}
