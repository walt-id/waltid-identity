package id.walt.ebsi.did

import id.walt.ebsi.EbsiEnvironment
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.plus

data class DidRegistrationOptions(
  val clientUri: String,
  val taoIssuerUri: String,
  val clientJwksUri: String = "$clientUri/jwks",
  val clientRedirectUri: String = "$clientUri/code-cb",
  val clientId: String = clientUri,
  val ebsiEnvironment: EbsiEnvironment = EbsiEnvironment.conformance,
  val didRegistryVersion: Int = 5,
  val notBefore: Instant = Clock.System.now(),
  val notAfter: Instant = notBefore.plus(365*24, DateTimeUnit.HOUR)
)
