package id.walt.issuer.config

import id.walt.commons.config.WaltConfig

data class AuthenticationServiceConfig(
    val name: String = "AWS-Documint-Cognito-UserPool",
    val authorizeUrl: String = "https://documint.auth.ap-south-1.amazoncognito.com/oauth2/authorize",
    val accessTokenUrl: String = "https://documint.auth.ap-south-1.amazoncognito.com/oauth2/token",
    val clientId: String = "30375ch7m71i1mbarifp1fc27f",
    val clientSecret: String = "r5qudm9lk44tj9ugbnd12e5bmo369vqp25fr7evjrathon5aj9b",
)  : WaltConfig()