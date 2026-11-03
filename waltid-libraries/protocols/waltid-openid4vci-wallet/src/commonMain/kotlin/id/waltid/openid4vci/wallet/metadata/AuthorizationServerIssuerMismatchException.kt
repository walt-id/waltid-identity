package id.waltid.openid4vci.wallet.metadata

/** The parsed authorization server metadata identifies a different issuer than requested. */
class AuthorizationServerIssuerMismatchException internal constructor() :
    IllegalArgumentException("Authorization server metadata issuer does not match the selected server")
