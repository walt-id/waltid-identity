@file:OptIn(ExperimentalTime::class, ExperimentalSerializationApi::class)

package id.walt.mdoc

import cbor.Cbor
import com.upokecenter.cbor.CBORObject
import id.walt.mdoc.cose.COSESign1Serializer
import id.walt.mdoc.dataelement.*
import id.walt.mdoc.dataretrieval.DeviceRequest
import id.walt.mdoc.dataretrieval.DeviceResponse
import id.walt.mdoc.doc.*
import id.walt.mdoc.docrequest.MDocRequestBuilder
import id.walt.mdoc.docrequest.MDocRequestVerificationParams
import id.walt.mdoc.mdocauth.DeviceAuthentication
import id.walt.mdoc.mso.DeviceKeyInfo
import id.walt.mdoc.mso.ValidityInfo
import id.walt.mdoc.readerauth.ReaderAuthentication
import korlibs.crypto.encoding.Hex
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToHexString
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.cose.java.AlgorithmID
import org.cose.java.OneKey
import org.junit.jupiter.api.BeforeAll
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Security
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class JVMMdocTest {

    private val ISSUER_KEY_ID = "ISSUER_KEY"
    private val DEVICE_KEY_ID = "DEVICE_KEY"
    private val READER_KEY_ID = "READER_KEY"

    @Test
    fun testSigningMdlWithIssuer() {
        // instantiate simple cose crypto provider for issuer keys and certificates
        val cryptoProvider = SimpleCOSECryptoProvider(
            listOf(
                COSECryptoProviderKeyInfo(
                    ISSUER_KEY_ID,
                    AlgorithmID.ECDSA_256,
                    issuerKeyPair.public,
                    issuerKeyPair.private,
                    listOf(issuerCertificate),
                    listOf(rootCaCertificate)
                ), COSECryptoProviderKeyInfo(
                    DEVICE_KEY_ID, AlgorithmID.ECDSA_256, deviceKeyPair.public, deviceKeyPair.private
                )
            )
        )
        testSigningMdl(cryptoProvider)
    }

    @Test
    fun testSigningMdlWithIntermediateIssuer() {
        // instantiate simple cose crypto provider for issuer keys and certificates
        val cryptoProvider = SimpleCOSECryptoProvider(
            listOf(
                COSECryptoProviderKeyInfo(
                    ISSUER_KEY_ID,
                    AlgorithmID.ECDSA_256,
                    intermIssuerKeyPair.public,
                    intermIssuerKeyPair.private,
                    listOf(intermIssuerCertificate, intermCaCertificate),
                    listOf(rootCaCertificate)
                ), COSECryptoProviderKeyInfo(
                    DEVICE_KEY_ID, AlgorithmID.ECDSA_256, deviceKeyPair.public, deviceKeyPair.private
                )
            )
        )
        testSigningMdl(cryptoProvider)
    }

    @Test
    fun testSigningMdlWithOnlyTheIntermediateIssuerCertInX5Chain() {
        // instantiate simple cose crypto provider for issuer keys and certificates
        val cryptoProvider = SimpleCOSECryptoProvider(
            listOf(
                COSECryptoProviderKeyInfo(
                    ISSUER_KEY_ID,
                    AlgorithmID.ECDSA_256,
                    intermIssuerKeyPair.public,
                    intermIssuerKeyPair.private,
                    listOf(intermIssuerCertificate),
                    listOf(rootCaCertificate, intermCaCertificate)
                ), COSECryptoProviderKeyInfo(
                    DEVICE_KEY_ID, AlgorithmID.ECDSA_256, deviceKeyPair.public, deviceKeyPair.private
                )
            )
        )
        testSigningMdl(cryptoProvider)
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun testSigningMdl(cryptoProvider: SimpleCOSECryptoProvider) {
        // ISO-IEC_18013-5:2021
        // Personal identification — ISO-compliant driving licence
        // Part 5: Mobile driving licence (mDL) application

        // create device key info structure of device public key, for holder binding
        val deviceKeyInfo =
            DeviceKeyInfo(DataElement.fromCBOR(OneKey(deviceKeyPair.public, null).AsCBOR().EncodeToBytes()))

        // build mdoc of type mDL and sign using issuer key with holder binding to device key
        val mdoc = MDocBuilder(MDocTypes.ISO_MDL).addItemToSign("org.iso.18013.5.1", "family_name", "Doe".toDataElement())
            .addItemToSign("org.iso.18013.5.1", "given_name", "John".toDataElement())
            .addItemToSign("org.iso.18013.5.1", "birth_date", FullDateElement(LocalDate(1990, 1, 15))).sign(
                ValidityInfo(
                    Clock.System.now(), Clock.System.now(), Clock.System.now().plus(365 * 24, DateTimeUnit.HOUR)
                ), deviceKeyInfo, cryptoProvider, ISSUER_KEY_ID
            )
        println("SIGNED MDOC (mDL):")
        println(Cbor.encodeToHexString(mdoc))

        assertNotEquals(illegal = null, actual = mdoc.MSO)
        assertEquals(expected = "SHA-256", actual = mdoc.MSO!!.digestAlgorithm.value)
        val signedItems = mdoc.getIssuerSignedItems("org.iso.18013.5.1")
        assertEquals(expected = 3, actual = signedItems.size)
        assertEquals(expected = 0L, actual = signedItems.first().digestID.value)
        assertContains(map = mdoc.MSO!!.valueDigests.value, key = MapKey("org.iso.18013.5.1"))
        assertContentEquals(
            expected = deviceKeyPair.public.encoded,
            actual = OneKey(CBORObject.DecodeFromBytes(mdoc.MSO!!.deviceKeyInfo.deviceKey.toCBOR())).AsPublicKey().encoded
        )
        assertEquals(
            expected = true,
            actual = mdoc.verify(MDocVerificationParams(VerificationType.forIssuance, ISSUER_KEY_ID), cryptoProvider)
        )

        val mdocTampered =
            MDocBuilder(MDocTypes.ISO_MDL).addItemToSign("org.iso.18013.5.1", "family_name", "Foe".toDataElement())
                .build(mdoc.issuerSigned.issuerAuth)
        // MSO is valid, signature check should succeed
        assertEquals(
            expected = true, actual = cryptoProvider.verify1(mdocTampered.issuerSigned.issuerAuth!!, ISSUER_KEY_ID)
        )
        // signed item was tampered, overall verification should fail
        assertEquals(
            expected = false, actual = mdocTampered.verify(
                MDocVerificationParams(VerificationType.forIssuance, ISSUER_KEY_ID), cryptoProvider
            )
        )

        // test presentation with device signature
        val ephemeralReaderKey = OneKey.generateKey(AlgorithmID.ECDSA_256)
        val deviceAuthentication = DeviceAuthentication(
            sessionTranscript = ListElement(
                listOf(
                    NullElement(), EncodedCBORElement(ephemeralReaderKey.AsCBOR().EncodeToBytes()), NullElement()
                )
            ), mdoc.docType.value, EncodedCBORElement(MapElement(mapOf()))
        )

        val mdocRequest =
            MDocRequestBuilder(mdoc.docType.value).addDataElementRequest("org.iso.18013.5.1", "family_name", true)
                .build()

        val presentedDoc =
            mdoc.presentWithDeviceSignature(mdocRequest, deviceAuthentication, cryptoProvider, DEVICE_KEY_ID)

        assertEquals(
            expected = true, actual = presentedDoc.verify(
                MDocVerificationParams(
                    VerificationType.forPresentation,
                    ISSUER_KEY_ID,
                    DEVICE_KEY_ID,
                    deviceAuthentication = deviceAuthentication,
                    mDocRequest = mdocRequest
                ), cryptoProvider
            )
        )
    }

    @Test
    fun testVerifyMdocExampleByCertificateInCOSEHeader() {
        // example from ISO/IEC FDIS 18013-5: D.4.1.2 mdoc response
        val mdocExample =
            "a36776657273696f6e63312e3069646f63756d656e747381a367646f6354797065756f72672e69736f2e31383031332e352e312e6d444c6c6973737565725369676e6564a26a6e616d65537061636573a1716f72672e69736f2e31383031332e352e3186d8185863a4686469676573744944006672616e646f6d58208798645b20ea200e19ffabac92624bee6aec63aceedecfb1b80077d22bfc20e971656c656d656e744964656e7469666965726b66616d696c795f6e616d656c656c656d656e7456616c756563446f65d818586ca4686469676573744944036672616e646f6d5820b23f627e8999c706df0c0a4ed98ad74af988af619b4bb078b89058553f44615d71656c656d656e744964656e7469666965726a69737375655f646174656c656c656d656e7456616c7565d903ec6a323031392d31302d3230d818586da4686469676573744944046672616e646f6d5820c7ffa307e5de921e67ba5878094787e8807ac8e7b5b3932d2ce80f00f3e9abaf71656c656d656e744964656e7469666965726b6578706972795f646174656c656c656d656e7456616c7565d903ec6a323032342d31302d3230d818586da4686469676573744944076672616e646f6d582026052a42e5880557a806c1459af3fb7eb505d3781566329d0b604b845b5f9e6871656c656d656e744964656e7469666965726f646f63756d656e745f6e756d6265726c656c656d656e7456616c756569313233343536373839d818590471a4686469676573744944086672616e646f6d5820d094dad764a2eb9deb5210e9d899643efbd1d069cc311d3295516ca0b024412d71656c656d656e744964656e74696669657268706f7274726169746c656c656d656e7456616c7565590412ffd8ffe000104a46494600010101009000900000ffdb004300130d0e110e0c13110f11151413171d301f1d1a1a1d3a2a2c2330453d4947443d43414c566d5d4c51685241435f82606871757b7c7b4a5c869085778f6d787b76ffdb0043011415151d191d381f1f38764f434f7676767676767676767676767676767676767676767676767676767676767676767676767676767676767676767676767676ffc00011080018006403012200021101031101ffc4001b00000301000301000000000000000000000005060401020307ffc400321000010303030205020309000000000000010203040005110612211331141551617122410781a1163542527391b2c1f1ffc4001501010100000000000000000000000000000001ffc4001a110101010003010000000000000000000000014111213161ffda000c03010002110311003f00a5bbde22da2329c7d692bc7d0d03f52cfb0ff75e7a7ef3e7709723a1d0dae146ddfbb3c039ce07ad2bd47a7e32dbb8dd1d52d6ef4b284f64a480067dfb51f87ffb95ff00eb9ff14d215de66af089ce44b7dbde9cb6890a2838eddf18078f7add62d411ef4db9b10a65d6b95a147381ea0d495b933275fe6bba75c114104a8ba410413e983dff004f5af5d34b4b4cde632d0bf1fd1592bdd91c6411f3934c2fa6af6b54975d106dcf4a65ae56e856001ebc03c7ce29dd9eef1ef10fc447dc9da76ad2aee93537a1ba7e4f70dd8eff0057c6dffb5e1a19854a83758e54528750946ec6704850cd037bceb08b6d7d2cc76d3317fc7b5cc04fb6707269c5c6e0c5b60ae549242123b0e493f602a075559e359970d98db89525456b51c951c8afa13ea8e98e3c596836783d5c63f5a61a99fdb7290875db4be88ab384bbbbbfc7183fdeaa633e8951db7da396dc48524fb1a8bd611a5aa2a2432f30ab420a7a6d3240c718cf031fa9ef4c9ad550205aa02951df4a1d6c8421b015b769db8c9229837ea2be8b1b0d39d0eba9c51484efdb8c0efd8d258daf3c449699f2edbd4584e7af9c64e3f96b9beb28d4ac40931e6478c8e76a24a825449501d867d2b1dcdebae99b9c752ae4ecd6dde4a179c1c1e460938f9149ef655e515c03919a289cb3dca278fb7bf177f4faa829dd8ce3f2ac9a7ecde490971fafd7dce15eed9b71c018c64fa514514b24e8e4f8c5c9b75c1e82579dc1233dfec08238f6add62d391acc1c5256a79e706d52d431c7a0145140b9fd149eb3a60dc5e88cbbc2da092411e9dc71f39a7766b447b344e847dcac9dcb5abba8d145061d43a6fcf1e65cf15d0e90231d3dd9cfe62995c6dcc5ca12a2c904a15f71dd27d451453e09d1a21450961cbb3ea8a956433b781f1ce33dfed54f0e2b50a2b71d84ed6db18028a28175f74fc6bda105c529a791c25c4f3c7a11f71586268f4a66b726e33de9ea6f1b52b181c760724e47b514520a5a28a283ffd9d81858ffa4686469676573744944096672616e646f6d58204599f81beaa2b20bd0ffcc9aa03a6f985befab3f6beaffa41e6354cdb2ab2ce471656c656d656e744964656e7469666965727264726976696e675f70726976696c656765736c656c656d656e7456616c756582a37576656869636c655f63617465676f72795f636f646561416a69737375655f64617465d903ec6a323031382d30382d30396b6578706972795f64617465d903ec6a323032342d31302d3230a37576656869636c655f63617465676f72795f636f646561426a69737375655f64617465d903ec6a323031372d30322d32336b6578706972795f64617465d903ec6a323032342d31302d32306a697373756572417574688443a10126a118215901f3308201ef30820195a00302010202143c4416eed784f3b413e48f56f075abfa6d87eb84300a06082a8648ce3d04030230233114301206035504030c0b75746f7069612069616361310b3009060355040613025553301e170d3230313030313030303030305a170d3231313030313030303030305a30213112301006035504030c0975746f706961206473310b30090603550406130255533059301306072a8648ce3d020106082a8648ce3d03010703420004ace7ab7340e5d9648c5a72a9a6f56745c7aad436a03a43efea77b5fa7b88f0197d57d8983e1b37d3a539f4d588365e38cbbf5b94d68c547b5bc8731dcd2f146ba381a83081a5301e0603551d120417301581136578616d706c65406578616d706c652e636f6d301c0603551d1f041530133011a00fa00d820b6578616d706c652e636f6d301d0603551d0e0416041414e29017a6c35621ffc7a686b7b72db06cd12351301f0603551d2304183016801454fa2383a04c28e0d930792261c80c4881d2c00b300e0603551d0f0101ff04040302078030150603551d250101ff040b3009060728818c5d050102300a06082a8648ce3d040302034800304502210097717ab9016740c8d7bcdaa494a62c053bbdecce1383c1aca72ad08dbc04cbb202203bad859c13a63c6d1ad67d814d43e2425caf90d422422c04a8ee0304c0d3a68d5903a2d81859039da66776657273696f6e63312e306f646967657374416c676f726974686d675348412d3235366c76616c756544696765737473a2716f72672e69736f2e31383031332e352e31ad00582075167333b47b6c2bfb86eccc1f438cf57af055371ac55e1e359e20f254adcebf01582067e539d6139ebd131aef441b445645dd831b2b375b390ca5ef6279b205ed45710258203394372ddb78053f36d5d869780e61eda313d44a392092ad8e0527a2fbfe55ae0358202e35ad3c4e514bb67b1a9db51ce74e4cb9b7146e41ac52dac9ce86b8613db555045820ea5c3304bb7c4a8dcb51c4c13b65264f845541341342093cca786e058fac2d59055820fae487f68b7a0e87a749774e56e9e1dc3a8ec7b77e490d21f0e1d3475661aa1d0658207d83e507ae77db815de4d803b88555d0511d894c897439f5774056416a1c7533075820f0549a145f1cf75cbeeffa881d4857dd438d627cf32174b1731c4c38e12ca936085820b68c8afcb2aaf7c581411d2877def155be2eb121a42bc9ba5b7312377e068f660958200b3587d1dd0c2a07a35bfb120d99a0abfb5df56865bb7fa15cc8b56a66df6e0c0a5820c98a170cf36e11abb724e98a75a5343dfa2b6ed3df2ecfbb8ef2ee55dd41c8810b5820b57dd036782f7b14c6a30faaaae6ccd5054ce88bdfa51a016ba75eda1edea9480c5820651f8736b18480fe252a03224ea087b5d10ca5485146c67c74ac4ec3112d4c3a746f72672e69736f2e31383031332e352e312e5553a4005820d80b83d25173c484c5640610ff1a31c949c1d934bf4cf7f18d5223b15dd4f21c0158204d80e1e2e4fb246d97895427ce7000bb59bb24c8cd003ecf94bf35bbd2917e340258208b331f3b685bca372e85351a25c9484ab7afcdf0d2233105511f778d98c2f544035820c343af1bd1690715439161aba73702c474abf992b20c9fb55c36a336ebe01a876d6465766963654b6579496e666fa1696465766963654b6579a40102200121582096313d6c63e24e3372742bfdb1a33ba2c897dcd68ab8c753e4fbd48dca6b7f9a2258201fb3269edd418857de1b39a4e4a44b92fa484caa722c228288f01d0c03a2c3d667646f6354797065756f72672e69736f2e31383031332e352e312e6d444c6c76616c6964697479496e666fa3667369676e6564c074323032302d31302d30315431333a33303a30325a6976616c696446726f6dc074323032302d31302d30315431333a33303a30325a6a76616c6964556e74696cc074323032312d31302d30315431333a33303a30325a584059e64205df1e2f708dd6db0847aed79fc7c0201d80fa55badcaf2e1bcf5902e1e5a62e4832044b890ad85aa53f129134775d733754d7cb7a413766aeff13cb2e6c6465766963655369676e6564a26a6e616d65537061636573d81841a06a64657669636541757468a1696465766963654d61638443a10105a0f65820e99521a85ad7891b806a07f8b5388a332d92c189a7bf293ee1f543405ae6824d6673746174757300"

        val mdoc = Cbor.decodeFromHexString<DeviceResponse>(mdocExample)
        // Get the public key certificate from the COSE_Sign1 unprotected header
        val certificateDER = mdoc.documents[0].issuerSigned.issuerAuth!!.x5Chain!!.first()
        val cert = CertificateFactory.getInstance("X509")
            .generateCertificate(ByteArrayInputStream(certificateDER)) as X509Certificate

        val cryptoProvider = SimpleCOSECryptoProvider(
            listOf(
                COSECryptoProviderKeyInfo(ISSUER_KEY_ID, AlgorithmID.ECDSA_256, cert.publicKey, null, listOf(cert))
            )
        )
        assertEquals(expected = true, actual = mdoc.documents[0].verifySignature(cryptoProvider, ISSUER_KEY_ID))
        // CA certificate of example not trusted
        //assertEquals(expected = true, actual = mdoc.documents[0].verifyCertificate(cryptoProvider))
    }

    @Test
    fun exampleCreateParseVerifyMDLRequest() {
        val cryptoProvider = SimpleCOSECryptoProvider(
            listOf(
                COSECryptoProviderKeyInfo(
                    READER_KEY_ID, AlgorithmID.ECDSA_256, readerKeyPair.public, readerKeyPair.private
                )
            )
        )
        val sessionTranscript =
            ListElement(/*... create session transcript according to ISO/IEC FDIS 18013-5, section 9.1.5.1 ...*/)

        // create and sign mdoc request
        val docReq =
            MDocRequestBuilder(MDocTypes.ISO_MDL).addDataElementRequest("org.iso.18013.5.1", "family_name", true)
                .addDataElementRequest("org.iso.18013.5.1", "birth_date", false)
                .sign(sessionTranscript, cryptoProvider, READER_KEY_ID)

        val deviceRequest = DeviceRequest(listOf(docReq))
        val devReqCbor = deviceRequest.toCBORHex()
        println("DEVICE REQUEST: $devReqCbor")

        // parse and verify mdoc request
        val parsedReq = DeviceRequest.fromCBORHex(devReqCbor)
        val firstParsedDocRequest = parsedReq.docRequests.first()
        val reqVerified = firstParsedDocRequest.verify(
            MDocRequestVerificationParams(
                requiresReaderAuth = true,
                READER_KEY_ID,
                allowedToRetain = mapOf("org.iso.18013.5.1" to setOf("family_name")),
                ReaderAuthentication(sessionTranscript, firstParsedDocRequest.itemsRequest)
            ), cryptoProvider
        )
        println("Request verified: $reqVerified")
        println("Requested doc type: ${firstParsedDocRequest.docType}")
        println("Requested items:")
        firstParsedDocRequest.nameSpaces.forEach { ns ->
            println("- NameSpace: $ns")
            firstParsedDocRequest.getRequestedItemsFor(ns).forEach {
                println("-- ${it.key} (intent-to-retain: ${it.value})")
            }
        }

        // load/parse mdoc and present using selective disclosure

    }

    @Test
    fun testReaderAuthVerification() {
        // try deserializing example from ISO/IEC FDIS 18013-5: D.4.1.1 mdoc request
        val exampleRequest =
            "a26776657273696f6e63312e306b646f63526571756573747381a26c6974656d7352657175657374d8185893a267646f6354797065756f72672e69736f2e31383031332e352e312e6d444c6a6e616d65537061636573a1716f72672e69736f2e31383031332e352e31a66b66616d696c795f6e616d65f56f646f63756d656e745f6e756d626572f57264726976696e675f70726976696c65676573f56a69737375655f64617465f56b6578706972795f64617465f568706f727472616974f46a726561646572417574688443a10126a118215901b7308201b330820158a00302010202147552715f6add323d4934a1ba175dc945755d8b50300a06082a8648ce3d04030230163114301206035504030c0b72656164657220726f6f74301e170d3230313030313030303030305a170d3233313233313030303030305a3011310f300d06035504030c067265616465723059301306072a8648ce3d020106082a8648ce3d03010703420004f8912ee0f912b6be683ba2fa0121b2630e601b2b628dff3b44f6394eaa9abdbcc2149d29d6ff1a3e091135177e5c3d9c57f3bf839761eed02c64dd82ae1d3bbfa38188308185301c0603551d1f041530133011a00fa00d820b6578616d706c652e636f6d301d0603551d0e04160414f2dfc4acafc5f30b464fada20bfcd533af5e07f5301f0603551d23041830168014cfb7a881baea5f32b6fb91cc29590c50dfac416e300e0603551d0f0101ff04040302078030150603551d250101ff040b3009060728818c5d050106300a06082a8648ce3d0403020349003046022100fb9ea3b686fd7ea2f0234858ff8328b4efef6a1ef71ec4aae4e307206f9214930221009b94f0d739dfa84cca29efed529dd4838acfd8b6bee212dc6320c46feb839a35f658401f3400069063c189138bdcd2f631427c589424113fc9ec26cebcacacfcdb9695d28e99953becabc4e30ab4efacc839a81f9159933d192527ee91b449bb7f80bf"
        val devRequest = DeviceRequest.fromCBORHex(exampleRequest)
        val readerAuthenticationBytes = Hex.decode(
            "d8185902ee837452656164657241757468656e7469636174696f6e83d8185858a20063312e30018201d818584ba4010220012158205a88d182bce5f42efa59943f33359d2e8a968ff289d93e5fa444b624343167fe225820b16e8cf858ddc7690407ba61d4c338237a8cfcf3de6aa672fc60a557aa32fc67d818584ba40102200121582060e3392385041f51403051f2415531cb56dd3f999c71687013aac6768bc8187e225820e58deb8fdbe907f7dd5368245551a34796f7d2215c440c339bb0f7b67beccdfa8258c391020f487315d10209616301013001046d646f631a200c016170706c69636174696f6e2f766e642e626c7565746f6f74682e6c652e6f6f6230081b28128b37282801021c015c1e580469736f2e6f72673a31383031333a646576696365656e676167656d656e746d646f63a20063312e30018201d818584ba4010220012158205a88d182bce5f42efa59943f33359d2e8a968ff289d93e5fa444b624343167fe225820b16e8cf858ddc7690407ba61d4c338237a8cfcf3de6aa672fc60a557aa32fc6758cd91022548721591020263720102110204616301013000110206616301036e6663005102046163010157001a201e016170706c69636174696f6e2f766e642e626c7565746f6f74682e6c652e6f6f6230081b28078080bf2801021c021107c832fff6d26fa0beb34dfcd555d4823a1c11010369736f2e6f72673a31383031333a6e66636e6663015a172b016170706c69636174696f6e2f766e642e7766612e6e616e57030101032302001324fec9a70b97ac9684a4e326176ef5b981c5e8533e5f00298cfccbc35e700a6b020414d8185893a267646f6354797065756f72672e69736f2e31383031332e352e312e6d444c6a6e616d65537061636573a1716f72672e69736f2e31383031332e352e31a66b66616d696c795f6e616d65f56f646f63756d656e745f6e756d626572f57264726976696e675f70726976696c65676573f56a69737375655f64617465f56b6578706972795f64617465f568706f727472616974f4"
        )

        val readerAuthentication =
            EncodedCBORElement.fromEncodedCBORElementData(readerAuthenticationBytes).decode<ReaderAuthentication>()
        assertEquals(
            expected = "ReaderAuthentication", actual = (readerAuthentication.data[0] as? StringElement)?.value
        )

        val certificateDER = devRequest.docRequests[0].readerAuth!!.x5Chain!!.first()
        val cert = CertificateFactory.getInstance("X509")
            .generateCertificate(ByteArrayInputStream(certificateDER)) as X509Certificate
        val cryptoProvider = SimpleCOSECryptoProvider(
            listOf(
                COSECryptoProviderKeyInfo(READER_KEY_ID, AlgorithmID.ECDSA_256, cert.publicKey, null, listOf(cert))
            )
        )

        // test with all fields allowed to retain
        assertEquals(
            expected = true, actual = devRequest.docRequests[0].verify(
                MDocRequestVerificationParams(
                    true, READER_KEY_ID, allowedToRetain = mapOf(
                        "org.iso.18013.5.1" to setOf(
                            "family_name", "document_number", "driving_privileges", "issue_date", "expiry_date"
                        )
                    ), readerAuthentication
                ), cryptoProvider
            )
        )

        // test with restricted fields allowed to retain
        assertEquals(
            expected = false, actual = devRequest.docRequests[0].verify(
                MDocRequestVerificationParams(
                    true, READER_KEY_ID, allowedToRetain = mapOf(
                        "org.iso.18013.5.1" to setOf("family_name")
                    ), readerAuthentication
                ), cryptoProvider
            )
        )
    }

    @Test
    fun testSelectiveDisclosure() {
        // try deserializing example from ISO/IEC FDIS 18013-5: D.4.1.2 mdoc response
        val serializedDoc =
            "a36776657273696f6e63312e3069646f63756d656e747381a367646f6354797065756f72672e69736f2e31383031332e352e312e6d444c6c6973737565725369676e6564a26a6e616d65537061636573a1716f72672e69736f2e31383031332e352e3186d8185863a4686469676573744944006672616e646f6d58208798645b20ea200e19ffabac92624bee6aec63aceedecfb1b80077d22bfc20e971656c656d656e744964656e7469666965726b66616d696c795f6e616d656c656c656d656e7456616c756563446f65d818586ca4686469676573744944036672616e646f6d5820b23f627e8999c706df0c0a4ed98ad74af988af619b4bb078b89058553f44615d71656c656d656e744964656e7469666965726a69737375655f646174656c656c656d656e7456616c7565d903ec6a323031392d31302d3230d818586da4686469676573744944046672616e646f6d5820c7ffa307e5de921e67ba5878094787e8807ac8e7b5b3932d2ce80f00f3e9abaf71656c656d656e744964656e7469666965726b6578706972795f646174656c656c656d656e7456616c7565d903ec6a323032342d31302d3230d818586da4686469676573744944076672616e646f6d582026052a42e5880557a806c1459af3fb7eb505d3781566329d0b604b845b5f9e6871656c656d656e744964656e7469666965726f646f63756d656e745f6e756d6265726c656c656d656e7456616c756569313233343536373839d818590471a4686469676573744944086672616e646f6d5820d094dad764a2eb9deb5210e9d899643efbd1d069cc311d3295516ca0b024412d71656c656d656e744964656e74696669657268706f7274726169746c656c656d656e7456616c7565590412ffd8ffe000104a46494600010101009000900000ffdb004300130d0e110e0c13110f11151413171d301f1d1a1a1d3a2a2c2330453d4947443d43414c566d5d4c51685241435f82606871757b7c7b4a5c869085778f6d787b76ffdb0043011415151d191d381f1f38764f434f7676767676767676767676767676767676767676767676767676767676767676767676767676767676767676767676767676ffc00011080018006403012200021101031101ffc4001b00000301000301000000000000000000000005060401020307ffc400321000010303030205020309000000000000010203040005110612211331141551617122410781a1163542527391b2c1f1ffc4001501010100000000000000000000000000000001ffc4001a110101010003010000000000000000000000014111213161ffda000c03010002110311003f00a5bbde22da2329c7d692bc7d0d03f52cfb0ff75e7a7ef3e7709723a1d0dae146ddfbb3c039ce07ad2bd47a7e32dbb8dd1d52d6ef4b284f64a480067dfb51f87ffb95ff00eb9ff14d215de66af089ce44b7dbde9cb6890a2838eddf18078f7add62d411ef4db9b10a65d6b95a147381ea0d495b933275fe6bba75c114104a8ba410413e983dff004f5af5d34b4b4cde632d0bf1fd1592bdd91c6411f3934c2fa6af6b54975d106dcf4a65ae56e856001ebc03c7ce29dd9eef1ef10fc447dc9da76ad2aee93537a1ba7e4f70dd8eff0057c6dffb5e1a19854a83758e54528750946ec6704850cd037bceb08b6d7d2cc76d3317fc7b5cc04fb6707269c5c6e0c5b60ae549242123b0e493f602a075559e359970d98db89525456b51c951c8afa13ea8e98e3c596836783d5c63f5a61a99fdb7290875db4be88ab384bbbbbfc7183fdeaa633e8951db7da396dc48524fb1a8bd611a5aa2a2432f30ab420a7a6d3240c718cf031fa9ef4c9ad550205aa02951df4a1d6c8421b015b769db8c9229837ea2be8b1b0d39d0eba9c51484efdb8c0efd8d258daf3c449699f2edbd4584e7af9c64e3f96b9beb28d4ac40931e6478c8e76a24a825449501d867d2b1dcdebae99b9c752ae4ecd6dde4a179c1c1e460938f9149ef655e515c03919a289cb3dca278fb7bf177f4faa829dd8ce3f2ac9a7ecde490971fafd7dce15eed9b71c018c64fa514514b24e8e4f8c5c9b75c1e82579dc1233dfec08238f6add62d391acc1c5256a79e706d52d431c7a0145140b9fd149eb3a60dc5e88cbbc2da092411e9dc71f39a7766b447b344e847dcac9dcb5abba8d145061d43a6fcf1e65cf15d0e90231d3dd9cfe62995c6dcc5ca12a2c904a15f71dd27d451453e09d1a21450961cbb3ea8a956433b781f1ce33dfed54f0e2b50a2b71d84ed6db18028a28175f74fc6bda105c529a791c25c4f3c7a11f71586268f4a66b726e33de9ea6f1b52b181c760724e47b514520a5a28a283ffd9d81858ffa4686469676573744944096672616e646f6d58204599f81beaa2b20bd0ffcc9aa03a6f985befab3f6beaffa41e6354cdb2ab2ce471656c656d656e744964656e7469666965727264726976696e675f70726976696c656765736c656c656d656e7456616c756582a37576656869636c655f63617465676f72795f636f646561416a69737375655f64617465d903ec6a323031382d30382d30396b6578706972795f64617465d903ec6a323032342d31302d3230a37576656869636c655f63617465676f72795f636f646561426a69737375655f64617465d903ec6a323031372d30322d32336b6578706972795f64617465d903ec6a323032342d31302d32306a697373756572417574688443a10126a118215901f3308201ef30820195a00302010202143c4416eed784f3b413e48f56f075abfa6d87eb84300a06082a8648ce3d04030230233114301206035504030c0b75746f7069612069616361310b3009060355040613025553301e170d3230313030313030303030305a170d3231313030313030303030305a30213112301006035504030c0975746f706961206473310b30090603550406130255533059301306072a8648ce3d020106082a8648ce3d03010703420004ace7ab7340e5d9648c5a72a9a6f56745c7aad436a03a43efea77b5fa7b88f0197d57d8983e1b37d3a539f4d588365e38cbbf5b94d68c547b5bc8731dcd2f146ba381a83081a5301e0603551d120417301581136578616d706c65406578616d706c652e636f6d301c0603551d1f041530133011a00fa00d820b6578616d706c652e636f6d301d0603551d0e0416041414e29017a6c35621ffc7a686b7b72db06cd12351301f0603551d2304183016801454fa2383a04c28e0d930792261c80c4881d2c00b300e0603551d0f0101ff04040302078030150603551d250101ff040b3009060728818c5d050102300a06082a8648ce3d040302034800304502210097717ab9016740c8d7bcdaa494a62c053bbdecce1383c1aca72ad08dbc04cbb202203bad859c13a63c6d1ad67d814d43e2425caf90d422422c04a8ee0304c0d3a68d5903a2d81859039da66776657273696f6e63312e306f646967657374416c676f726974686d675348412d3235366c76616c756544696765737473a2716f72672e69736f2e31383031332e352e31ad00582075167333b47b6c2bfb86eccc1f438cf57af055371ac55e1e359e20f254adcebf01582067e539d6139ebd131aef441b445645dd831b2b375b390ca5ef6279b205ed45710258203394372ddb78053f36d5d869780e61eda313d44a392092ad8e0527a2fbfe55ae0358202e35ad3c4e514bb67b1a9db51ce74e4cb9b7146e41ac52dac9ce86b8613db555045820ea5c3304bb7c4a8dcb51c4c13b65264f845541341342093cca786e058fac2d59055820fae487f68b7a0e87a749774e56e9e1dc3a8ec7b77e490d21f0e1d3475661aa1d0658207d83e507ae77db815de4d803b88555d0511d894c897439f5774056416a1c7533075820f0549a145f1cf75cbeeffa881d4857dd438d627cf32174b1731c4c38e12ca936085820b68c8afcb2aaf7c581411d2877def155be2eb121a42bc9ba5b7312377e068f660958200b3587d1dd0c2a07a35bfb120d99a0abfb5df56865bb7fa15cc8b56a66df6e0c0a5820c98a170cf36e11abb724e98a75a5343dfa2b6ed3df2ecfbb8ef2ee55dd41c8810b5820b57dd036782f7b14c6a30faaaae6ccd5054ce88bdfa51a016ba75eda1edea9480c5820651f8736b18480fe252a03224ea087b5d10ca5485146c67c74ac4ec3112d4c3a746f72672e69736f2e31383031332e352e312e5553a4005820d80b83d25173c484c5640610ff1a31c949c1d934bf4cf7f18d5223b15dd4f21c0158204d80e1e2e4fb246d97895427ce7000bb59bb24c8cd003ecf94bf35bbd2917e340258208b331f3b685bca372e85351a25c9484ab7afcdf0d2233105511f778d98c2f544035820c343af1bd1690715439161aba73702c474abf992b20c9fb55c36a336ebe01a876d6465766963654b6579496e666fa1696465766963654b6579a40102200121582096313d6c63e24e3372742bfdb1a33ba2c897dcd68ab8c753e4fbd48dca6b7f9a2258201fb3269edd418857de1b39a4e4a44b92fa484caa722c228288f01d0c03a2c3d667646f6354797065756f72672e69736f2e31383031332e352e312e6d444c6c76616c6964697479496e666fa3667369676e6564c074323032302d31302d30315431333a33303a30325a6976616c696446726f6dc074323032302d31302d30315431333a33303a30325a6a76616c6964556e74696cc074323032312d31302d30315431333a33303a30325a584059e64205df1e2f708dd6db0847aed79fc7c0201d80fa55badcaf2e1bcf5902e1e5a62e4832044b890ad85aa53f129134775d733754d7cb7a413766aeff13cb2e6c6465766963655369676e6564a26a6e616d65537061636573d81841a06a64657669636541757468a1696465766963654d61638443a10105a0f65820e99521a85ad7891b806a07f8b5388a332d92c189a7bf293ee1f543405ae6824d6673746174757300"
        val mdocRespParsed = DeviceResponse.fromCBORHex(serializedDoc)
        val mdoc = mdocRespParsed.documents[0]

        val deviceAuthenticationBytes = Hex.decode(
            "d818590271847444657669636541757468656e7469636174696f6e83d8185858a20063312e30018201d818584ba4010220012158205a88d182bce5f42efa59943f33359d2e8a968ff289d93e5fa444b624343167fe225820b16e8cf858ddc7690407ba61d4c338237a8cfcf3de6aa672fc60a557aa32fc67d818584ba40102200121582060e3392385041f51403051f2415531cb56dd3f999c71687013aac6768bc8187e225820e58deb8fdbe907f7dd5368245551a34796f7d2215c440c339bb0f7b67beccdfa8258c391020f487315d10209616301013001046d646f631a200c016170706c69636174696f6e2f766e642e626c7565746f6f74682e6c652e6f6f6230081b28128b37282801021c015c1e580469736f2e6f72673a31383031333a646576696365656e676167656d656e746d646f63a20063312e30018201d818584ba4010220012158205a88d182bce5f42efa59943f33359d2e8a968ff289d93e5fa444b624343167fe225820b16e8cf858ddc7690407ba61d4c338237a8cfcf3de6aa672fc60a557aa32fc6758cd91022548721591020263720102110204616301013000110206616301036e6663005102046163010157001a201e016170706c69636174696f6e2f766e642e626c7565746f6f74682e6c652e6f6f6230081b28078080bf2801021c021107c832fff6d26fa0beb34dfcd555d4823a1c11010369736f2e6f72673a31383031333a6e66636e6663015a172b016170706c69636174696f6e2f766e642e7766612e6e616e57030101032302001324fec9a70b97ac9684a4e326176ef5b981c5e8533e5f00298cfccbc35e700a6b020414756f72672e69736f2e31383031332e352e312e6d444cd81841a0"
        )
        val deviceAuthentication =
            DataElement.fromCBOR<EncodedCBORElement>(deviceAuthenticationBytes).decode<DeviceAuthentication>()
        val ephemeralMacKey = Hex.decode("dc2b9566fdaaae3c06baa40993cd0451aeba15e7677ef5305f6531f3533c35dd")

        val mdocRequest =
            MDocRequestBuilder(mdoc.docType.value).addDataElementRequest("org.iso.18013.5.1", "family_name", true)
                .addDataElementRequest("org.iso.18013.5.1", "document_number", true).build()

        // present with selective disclosure
        val presentedMdoc = mdoc.presentWithDeviceMAC(mdocRequest, deviceAuthentication, ephemeralMacKey)
        println("Presented MDOC: ${presentedMdoc.toCBORHex()}")
        presentedMdoc.nameSpaces.forEach { ns ->
            println("Namespace: $ns")
            presentedMdoc.getIssuerSignedItems(ns).forEach { issuerSignedItem ->
                println("- ${issuerSignedItem.elementIdentifier.value}: ${issuerSignedItem.elementValue.internalValue.toString()}")
            }
        }
        assertEquals(expected = setOf("org.iso.18013.5.1"), actual = presentedMdoc.nameSpaces)
        assertContentEquals(expected = setOf("family_name", "document_number"),
            actual = presentedMdoc.getIssuerSignedItems("org.iso.18013.5.1").map {
                it.elementIdentifier.value
            })

        // validate issuer signature, tamper check and device mac
        val certificateDER = mdoc.issuerSigned.issuerAuth!!.x5Chain!!.first()
        val cert = CertificateFactory.getInstance("X509")
            .generateCertificate(ByteArrayInputStream(certificateDER)) as X509Certificate
        val cryptoProvider = SimpleCOSECryptoProvider(
            listOf(
                COSECryptoProviderKeyInfo(ISSUER_KEY_ID, AlgorithmID.ECDSA_256, cert.publicKey, null, listOf(cert))
            )
        )

        val mdocVerified = presentedMdoc.verify(
            MDocVerificationParams(
                VerificationType.DOC_TYPE and VerificationType.DEVICE_SIGNATURE and VerificationType.ISSUER_SIGNATURE and VerificationType.ITEMS_TAMPER_CHECK,
                ISSUER_KEY_ID,
                ephemeralMacKey = ephemeralMacKey,
                deviceAuthentication = deviceAuthentication,
                mDocRequest = mdocRequest
            ), cryptoProvider
        )
        println("Verified: $mdocVerified")
        assertEquals(expected = true, actual = mdocVerified)
    }

    @Test
    fun testSigningMobileEIDDocument() {
        // ISO-IEC_23220-2
        // Cards and security devices for personal identification
        // Building blocks for identity management via mobile devices
        // Part 2: Data objects and encoding rules for generic eID-System

        // instantiate simple cose crypto provider for issuer keys and certificates
        val cryptoProvider = SimpleCOSECryptoProvider(
            listOf(
                COSECryptoProviderKeyInfo(
                    ISSUER_KEY_ID,
                    AlgorithmID.ECDSA_256,
                    issuerKeyPair.public,
                    issuerKeyPair.private,
                    listOf(issuerCertificate),
                    listOf(rootCaCertificate)
                ), COSECryptoProviderKeyInfo(
                    DEVICE_KEY_ID, AlgorithmID.ECDSA_256, deviceKeyPair.public, deviceKeyPair.private
                )
            )
        )
        // create device key info structure of device public key, for holder binding
        val deviceKeyInfo =
            DeviceKeyInfo(DataElement.fromCBOR(OneKey(deviceKeyPair.public, null).AsCBOR().EncodeToBytes()))

        // build mdoc of type mID and sign using issuer key with holder binding to device key
        val mdoc = MDocBuilder("org.iso.23220.mID.1").addItemToSign("org.iso.23220.1", "family_name", "Doe".toDataElement())
            .addItemToSign("org.iso.23220.1", "given_name", "John".toDataElement())
            .addItemToSign("org.iso.23220.1", "birth_date", FullDateElement(LocalDate(1990, 1, 15)))
            .addItemToSign("org.iso.23220.1", "sex", "1".toDataElement()) // ISO/IEC 5218
            .addItemToSign("org.iso.23220.1", "height", "175".toDataElement())
            .addItemToSign("org.iso.23220.1", "weight", "70".toDataElement())
            .addItemToSign("org.iso.23220.1", "birthplace", "Vienna".toDataElement())
            .addItemToSign("org.iso.23220.1", "nationality", "AT".toDataElement())
            .addItemToSign("org.iso.23220.1", "telephone_number", "0987654".toDataElement())
            .addItemToSign("org.iso.23220.1", "email_address", "john@email.com".toDataElement()).sign(
                ValidityInfo(
                    Clock.System.now(), Clock.System.now(), Clock.System.now().plus(365 * 24, DateTimeUnit.HOUR)
                ), deviceKeyInfo, cryptoProvider, ISSUER_KEY_ID
            )

        mdoc.nameSpaces.forEach { ns ->
            println("mobile eID ($ns)")
            mdoc.getIssuerSignedItems(ns).forEach { issuerSignedItem ->
                println("- ${issuerSignedItem.elementIdentifier.value}: ${issuerSignedItem.elementValue.internalValue.toString()}")
            }
        }
        println("SIGNED MDOC (mobile eID):")
        println(Cbor.encodeToHexString(mdoc))

        assertNotEquals(illegal = null, actual = mdoc.MSO)
        assertEquals(expected = "SHA-256", actual = mdoc.MSO!!.digestAlgorithm.value)
        val signedItems = mdoc.getIssuerSignedItems("org.iso.23220.1")
        assertEquals(expected = 10, actual = signedItems.size)
        assertEquals(expected = 0L, actual = signedItems.first().digestID.value)
        assertContains(map = mdoc.MSO!!.valueDigests.value, key = MapKey("org.iso.23220.1"))
        assertContentEquals(
            expected = deviceKeyPair.public.encoded,
            actual = OneKey(CBORObject.DecodeFromBytes(mdoc.MSO!!.deviceKeyInfo.deviceKey.toCBOR())).AsPublicKey().encoded
        )
        assertEquals(
            expected = true,
            actual = mdoc.verify(MDocVerificationParams(VerificationType.forIssuance, ISSUER_KEY_ID), cryptoProvider)
        )


        // test presentation with device signature
        val ephemeralReaderKey = OneKey.generateKey(AlgorithmID.ECDSA_256)
        val deviceAuthentication = DeviceAuthentication(
            sessionTranscript = ListElement(
                listOf(
                    NullElement(), EncodedCBORElement(ephemeralReaderKey.AsCBOR().EncodeToBytes()), NullElement()
                )
            ), mdoc.docType.value, EncodedCBORElement(MapElement(mapOf()))
        )


        // we present the mandatory attributes of the eIDAS minimal data set for natural persons (CIR 2015/1501), although the unique ID is missing in ISO-IEC_23220-2
        // mandatory:
        // - current family name(s)
        // - current first name(s)
        // - date of birth;
        // - a unique identifier constructed by the sending Member State in accordance with the technical specifications for the purposes of cross-border identification and which is as persistent as possible in time.
        // optional:
        // - first name(s) and family name(s) at birth
        // - place of birth;
        // - current address;
        // - gender

        val mdocRequest =
            MDocRequestBuilder(mdoc.docType.value).addDataElementRequest("org.iso.23220.1", "family_name", true)
                .addDataElementRequest("org.iso.23220.1", "given_name", true)
                .addDataElementRequest("org.iso.23220.1", "birth_date", true).build()

        val presentedMdoc =
            mdoc.presentWithDeviceSignature(mdocRequest, deviceAuthentication, cryptoProvider, DEVICE_KEY_ID)

        assertEquals(
            expected = true, actual = presentedMdoc.verify(
                MDocVerificationParams(
                    VerificationType.forPresentation,
                    ISSUER_KEY_ID,
                    DEVICE_KEY_ID,
                    deviceAuthentication = deviceAuthentication,
                    mDocRequest = mdocRequest
                ), cryptoProvider
            )
        )

        presentedMdoc.nameSpaces.forEach { ns ->
            println("Presented mobile eID ($ns)")
            presentedMdoc.getIssuerSignedItems(ns).forEach { issuerSignedItem ->
                println("- ${issuerSignedItem.elementIdentifier.value}: ${issuerSignedItem.elementValue.internalValue.toString()}")
            }
        }
    }

    //@Test
    fun testDeserializeJSCoseResult() {
        val jsCoseResult =
            "d28443a10126a1044d4953535545525f4b45595f4944d84859016ed818590169a66776657273696f6e63312e306f646967657374416c676f726974686d675348412d3235366c76616c756544696765737473a1716f72672e69736f2e31383031332e352e31a3005820973d5016a63d3949c90bfeb1c82249ebb2b3aab57c945d7e52d8ffd61ce2f18c0158206c63e9f9237e5bcd7ea8096615c2d1ce07be9250fc1ad70cee6a32ea34d6ec74025820d30704f179d44947eaa445165f19aebfbe46193d1163b20ffccc9ed844bbf5986d6465766963654b6579496e666fa1696465766963654b6579a1616b643132333467646f6354797065756f72672e69736f2e31383031332e352e312e6d444c6c76616c6964697479496e666fa3667369676e6564c07818323032332d31312d33305431353a35323a33392e3934395a6976616c696446726f6dc07818323032332d31312d33305431353a35323a33392e3934395a6a76616c6964556e74696cc07818323032342d31312d32395431353a35323a33392e3934395a5840640ef0805702cc6972bcb24a325fd71073caa3952ffd5e38cd6847169deb02908e94629ffbaecee02ab556d4d4af1ae0cc779c0f46f1721ad830f0badf942ee5"
        val coseSign1 = Cbor.decodeFromByteArray(COSESign1Serializer, Hex.decode(jsCoseResult))
        assertEquals(expected = AlgorithmID.ECDSA_256, actual = AlgorithmID.valueOf(coseSign1.algorithm.toString()))
    }

    /*@Test
    fun testReproduceJSTestResult() {
      val key = Hex.decode("6c1382765aec5358f117733d281c1c7bdc39884d04a45a1e6c67c858bc206c19")
      val curve = SECNamedCurves.getByName("secp256k1") as X9ECParameters
      val domain = ECDomainParameters(curve.curve, curve.g, curve.n, curve.h)
      val d = BigInteger(key)
      val q = domain.g.multiply(d)
      val pubParams = ECPublicKeyParameters(q, domain)
      val pub = pubParams.q.getEncoded(false)
      val priv = ECPrivateKey(d)
      val kf = KeyFactory.getInstance("EC")
      val cryptoProvider = SimpleCOSECryptoProvider(listOf(
        COSECryptoProviderKeyInfo("ISSUER_KEY_ID", AlgorithmID.ECDSA_256,
          pub,
          )
      ))
      val deviceKeyInfo = DeviceKeyInfo(MapElement(mapOf(MapKey("k") to StringElement("1234"))))
      val mdoc = MDocBuilder("org.iso.18013.5.1.mDL")
        .addItemToSign("org.iso.18013.5.1", "family_name", "Doe".toDE())
        .addItemToSign("org.iso.18013.5.1", "given_name", "John".toDE())
        .addItemToSign("org.iso.18013.5.1", "birth_date", FullDateElement(LocalDate(1990, 1, 15)))
        .sign(
          ValidityInfo(Clock.System.now(), Clock.System.now(), Clock.System.now().plus(365*24, DateTimeUnit.HOUR)),
          deviceKeyInfo, cryptoProvider, "ISSUER_KEY_ID"
        )
      println("SIGNED MDOC (mDL):")
      println(Cbor.encodeToHexString(mdoc))
    }*/
    companion object {
        lateinit var rootCaKeyPair: KeyPair
        lateinit var intermCaKeyPair: KeyPair
        lateinit var issuerKeyPair: KeyPair
        lateinit var intermIssuerKeyPair: KeyPair
        lateinit var deviceKeyPair: KeyPair
        lateinit var readerKeyPair: KeyPair
        lateinit var rootCaCertificate: X509Certificate
        lateinit var intermCaCertificate: X509Certificate
        lateinit var issuerCertificate: X509Certificate
        lateinit var intermIssuerCertificate: X509Certificate

        @JvmStatic
        @BeforeAll
        fun initializeIssuerKeys() {
            Security.addProvider(BouncyCastleProvider())
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(256)
            // create key pair for test CA
            rootCaKeyPair = kpg.genKeyPair()
            intermCaKeyPair = kpg.genKeyPair()
            // create key pair for test signer/issuer
            issuerKeyPair = kpg.genKeyPair()
            intermIssuerKeyPair = kpg.genKeyPair()
            // create key pair for mdoc auth (device/holder key)
            deviceKeyPair = kpg.genKeyPair()
            readerKeyPair = kpg.genKeyPair()

            // create CA certificate
            rootCaCertificate = X509v3CertificateBuilder(
                X500Name("CN=MDOC ROOT CSP"),
                BigInteger.valueOf(SecureRandom().nextLong()),
                Date(),
                Date(System.currentTimeMillis() + 24L * 3600 * 1000),
                X500Name("CN=MDOC ROOT CA"),
                SubjectPublicKeyInfo.getInstance(rootCaKeyPair.public.encoded)
            ).addExtension(
                Extension.basicConstraints, true, BasicConstraints(false)
            ) // TODO: Should be CA! Should not pass validation when false!
                .addExtension(
                    Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign)
                ) // Key usage not validated.
                .build(JcaContentSignerBuilder("SHA256withECDSA").setProvider("BC").build(rootCaKeyPair.private)).let {
                    JcaX509CertificateConverter().setProvider("BC").getCertificate(it)
                }

            intermCaCertificate = X509v3CertificateBuilder(
                X500Name("CN=MDOC ROOT CA"),
                BigInteger.valueOf(SecureRandom().nextLong()),
                Date(),
                Date(System.currentTimeMillis() + 24L * 3600 * 1000),
                X500Name("CN=MDOC Iterm CA"),
                SubjectPublicKeyInfo.getInstance(intermCaKeyPair.public.encoded)
            ).addExtension(
                Extension.basicConstraints, true, BasicConstraints(true)
            ) // When set to false will not pass validation as expected!
                .addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign))

                .build(JcaContentSignerBuilder("SHA256withECDSA").setProvider("BC").build(rootCaKeyPair.private)).let {
                    JcaX509CertificateConverter().setProvider("BC").getCertificate(it)
                }

            // create intermediate CA issuer certificate
            intermIssuerCertificate = X509v3CertificateBuilder(
                X500Name("CN=MDOC Iterm CA"),
                BigInteger.valueOf(SecureRandom().nextLong()),
                Date(),
                Date(System.currentTimeMillis() + 24L * 3600 * 1000),
                X500Name("CN=MDOC Iterm Test Issuer"),
                SubjectPublicKeyInfo.getInstance(intermIssuerKeyPair.public.encoded)
            ).addExtension(Extension.basicConstraints, true, BasicConstraints(false))
                .addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
                .build(JcaContentSignerBuilder("SHA256withECDSA").setProvider("BC").build(intermCaKeyPair.private))
                .let {
                    JcaX509CertificateConverter().setProvider("BC").getCertificate(it)
                }

            // create issuer certificate
            issuerCertificate = X509v3CertificateBuilder(
                X500Name("CN=MDOC ROOT CA"),
                BigInteger.valueOf(SecureRandom().nextLong()),
                Date(),
                Date(System.currentTimeMillis() + 24L * 3600 * 1000),
                X500Name("CN=MDOC Test Issuer"),
                SubjectPublicKeyInfo.getInstance(issuerKeyPair.public.encoded)
            ).addExtension(Extension.basicConstraints, true, BasicConstraints(false))
                .addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
                .build(JcaContentSignerBuilder("SHA256withECDSA").setProvider("BC").build(rootCaKeyPair.private)).let {
                    JcaX509CertificateConverter().setProvider("BC").getCertificate(it)
                }
        }
    }
}
