package id.walt.verifier2.mdocs

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.representations.X5CCertificateString
import id.walt.credentials.representations.X5CList
import id.walt.credentials.signatures.CoseCredentialSignature
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.KeyManager
import id.walt.dcql.DcqlDisclosure
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.MsoMdocMeta
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.LocalResolver
import id.walt.policies2.vc.VCPolicyList
import id.walt.policies2.vc.policies.CredentialSignaturePolicy
import id.walt.policies2.vc.policies.RegexPolicy
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.walt.verifier2.OSSVerifier2FeatureCatalog
import id.walt.verifier2.OSSVerifier2ServiceConfig
import id.walt.ktornotifications.core.KtorSessionNotifications
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.SessionEvent
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.data.VerificationSessionSetup
import id.walt.verifier2.events.Verifier2WebhookRecorder
import id.walt.verifier2.handlers.sessioncreation.VerificationSessionCreationResponse
import id.walt.verifier2.verifierModule
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.server.application.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant


class MsoMdocsVerifier2IntegrationTest {

    private val mdocsDcqlQuery = DcqlQuery(
        credentials = listOf(
            CredentialQuery(
                id = "my_photoid",
                format = CredentialFormat.MSO_MDOC,
                meta = MsoMdocMeta(
                    doctypeValue = "org.iso.23220.photoid.1"
                ),
                claims = listOf(
                    ClaimsQuery(pathStrings = listOf("org.iso.18013.5.1", "family_name_unicode")),
                    ClaimsQuery(pathStrings = listOf("org.iso.18013.5.1", "given_name_unicode")),
                    ClaimsQuery(pathStrings = listOf("org.iso.18013.5.1", "issuing_authority_unicode")),
                    ClaimsQuery(
                        pathStrings = listOf("org.iso.18013.5.1", "resident_postal_code"),
                        values = listOf(1180, 1190, 1200, 1210).map { JsonPrimitive(it) }
                    ),
                    ClaimsQuery(
                        pathStrings = listOf("org.iso.18013.5.1", "issuing_country"),
                        values = listOf(JsonPrimitive("AT"))
                    ),
                    ClaimsQuery(pathStrings = listOf("org.iso.23220.photoid.1", "person_id")),
                    ClaimsQuery(pathStrings = listOf("org.iso.23220.photoid.1", "resident_street")),
                    ClaimsQuery(pathStrings = listOf("org.iso.23220.photoid.1", "administrative_number")),
                    ClaimsQuery(pathStrings = listOf("org.iso.23220.photoid.1", "travel_document_number")),

                    ClaimsQuery(pathStrings = listOf("org.iso.23220.dtc.1", "dtc_version")),
                    ClaimsQuery(pathStrings = listOf("org.iso.23220.dtc.1", "dtc_dg1"))
                )
            )
        )
    )

    private val mdocsPolicies = Verification2Session.DefinedVerificationPolicies(
        vc_policies = VCPolicyList(
            listOf(
                CredentialSignaturePolicy(),
                RegexPolicy(path = "$.['org.iso.23220.dtc.1'].dtc_version", regex = """^("[0-9]+"|-?[0-9]+(\.[0-9]+)?)$""")
            )
        )
    )

    private fun verificationSessionSetup(notifications: KtorSessionNotifications): VerificationSessionSetup =
        CrossDeviceFlowSetup(
            core = GeneralFlowConfig(
                dcqlQuery = mdocsDcqlQuery,
                policies = mdocsPolicies,
                notifications = notifications,
            )
        )

    private val walletCredentials = listOf(
        MdocsCredential(
            credentialData = Json.decodeFromString(
                """
                {
                    "org.iso.18013.5.1": {
                        "family_name": "Doe",
                        "given_name": "John",
                        "birth_date": "1986-03-22",
                        "issue_date": "2019-10-20",
                        "expiry_date": "2024-10-20",
                        "issuing_country": "AT",
                        "issuing_authority": "AT DMV",
                        "document_number": 123456789,
                        "un_distinguishing_sign": "AT"
                    },
                    "docType": "org.iso.18013.5.1.mDL"
                }
                """
            ),
            signed = MDL_FIXTURE_SIGNED_HEX,
            docType = "org.iso.18013.5.1.mDL",
            signature = CoseCredentialSignature(
                x5cList = X5CList(listOf(X5CCertificateString(MDL_FIXTURE_X5C))),
                signerKey = DirectSerializedKey(runBlocking { KeyManager.resolveSerializedKey(MDL_FIXTURE_SIGNER_JWK) })

            ),
        ),
        MdocsCredential(
            credentialData = Json.decodeFromString(
                """
                {
                    "org.iso.18013.5.1": {
                        "family_name_unicode": "Doe",
                        "given_name_unicode": "John",
                        "birth_date": "2000-01-20",
                        "issue_date": "2025-09-12",
                        "expiry_date": "2030-09-12",
                        "issuing_authority_unicode": "LPD Wien 22",
                        "issuing_country": "AT",
                        "sex": 1,
                        "nationality": "AT",
                        "document_number": 1234567890,
                        "name_at_birth": "Max Mustermann",
                        "birthplace": "Baden bei Wien",
                        "resident_address_unicode": "Püchlgasse 1D, 4.4.4.",
                        "resident_city_unicode": "Vienna",
                        "resident_postal_code": 1190,
                        "resident_country": "AT",
                        "age_over_18": true,
                        "age_in_years": 25,
                        "age_birth_year": 2000,
                        "family_name_latin1": "Mustermann",
                        "given_name_latin1": "Max"
                    },
                    "org.iso.23220.photoid.1": {
                        "person_id": "AT12345",
                        "birth_country": "AT",
                        "birth_state": "LOWER AUSTRIA",
                        "birth_city": "Baden bei Wien",
                        "administrative_number": "ATATAT123",
                        "resident_street": "Püchlgasse",
                        "resident_house_number": "1D, 4.4.4.",
                        "travel_document_number": 1122334,
                        "resident_state": "VIENNA"
                    },
                    "org.iso.23220.dtc.1": {
                        "dtc_version": 1,
                        "dtc_dg1": "P<AUTDOE<<JOHN<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<0011223340AUT2001207M3009129<<<<<<<<<<<<<<06"
                    },
                    "docType": "org.iso.23220.photoid.1"
                }
                """
            ),
            signed = "a267646f6354797065776f72672e69736f2e32333232302e70686f746f69642e316c6973737565725369676e6564a26a6e616d65537061636573a3716f72672e69736f2e31383031332e352e3195d818585aa4686469676573744944006672616e646f6d5070f33bd9d920c758b40a5a67b0b0301971656c656d656e744964656e7469666965727366616d696c795f6e616d655f756e69636f64656c656c656d656e7456616c756563446f65d818585aa4686469676573744944016672616e646f6d50f3c575106ca46c9c2ff9851a66218cd871656c656d656e744964656e74696669657272676976656e5f6e616d655f756e69636f64656c656c656d656e7456616c7565644a6f686ed8185858a4686469676573744944026672616e646f6d5077be51d654f521cbe0b4d71b6b06e73871656c656d656e744964656e7469666965726a62697274685f646174656c656c656d656e7456616c75656a323030302d30312d3230d8185858a4686469676573744944036672616e646f6d5043951c9c558540977286a38223d7da2671656c656d656e744964656e7469666965726a69737375655f646174656c656c656d656e7456616c75656a323032352d30392d3132d8185859a4686469676573744944046672616e646f6d50bdc7002c6bc0cfa087ad6e3555540e1971656c656d656e744964656e7469666965726b6578706972795f646174656c656c656d656e7456616c75656a323033302d30392d3132d8185869a4686469676573744944056672616e646f6d5083ddcfb8fa8a249e3fed9139dd707b2471656c656d656e744964656e746966696572781969737375696e675f617574686f726974795f756e69636f64656c656c656d656e7456616c75656b4c5044205769656e203232d8185855a4686469676573744944066672616e646f6d50dac3c52ea12467e7fa504d067f819a3e71656c656d656e744964656e7469666965726f69737375696e675f636f756e7472796c656c656d656e7456616c7565624154d8185847a4686469676573744944076672616e646f6d504059cf49f5cafa8992bde228c81a10d771656c656d656e744964656e746966696572637365786c656c656d656e7456616c756501d8185851a4686469676573744944086672616e646f6d502aaf6252abd4be5fde07d5e8ec17764d71656c656d656e744964656e7469666965726b6e6174696f6e616c6974796c656c656d656e7456616c7565624154d8185857a4686469676573744944096672616e646f6d503579bef05340e5b5a4b38fcb23cd6a5b71656c656d656e744964656e7469666965726f646f63756d656e745f6e756d6265726c656c656d656e7456616c75651a499602d2d818585fa46864696765737449440a6672616e646f6d509c1bfb43176dbcc8e494b3a2db4c1f7b71656c656d656e744964656e7469666965726d6e616d655f61745f62697274686c656c656d656e7456616c75656e4d6178204d75737465726d616e6ed818585ca46864696765737449440b6672616e646f6d50f4f4d96ff2742ba24b6127014d91f0f871656c656d656e744964656e7469666965726a6269727468706c6163656c656c656d656e7456616c75656e426164656e20626569205769656ed8185873a46864696765737449440c6672616e646f6d50e2dd66b0bb4a051ab279e46ccf5bccda71656c656d656e744964656e74696669657278187265736964656e745f616464726573735f756e69636f64656c656c656d656e7456616c75657650c3bc63686c67617373652031442c20342e342e342ed818585fa46864696765737449440d6672616e646f6d50bd40101143beb7486e9c549aeb7a1d3d71656c656d656e744964656e746966696572757265736964656e745f636974795f756e69636f64656c656c656d656e7456616c7565665669656e6e61d818585aa46864696765737449440e6672616e646f6d5091a12b534a9e1c2ca8163433194d7ebe71656c656d656e744964656e746966696572747265736964656e745f706f7374616c5f636f64656c656c656d656e7456616c75651904a6d8185856a46864696765737449440f6672616e646f6d5022b3f3bf92edf83f927dcccc5869a64571656c656d656e744964656e746966696572707265736964656e745f636f756e7472796c656c656d656e7456616c7565624154d818584fa4686469676573744944106672616e646f6d50ce9556ef76f563de61fbe58721ba43b971656c656d656e744964656e7469666965726b6167655f6f7665725f31386c656c656d656e7456616c7565f5d8185851a4686469676573744944116672616e646f6d5082894d511da1a2173d73456686eba50471656c656d656e744964656e7469666965726c6167655f696e5f79656172736c656c656d656e7456616c75651819d8185854a4686469676573744944126672616e646f6d50fed9f0d45626eb52884d08b0a73fb3e271656c656d656e744964656e7469666965726e6167655f62697274685f796561726c656c656d656e7456616c75651907d0d8185860a4686469676573744944136672616e646f6d5056be2380676f55d5a0d473bc01954a2071656c656d656e744964656e7469666965727266616d696c795f6e616d655f6c6174696e316c656c656d656e7456616c75656a4d75737465726d616e6ed8185858a4686469676573744944146672616e646f6d50db91f00f2b3cf9db85e7b1693a7d05e071656c656d656e744964656e74696669657271676976656e5f6e616d655f6c6174696e316c656c656d656e7456616c7565634d6178776f72672e69736f2e32333232302e70686f746f69642e3189d8185854a4686469676573744944006672616e646f6d50ae1185ee6305e889942740573f86c98371656c656d656e744964656e74696669657269706572736f6e5f69646c656c656d656e7456616c75656741543132333435d8185853a4686469676573744944016672616e646f6d506191db3e3068cf18655e40a181541be871656c656d656e744964656e7469666965726d62697274685f636f756e7472796c656c656d656e7456616c7565624154d818585ca4686469676573744944026672616e646f6d50201fbe5779cfc9f0432b64f41774493771656c656d656e744964656e7469666965726b62697274685f73746174656c656c656d656e7456616c75656d4c4f5745522041555354524941d818585ca4686469676573744944036672616e646f6d50ab9343a13b066c74d386bd72bd6e0f9f71656c656d656e744964656e7469666965726a62697274685f636974796c656c656d656e7456616c75656e426164656e20626569205769656ed8185862a4686469676573744944046672616e646f6d503c936db5c12d1b058307f5ae291c460a71656c656d656e744964656e7469666965727561646d696e6973747261746976655f6e756d6265726c656c656d656e7456616c756569415441544154313233d818585ea4686469676573744944056672616e646f6d506954b7c846acf278a8b9f749efb78fee71656c656d656e744964656e7469666965726f7265736964656e745f7374726565746c656c656d656e7456616c75656b50c3bc63686c6761737365d8185863a4686469676573744944066672616e646f6d509975cd1814249553bd1327bf06628b3571656c656d656e744964656e746966696572757265736964656e745f686f7573655f6e756d6265726c656c656d656e7456616c75656a31442c20342e342e342ed818585ea4686469676573744944076672616e646f6d50be415b0737fba19317a2fff1d9d158ca71656c656d656e744964656e7469666965727674726176656c5f646f63756d656e745f6e756d6265726c656c656d656e7456616c75651a0011201ed8185858a4686469676573744944086672616e646f6d50177fe489f9714e45631a72bb766ee8da71656c656d656e744964656e7469666965726e7265736964656e745f73746174656c656c656d656e7456616c7565665649454e4e41736f72672e69736f2e32333232302e6474632e3182d818584fa4686469676573744944006672616e646f6d5009577603b0002d3ec24fe6c354332d5471656c656d656e744964656e7469666965726b6474635f76657273696f6e6c656c656d656e7456616c756501d81858a4a4686469676573744944016672616e646f6d5046dd8f831eaf2f8ed620618b898ba38071656c656d656e744964656e746966696572676474635f6467316c656c656d656e7456616c75657858503c415554444f453c3c4a4f484e3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c30303131323233333430415554323030313230374d333030393132393c3c3c3c3c3c3c3c3c3c3c3c3c3c30366a697373756572417574688443a10126a1182159020d30820209308201b0a003020102021470435003e08ea8e6c1277d3234dcd0406cd8dac7300a06082a8648ce3d0403023028310b30090603550406130241543119301706035504030c1057616c74696420546573742049414341301e170d3236303130313030303030305a170d3436303130313030303030305a3033310b30090603550406130241543124302206035504030c1b57616c746964205465737420446f63756d656e74205369676e65723059301306072a8648ce3d020106082a8648ce3d030107034200045d670fb64d70aed9043ad9c91deb85a454f35f7ce370b39bda988c23336c52142698f549a06e7a10a9ea7b527430e953ed81eadf72b202dcc58eb9d4f73ac00ca381ac3081a9301f0603551d23041830168014d778df224b74775ce7982407d0e28551b9e093f0301d0603551d0e041604142451d2f920a0a3907a3d1a94a49d38b08ba41b3a300e0603551d0f0101ff040403020080301a0603551d1204133011860f68747470733a2f2f77616c742e696430150603551d250101ff040b3009060728818c5d05010230240603551d1f041d301b3019a017a015861368747470733a2f2f77616c742e69642f63726c300a06082a8648ce3d040302034700304402201649928d6b142c61c87e2f2e30b2119bf8d71842b76e550de0a6bbb279bc88370220462bb4c8c5771689efa42bdec2d8fdd152e2892692b6e5a5d519e61b4a67512c5905c9d8185905c4a66776657273696f6e63312e306f646967657374416c676f726974686d675348412d3235366c76616c756544696765737473a3716f72672e69736f2e31383031332e352e31b5005820aea592ddef2aeb74381ca77566365198bc5ca480a26cbcfd418ae7d0d26367a3015820da6d18218eecebf08547cd49f8a1de48b880b3effd51c5250af4a3dabd21d464025820427f5f2eff1e250b20b058e268f441005bce08c405475e0a19744cdb0cfe16e90358203f3f3707eb36dc80471223aa731506ee291854a6c7d5defb1aaac8b54d21c8b804582031508ec8c92ee15e17d34c74bbd98940a4127003dd55ee22f57af185514b4816055820e7aec50f0a2d93eab8024262044854f5d6542a871d60aed2cc191e7ad55b61f0065820861386931d2ab371cecca31b0909e9934cce35f8eed66d00e856678185c6bc13075820e6f81c95f0e3d15ca6acb9cc1d144b6d6de304bc07e7d610fd76de7291b9bbf208582027e0642f5b9cc4bdee6ed0f605c4360ba019a03d455269f099b6263cb24dfc13095820db9e4f284399a34c4b0f842cd066f084fdf256efcb31948733e153935e30aefc0a582017d65c0db8331a7ade5fc1510c9427f3d6652c4234899d4e4d69768fbec1f39c0b58203f41d0505d4ac5b26c2b4fc22b496f9713f5d6d3ffd0f8897810d75abe9dd1230c5820cf52ef397c33d197c66ddcbf638d70adb43b52377f23e394cba6570e40251d2e0d582073cd6f037f787b104d88760b566a34ffc291a2504dd3282a2b792f2e13ad3bff0e5820146696af06c5e99ac6f0a5c61a6d3f9ede8aaa0ef36c9c2e7d366eadf1acf2460f5820265e582512a1cbb772f346f0d50af589faf30f79d7c9768d7c9425b3a02720fc10582045049fd29c210f009a77f1d8f0f193cddd5678bf6ef5449d186684137f8cc5d51158202611f542a5a16b2b1c2911052d16b2626fbf2fa98ed200d64ee1c4518b58c795125820f67c243f71bb9b392396544ea9c19f0f80005187d6082797fdb3f45fa1392cdb1358208c95bba02bb847e41f5c09edc84871d84eb1ec5538cb9d2f85c6613e3a43ff171458200c33f8ba4ed3b1c84544b670527efbd8bcec13f23835836147ab7e22e3792536776f72672e69736f2e32333232302e70686f746f69642e31a9005820acafe4c641de9c77cd9ef2c2d008c28c2e3eb4d87e9d8ec95cc5943d9cab73c1015820273816ee73a145b57ff3f7a35bb16716935e832a77bc8356720cd3dee9de903c025820e1f47505c2231d9860e58afcef9922587ac878d859afba0e5c648d1ecf5479130358206abaaabdae14cba06914eef4d4f33fab2b1406e42de2e79268dfc3a10a26ca050458204561151f18ed1b8bfd5ccafdda5bed793296b7de3166a58c4d5572253ec8acb9055820decd17239701d27b78b3dd4ba7487f9f3f2670a64292e9eb902235dbd3dc910706582017ae306e813e496df795dca91382a3d55e621db09e4a5dcd7dfd8b8c046a7377075820a356de52bf7d6233fc8c00f124950ac2fe23e5cee8724bda69601ccc719dbe13085820bdff0ed0da08b3c2ffaf6ddfc86c36785c2b621d33c5b39167ab2be0a31e3500736f72672e69736f2e32333232302e6474632e31a2005820c88336a5723e9d9558a10c57662deb7fbd7c9a3f44562639ec3c20aac2a7ea760158205e5506ca68aa6145d53025ba84c9fd74cce4bcc522eafd629f8c5aaf58836ba86d6465766963654b6579496e666fa1696465766963654b6579a4010220012158207934f659dce598e58122d7604a6b2a075fc13faf707eec0e7b5218bda63559d2225820c1b3aedc63f4d8988e548450fee7d62d134e98307ab1e60069b0a6b0605faffe67646f6354797065776f72672e69736f2e32333232302e70686f746f69642e316c76616c6964697479496e666fa3667369676e6564c074323032362d30312d30325430303a30303a30305a6976616c696446726f6dc074323032362d30312d30325430303a30303a30305a6a76616c6964556e74696cc074323034352d31322d33315430303a30303a30305a5840c0a07ba4d68a673b93c34b110dae9e7e29fd10b54acac26c23a07457ced4e09d8c8a64facd164b5798129e5e101553cff5f1ed1c48983e279f392a1633d48bb8",
            docType = "org.iso.23220.photoid.1",
            signature = CoseCredentialSignature(
                x5cList = X5CList(listOf(X5CCertificateString(MDL_FIXTURE_X5C))),
                signerKey = DirectSerializedKey(runBlocking { KeyManager.resolveSerializedKey(MDL_FIXTURE_SIGNER_JWK) })
            ),
        )
    )

    private val holderKeyFun = suspend { KeyManager.resolveSerializedKey(HOLDER_SERIALIZED_KEY) }

    private suspend fun selectCredentialsForQuery(
        query: DcqlQuery,
    ): Map<String, List<DcqlMatcher.DcqlMatchResult>> {
        val storedCredentials = walletCredentials

        val dcqlCredentials = storedCredentials.mapIndexed { idx, credential ->
            RawDcqlCredential(
                id = idx.toString(),
                format = credential.format,
                data = credential.credentialData,
                originalCredential = credential,
                // walletCredentials only contains MdocsCredential in this test; no SD disclosures
                disclosures = null
            )
        }

        val matched = DcqlMatcher.match(query, dcqlCredentials).getOrThrow()
        if (matched.isEmpty()) {
            throw IllegalArgumentException("No matching credential")
        }

        return matched
    }

    @Test
    fun test() = runVerifierWalletFlow(port = 17011, clientId = "verifier2")

    @Test
    fun `omitted clientId generates redirect_uri and wallet presents`() =
        runVerifierWalletFlow(port = 17012, clientId = null)

    private fun runVerifierWalletFlow(port: Int, clientId: String?) {
        val host = "127.0.0.1"
        Verifier2WebhookRecorder().start().use { webhook ->
        E2ETest(host, port, true).testBlock(
            features = listOf(OSSVerifier2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "verifier-service", OSSVerifier2ServiceConfig(
                        clientId = clientId,
                        clientMetadata = ClientMetadata(
                            clientName = "Verifier2",
                            logoUri = "https://images.squarespace-cdn.com/content/v1/609c0ddf94bcc0278a7cbdb4/4d493ccf-c893-4882-925f-fda3256c38f4/Walt.id_Logo_transparent.png"
                        ),
                        urlPrefix = "http://$host:$port/verification-session",
                        urlHost = "openid4vp://authorize"
                    )
                )
            },
            init = {
                DidService.apply {
                    registerResolver(LocalResolver())
                    updateResolversForMethods()
                }
            },
            module = Application::verifierModule
        ) {
            val http = testHttpClient()

            // Create the verification session
            val verificationSessionResponse = testAndReturn("Create verification session") {
                http.post("/verification-session/create") {
                    setBody(verificationSessionSetup(webhook.notifications()))
                }.body<VerificationSessionCreationResponse>()
            }
            println("Verification Session Response: $verificationSessionResponse")

            // Check verification session
            test("Check Verification Session Response") {
                assertTrue {
                    verificationSessionResponse.bootstrapAuthorizationRequestUrl.toString().length < verificationSessionResponse.fullAuthorizationRequestUrl.toString().length
                }
            }

            val sessionId = verificationSessionResponse.sessionId

            // View created session
            val info1 = testAndReturn("View created session") {
                http.get("/verification-session/$sessionId/info")
                    .body<Verification2Session>()
            }

            // Check created session
            test("Check Verification Session") {
                assertTrue {
                    info1.creationDate.wasWithinLastSeconds()
                }
            }

            if (clientId == null) {
                val responseUri = "http://$host:$port/verification-session/$sessionId/response"
                test("Omitted clientId is generated as redirect_uri bound to response_uri") {
                    assertEquals(responseUri, info1.authorizationRequest.responseUri)
                    assertEquals("redirect_uri:$responseUri", info1.authorizationRequest.clientId)
                    assertEquals("redirect_uri:$responseUri", info1.bootstrapAuthorizationRequest?.clientId)
                }
            }

            // Present with wallet
            val bootstrapUrl = verificationSessionResponse.bootstrapAuthorizationRequestUrl

            val holderKey = holderKeyFun()

            val selectCallback: suspend (DcqlQuery) -> Map<String, List<DcqlMatcher.DcqlMatchResult>> = { query ->
                selectCredentialsForQuery(
                    query = query
                )
            }

            val presentationResult = testAndReturn("Present with wallet") {
                WalletPresentFunctionality2.walletPresentHandling(
                    holderKey = holderKey,
                    holderDid = null, // No DID required for mso_mdoc
                    presentationRequestUrl = bootstrapUrl!!,
                    selectCredentialsForQuery = selectCallback,
                    holderPoliciesToRun = null,
                    runPolicies = null,
                    transactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
                )
            }

            println("Presentation result: $presentationResult")

            // Check presentation result by wallet
            test("Verify presentation result") {
                assertTrue { presentationResult.isSuccess }

                val resp = presentationResult.getOrThrow()
                assertTrue("Transmission success is false") { resp.transmissionSuccess == true }
                assertTrue { resp.verifierResponse!!.jsonObject["status"]!!.jsonPrimitive.content == "received" }
            }

            // View session that was presented to
            val info2 = testAndReturn("View presented session") {
                http.get("/verification-session/$sessionId/info")
                    .body<Verification2Session>()
            }

            // Check created session
            test("Check Verification Session after presentation") {
                assertTrue { info2.attempted }
                assertTrue { info2.status == Verification2Session.VerificationSessionStatus.SUCCESSFUL }

                assertNotNull(info2.presentedCredentials)
                assertNotNull(info2.presentedCredentials!!["my_photoid"])
                assertTrue { info2.presentedCredentials!!["my_photoid"]!!.size == 1 }

                assertNotNull(info2.policyResults)
                assertTrue { info2.policyResults!!.overallSuccess }
                assertTrue { info2.policyResults!!.vcPolicies.size == 2 }
            }

            test("Emit successful verification callback events") {
                webhook.assertReceivedInOrder(
                    sessionId,
                    Verifier2WebhookRecorder.successfulPresentationEvents,
                )
                webhook.assertDoesNotContain(sessionId, SessionEvent.presentation_validation_failed)
                webhook.assertDoesNotContain(sessionId, SessionEvent.dcql_fulfillment_check_failed)
                webhook.assertDoesNotContain(sessionId, SessionEvent.wallet_error_response_received)
            }
        }
        }
    }

    // Utils:
    private fun Instant.wasWithinLastSeconds(): Boolean {
        val now = Clock.System.now()
        return this <= now && (now - this) <= 1500.milliseconds
    }
}
