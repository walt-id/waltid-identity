package id.walt.mdoc

import cbor.Cbor
import id.walt.mdoc.dataelement.*
import id.walt.mdoc.doc.MDocBuilder
import id.walt.mdoc.doc.MDocVerificationParams
import id.walt.mdoc.doc.VerificationType
import id.walt.mdoc.docrequest.MDocRequestBuilder
import id.walt.mdoc.mdocauth.DeviceAuthentication
import id.walt.mdoc.mso.DeviceKeyInfo
import id.walt.mdoc.mso.ValidityInfo
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import korlibs.crypto.encoding.Hex
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToHexString
import kotlin.test.Test

class JSTest {

  @OptIn(DelicateCoroutinesApi::class, ExperimentalJsExport::class)
  //@Test
  fun test1() = GlobalScope.promise {
    val key: dynamic = object {}
    key["d"] = Hex.decode("6c1382765aec5358f117733d281c1c7bdc39884d04a45a1e6c67c858bc206c19")
    val cryptoProvider = SimpleAsyncCOSECryptoProvider(listOf(
      COSECryptoProviderKeyInfo("ISSUER_KEY_ID", "ES256", key)
    ))
    testSigningMdl(cryptoProvider)
  }

  @OptIn(ExperimentalSerializationApi::class)
  suspend fun testSigningMdl(cryptoProvider: SimpleAsyncCOSECryptoProvider) {
    // ISO-IEC_18013-5:2021
    // Personal identification — ISO-compliant driving licence
    // Part 5: Mobile driving licence (mDL) application
    println("test signing mdl")
    // create device key info structure of device public key, for holder binding
    val deviceKeyInfo = DeviceKeyInfo(MapElement(mapOf(MapKey("k") to StringElement("1234"))))
    println("device key info: ${deviceKeyInfo}")
    // build mdoc of type mDL and sign using issuer key with holder binding to device key
    val mdoc = MDocBuilder("org.iso.18013.5.1.mDL")
      .addItemToSign("org.iso.18013.5.1", "family_name", "Doe".toDE())
      .addItemToSign("org.iso.18013.5.1", "given_name", "John".toDE())
      .addItemToSign("org.iso.18013.5.1", "birth_date", FullDateElement(LocalDate(1990, 1, 15)))
      .signAsync(
        ValidityInfo(Clock.System.now(), Clock.System.now(), Clock.System.now().plus(365*24, DateTimeUnit.HOUR)),
        deviceKeyInfo, cryptoProvider, "ISSUER_KEY_ID"
      )
    println("SIGNED MDOC (mDL):")
    println(Cbor.encodeToHexString(mdoc))
  }
}