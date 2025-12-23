@file:OptIn(ExperimentalSerializationApi::class)

package interop

import at.asitplus.iso.IssuerSigned
import at.asitplus.iso.MobileSecurityObject
import at.asitplus.signum.indispensable.cosef.io.coseCompliantSerializer
import at.asitplus.wallet.mdl.DrivingPrivilege
import id.walt.mdoc.dataelement.*
import id.walt.mdoc.dataretrieval.DeviceResponse
import id.walt.mdoc.issuersigned.IssuerSignedItem
import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.io.encoding.Base64
import kotlin.test.*

class ASITTest {

    @Test
    fun testDeviceResponse() {

        val isoSpecExampleSignedDeviceResponse =
            "a36776657273696f6e63312e3069646f63756d656e747381a367646f6354797065756f72672e69736f2e31383031332e352e312e6d444c6c6973737565725369676e6564a26a6e616d65537061636573a1716f72672e69736f2e31383031332e352e3186d8185863a4686469676573744944006672616e646f6d58208798645b20ea200e19ffabac92624bee6aec63aceedecfb1b80077d22bfc20e971656c656d656e744964656e7469666965726b66616d696c795f6e616d656c656c656d656e7456616c756563446f65d818586ca4686469676573744944036672616e646f6d5820b23f627e8999c706df0c0a4ed98ad74af988af619b4bb078b89058553f44615d71656c656d656e744964656e7469666965726a69737375655f646174656c656c656d656e7456616c7565d903ec6a323031392d31302d3230d818586da4686469676573744944046672616e646f6d5820c7ffa307e5de921e67ba5878094787e8807ac8e7b5b3932d2ce80f00f3e9abaf71656c656d656e744964656e7469666965726b6578706972795f646174656c656c656d656e7456616c7565d903ec6a323032342d31302d3230d818586da4686469676573744944076672616e646f6d582026052a42e5880557a806c1459af3fb7eb505d3781566329d0b604b845b5f9e6871656c656d656e744964656e7469666965726f646f63756d656e745f6e756d6265726c656c656d656e7456616c756569313233343536373839d818590471a4686469676573744944086672616e646f6d5820d094dad764a2eb9deb5210e9d899643efbd1d069cc311d3295516ca0b024412d71656c656d656e744964656e74696669657268706f7274726169746c656c656d656e7456616c7565590412ffd8ffe000104a46494600010101009000900000ffdb004300130d0e110e0c13110f11151413171d301f1d1a1a1d3a2a2c2330453d4947443d43414c566d5d4c51685241435f82606871757b7c7b4a5c869085778f6d787b76ffdb0043011415151d191d381f1f38764f434f7676767676767676767676767676767676767676767676767676767676767676767676767676767676767676767676767676ffc00011080018006403012200021101031101ffc4001b00000301000301000000000000000000000005060401020307ffc400321000010303030205020309000000000000010203040005110612211331141551617122410781a1163542527391b2c1f1ffc4001501010100000000000000000000000000000001ffc4001a110101010003010000000000000000000000014111213161ffda000c03010002110311003f00a5bbde22da2329c7d692bc7d0d03f52cfb0ff75e7a7ef3e7709723a1d0dae146ddfbb3c039ce07ad2bd47a7e32dbb8dd1d52d6ef4b284f64a480067dfb51f87ffb95ff00eb9ff14d215de66af089ce44b7dbde9cb6890a2838eddf18078f7add62d411ef4db9b10a65d6b95a147381ea0d495b933275fe6bba75c114104a8ba410413e983dff004f5af5d34b4b4cde632d0bf1fd1592bdd91c6411f3934c2fa6af6b54975d106dcf4a65ae56e856001ebc03c7ce29dd9eef1ef10fc447dc9da76ad2aee93537a1ba7e4f70dd8eff0057c6dffb5e1a19854a83758e54528750946ec6704850cd037bceb08b6d7d2cc76d3317fc7b5cc04fb6707269c5c6e0c5b60ae549242123b0e493f602a075559e359970d98db89525456b51c951c8afa13ea8e98e3c596836783d5c63f5a61a99fdb7290875db4be88ab384bbbbbfc7183fdeaa633e8951db7da396dc48524fb1a8bd611a5aa2a2432f30ab420a7a6d3240c718cf031fa9ef4c9ad550205aa02951df4a1d6c8421b015b769db8c9229837ea2be8b1b0d39d0eba9c51484efdb8c0efd8d258daf3c449699f2edbd4584e7af9c64e3f96b9beb28d4ac40931e6478c8e76a24a825449501d867d2b1dcdebae99b9c752ae4ecd6dde4a179c1c1e460938f9149ef655e515c03919a289cb3dca278fb7bf177f4faa829dd8ce3f2ac9a7ecde490971fafd7dce15eed9b71c018c64fa514514b24e8e4f8c5c9b75c1e82579dc1233dfec08238f6add62d391acc1c5256a79e706d52d431c7a0145140b9fd149eb3a60dc5e88cbbc2da092411e9dc71f39a7766b447b344e847dcac9dcb5abba8d145061d43a6fcf1e65cf15d0e90231d3dd9cfe62995c6dcc5ca12a2c904a15f71dd27d451453e09d1a21450961cbb3ea8a956433b781f1ce33dfed54f0e2b50a2b71d84ed6db18028a28175f74fc6bda105c529a791c25c4f3c7a11f71586268f4a66b726e33de9ea6f1b52b181c760724e47b514520a5a28a283ffd9d81858ffa4686469676573744944096672616e646f6d58204599f81beaa2b20bd0ffcc9aa03a6f985befab3f6beaffa41e6354cdb2ab2ce471656c656d656e744964656e7469666965727264726976696e675f70726976696c656765736c656c656d656e7456616c756582a37576656869636c655f63617465676f72795f636f646561416a69737375655f64617465d903ec6a323031382d30382d30396b6578706972795f64617465d903ec6a323032342d31302d3230a37576656869636c655f63617465676f72795f636f646561426a69737375655f64617465d903ec6a323031372d30322d32336b6578706972795f64617465d903ec6a323032342d31302d32306a697373756572417574688443a10126a118215901f3308201ef30820195a00302010202143c4416eed784f3b413e48f56f075abfa6d87eb84300a06082a8648ce3d04030230233114301206035504030c0b75746f7069612069616361310b3009060355040613025553301e170d3230313030313030303030305a170d3231313030313030303030305a30213112301006035504030c0975746f706961206473310b30090603550406130255533059301306072a8648ce3d020106082a8648ce3d03010703420004ace7ab7340e5d9648c5a72a9a6f56745c7aad436a03a43efea77b5fa7b88f0197d57d8983e1b37d3a539f4d588365e38cbbf5b94d68c547b5bc8731dcd2f146ba381a83081a5301e0603551d120417301581136578616d706c65406578616d706c652e636f6d301c0603551d1f041530133011a00fa00d820b6578616d706c652e636f6d301d0603551d0e0416041414e29017a6c35621ffc7a686b7b72db06cd12351301f0603551d2304183016801454fa2383a04c28e0d930792261c80c4881d2c00b300e0603551d0f0101ff04040302078030150603551d250101ff040b3009060728818c5d050102300a06082a8648ce3d040302034800304502210097717ab9016740c8d7bcdaa494a62c053bbdecce1383c1aca72ad08dbc04cbb202203bad859c13a63c6d1ad67d814d43e2425caf90d422422c04a8ee0304c0d3a68d5903a2d81859039da66776657273696f6e63312e306f646967657374416c676f726974686d675348412d3235366c76616c756544696765737473a2716f72672e69736f2e31383031332e352e31ad00582075167333b47b6c2bfb86eccc1f438cf57af055371ac55e1e359e20f254adcebf01582067e539d6139ebd131aef441b445645dd831b2b375b390ca5ef6279b205ed45710258203394372ddb78053f36d5d869780e61eda313d44a392092ad8e0527a2fbfe55ae0358202e35ad3c4e514bb67b1a9db51ce74e4cb9b7146e41ac52dac9ce86b8613db555045820ea5c3304bb7c4a8dcb51c4c13b65264f845541341342093cca786e058fac2d59055820fae487f68b7a0e87a749774e56e9e1dc3a8ec7b77e490d21f0e1d3475661aa1d0658207d83e507ae77db815de4d803b88555d0511d894c897439f5774056416a1c7533075820f0549a145f1cf75cbeeffa881d4857dd438d627cf32174b1731c4c38e12ca936085820b68c8afcb2aaf7c581411d2877def155be2eb121a42bc9ba5b7312377e068f660958200b3587d1dd0c2a07a35bfb120d99a0abfb5df56865bb7fa15cc8b56a66df6e0c0a5820c98a170cf36e11abb724e98a75a5343dfa2b6ed3df2ecfbb8ef2ee55dd41c8810b5820b57dd036782f7b14c6a30faaaae6ccd5054ce88bdfa51a016ba75eda1edea9480c5820651f8736b18480fe252a03224ea087b5d10ca5485146c67c74ac4ec3112d4c3a746f72672e69736f2e31383031332e352e312e5553a4005820d80b83d25173c484c5640610ff1a31c949c1d934bf4cf7f18d5223b15dd4f21c0158204d80e1e2e4fb246d97895427ce7000bb59bb24c8cd003ecf94bf35bbd2917e340258208b331f3b685bca372e85351a25c9484ab7afcdf0d2233105511f778d98c2f544035820c343af1bd1690715439161aba73702c474abf992b20c9fb55c36a336ebe01a876d6465766963654b6579496e666fa1696465766963654b6579a40102200121582096313d6c63e24e3372742bfdb1a33ba2c897dcd68ab8c753e4fbd48dca6b7f9a2258201fb3269edd418857de1b39a4e4a44b92fa484caa722c228288f01d0c03a2c3d667646f6354797065756f72672e69736f2e31383031332e352e312e6d444c6c76616c6964697479496e666fa3667369676e6564c074323032302d31302d30315431333a33303a30325a6976616c696446726f6dc074323032302d31302d30315431333a33303a30325a6a76616c6964556e74696cc074323032312d31302d30315431333a33303a30325a584059e64205df1e2f708dd6db0847aed79fc7c0201d80fa55badcaf2e1bcf5902e1e5a62e4832044b890ad85aa53f129134775d733754d7cb7a413766aeff13cb2e6c6465766963655369676e6564a26a6e616d65537061636573d81841a06a64657669636541757468a1696465766963654d61638443a10105a0f65820e99521a85ad7891b806a07f8b5388a332d92c189a7bf293ee1f543405ae6824d6673746174757300"

        val deviceResponse = DeviceResponse.fromCBORHex(isoSpecExampleSignedDeviceResponse)

        val asitDeviceResponse = coseCompliantSerializer.decodeFromHexString<at.asitplus.iso.DeviceResponse>(
            isoSpecExampleSignedDeviceResponse
        )

        assertEquals(
            expected = deviceResponse.version.value,
            actual = asitDeviceResponse.version,
        )

        assertEquals(
            expected = deviceResponse.status.value.toLong().toUInt(),
            actual = asitDeviceResponse.status,
        )

        assertEquals(
            expected = deviceResponse.documents.size,
            actual = asitDeviceResponse.documents!!.size,
        )

        val asitMDocList = asitDeviceResponse.documents!!

        deviceResponse.documents.zip(asitMDocList).forEach { (waltMDoc, asitMDoc) ->

            assertEquals(
                expected = waltMDoc.docType.value,
                actual = asitMDoc.docType,
            )

            assertTrue {
                waltMDoc.issuerSigned.nameSpaces!!.keys.containsAll(asitMDoc.issuerSigned.namespaces!!.keys)
            }

            val waltMdlNamespace = assertNotNull(waltMDoc.issuerSigned.nameSpaces).run {
                assertNotNull(this[MDL_NAMESPACE_ID])
            }

            val waltMdlNamespaceIssuerSignedItemMap = waltMdlNamespace.map { waltMdlNamespaceEncodedEntry ->
                IssuerSignedItem.fromMapElement(waltMdlNamespaceEncodedEntry.decodeDataElement<MapElement>())
            }.associateBy { issuerSignedItem ->
                issuerSignedItem.elementIdentifier.value
            }

            val asitMdlNamespace = assertNotNull(asitMDoc.issuerSigned.namespaces).run {
                assertNotNull(this[MDL_NAMESPACE_ID])
            }

            val asitMdlNamespaceIssuerSignedItemMap = asitMdlNamespace.entries.map { asitMdlNamespaceEncodedEntry ->
                asitMdlNamespaceEncodedEntry
            }.associateBy { issuerSignedItem ->
                issuerSignedItem.value.elementIdentifier
            }

            val expectedElementIdentifiers = setOf(
                "family_name",
                "issue_date",
                "expiry_date",
                "document_number",
                "portrait",
                "driving_privileges",
            )

            assertEquals(
                expected = expectedElementIdentifiers,
                actual = waltMdlNamespaceIssuerSignedItemMap.keys
            )


            assertEquals(
                expected = waltMdlNamespaceIssuerSignedItemMap.keys,
                actual = asitMdlNamespaceIssuerSignedItemMap.keys,
            )

            //check that all serialized issuer signed items have the same byte array contents
            expectedElementIdentifiers.forEach { expectedElementId ->
                assertContentEquals(
                    expected = asitMdlNamespaceIssuerSignedItemMap[expectedElementId]!!.serialized,
                    actual = waltMdlNamespaceIssuerSignedItemMap[expectedElementId]!!.toMapElement().toCBOR(),
                )
            }

            assertEquals(
                expected = (waltMdlNamespaceIssuerSignedItemMap["family_name"]!!.elementValue as StringElement).value,
                actual = (asitMdlNamespaceIssuerSignedItemMap["family_name"]!!.value.elementValue as String)
            )

            val asitIssueDate = assertDoesNotThrow {
                (asitMdlNamespaceIssuerSignedItemMap["issue_date"]!!.value.elementValue as LocalDate).run {
                    assertEquals(
                        expected = "2019-10-20",
                        actual = this.toString(),
                    )
                    this
                }
            }

            val waltIssueDate = waltMdlNamespaceIssuerSignedItemMap["issue_date"]?.let {
                (it.elementValue as FullDateElement).value
            }

            assertEquals(
                expected = asitIssueDate,
                actual = waltIssueDate,
            )

            val asitExpiryDate = assertDoesNotThrow {
                (asitMdlNamespaceIssuerSignedItemMap["expiry_date"]!!.value.elementValue as LocalDate).run {
                    assertEquals(
                        expected = "2024-10-20",
                        actual = this.toString(),
                    )
                    this
                }
            }

            val waltExpiryDate = waltMdlNamespaceIssuerSignedItemMap["expiry_date"]?.let {
                (it.elementValue as FullDateElement).value
            }

            assertEquals(
                expected = asitExpiryDate,
                actual = waltExpiryDate,
            )

            assertEquals(
                expected = (waltMdlNamespaceIssuerSignedItemMap["document_number"]!!.elementValue as StringElement).value,
                actual = (asitMdlNamespaceIssuerSignedItemMap["document_number"]!!.value.elementValue as String)
            )

            assertContentEquals(
                expected = (waltMdlNamespaceIssuerSignedItemMap["portrait"]!!.elementValue as ByteStringElement).value,
                actual = asitMdlNamespaceIssuerSignedItemMap["portrait"]!!.value.elementValue as ByteArray
            )

            val asitDrivingPrivilegeList = assertDoesNotThrow {
                asitMdlNamespaceIssuerSignedItemMap["driving_privileges"]!!.value.elementValue as Array<*>
            }.run {
                assertIs<Array<DrivingPrivilege>>(this)
            }.toList()

            val waltDrivingPrivilegeList = assertDoesNotThrow {
                waltMdlNamespaceIssuerSignedItemMap["driving_privileges"]!!.elementValue as ListElement
            }.value.map {
                it as MapElement
            }

            assertEquals(
                expected = asitDrivingPrivilegeList.size,
                actual = waltDrivingPrivilegeList.size,
            )

            asitDrivingPrivilegeList.zip(waltDrivingPrivilegeList) { asitDrivingPrivilege, waltDrivingPrivilege ->

                assertEquals(
                    expected = asitDrivingPrivilege.vehicleCategoryCode,
                    actual = (waltDrivingPrivilege.value[MapKey("vehicle_category_code")] as StringElement).value,
                )

                assertEquals(
                    expected = asitDrivingPrivilege.issueDate,
                    actual = (waltDrivingPrivilege.value[MapKey("issue_date")] as FullDateElement).value,
                )

                assertEquals(
                    expected = asitDrivingPrivilege.expiryDate,
                    actual = (waltDrivingPrivilege.value[MapKey("expiry_date")] as FullDateElement).value,
                )
            }

            //done with issuer signed namespace contents

            assertTrue {
                waltMDoc.deviceSigned!!.toMapElement().toCBOR()
                    .contentEquals(coseCompliantSerializer.encodeToByteArray(asitMDoc.deviceSigned))
            }

            assertTrue {
                waltMDoc.issuerSigned.issuerAuth!!.toCBOR().contentEquals(
                    asitMDoc.issuerSigned.issuerAuth.serialize(
                        MobileSecurityObject.serializer()
                    )
                )
            }

            val mso = waltMDoc.MSO!!

            val asitMSO = asitMDoc.issuerSigned.issuerAuth.payload!!

            assertEquals(
                expected = mso.version.value,
                actual = asitMSO.version,
            )

            assertEquals(
                expected = mso.digestAlgorithm.value,
                actual = asitMSO.digestAlgorithm,
            )

            assertEquals(
                expected = mso.docType.value,
                actual = asitMSO.docType,
            )

        }
    }

    @Test
    fun testWaltIdGeneratedIssuerSigned() {

        val waltIdIssuerSignedBase64Url =
            "ompuYW1lU3BhY2VzoXFvcmcuaXNvLjE4MDEzLjUuMYvYGFhSpGhkaWdlc3RJRABmcmFuZG9tUNSPxfJM5NP_qY1vTZYAp6NxZWxlbWVudElkZW50aWZpZXJrZmFtaWx5X25hbWVsZWxlbWVudFZhbHVlY0RvZdgYWFKkaGRpZ2VzdElEAWZyYW5kb21QMCqA9Utiwo7ih10IM_8k9nFlbGVtZW50SWRlbnRpZmllcmpnaXZlbl9uYW1lbGVsZW1lbnRWYWx1ZWRKb2hu2BhYW6RoZGlnZXN0SUQCZnJhbmRvbVASJnpHaI7WheaRvKm_HZ9jcWVsZW1lbnRJZGVudGlmaWVyamJpcnRoX2RhdGVsZWxlbWVudFZhbHVl2QPsajE5ODYtMDMtMjLYGFhbpGhkaWdlc3RJRANmcmFuZG9tUG5gTOxIdzwhjxoH6w23y_JxZWxlbWVudElkZW50aWZpZXJqaXNzdWVfZGF0ZWxlbGVtZW50VmFsdWXZA-xqMjAxOS0xMC0yMNgYWFykaGRpZ2VzdElEBGZyYW5kb21QpX-4BVI1LMvXTYlihGgjdHFlbGVtZW50SWRlbnRpZmllcmtleHBpcnlfZGF0ZWxlbGVtZW50VmFsdWXZA-xqMjAyNC0xMC0yMNgYWFWkaGRpZ2VzdElEBWZyYW5kb21Q1bG1e_F-iBc60MXMdaLWN3FlbGVtZW50SWRlbnRpZmllcm9pc3N1aW5nX2NvdW50cnlsZWxlbWVudFZhbHVlYkFU2BhYW6RoZGlnZXN0SUQGZnJhbmRvbVD9g0Ky4OKbbULWZHSHaKwRcWVsZW1lbnRJZGVudGlmaWVycWlzc3VpbmdfYXV0aG9yaXR5bGVsZW1lbnRWYWx1ZWZBVCBETVbYGFhcpGhkaWdlc3RJRAdmcmFuZG9tUA_vix4PGecnkA8a7ItC7stxZWxlbWVudElkZW50aWZpZXJvZG9jdW1lbnRfbnVtYmVybGVsZW1lbnRWYWx1ZWkxMjM0NTY3ODnYGFkEYKRoZGlnZXN0SUQIZnJhbmRvbVCu1IwIzCp2SlOJg1N9lqjjcWVsZW1lbnRJZGVudGlmaWVyaHBvcnRyYWl0bGVsZW1lbnRWYWx1ZVkEEv_Y_-AAEEpGSUYAAQEBAJAAkAAA_9sAQwATDQ4RDgwTEQ8RFRQTFx0wHx0aGh06KiwjMEU9SUdEPUNBTFZtXUxRaFJBQ1-CYGhxdXt8e0pchpCFd49teHt2_9sAQwEUFRUdGR04Hx84dk9DT3Z2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2_8AAEQgAGABkAwEiAAIRAQMRAf_EABsAAAMBAAMBAAAAAAAAAAAAAAAFBgQBAgMH_8QAMhAAAQMDAwIFAgMJAAAAAAAAAQIDBAAFEQYSIRMxFBVRYXEiQQeBoRY1QlJzkbLB8f_EABUBAQEAAAAAAAAAAAAAAAAAAAAB_8QAGhEBAQEAAwEAAAAAAAAAAAAAAAFBESExYf_aAAwDAQACEQMRAD8ApbveItojKcfWkrx9DQP1LPsP9156fvPncJcjodDa4Ubd-7PAOc4HrSvUen4y27jdHVLW70soT2SkgAZ9-1H4f_uV_wDrn_FNIV3mavCJzkS3296ctokKKDjt3xgHj3rdYtQR7025sQpl1rlaFHOB6g1JW5Mydf5runXBFBBKi6QQQT6YPf8AT1r100tLTN5jLQvx_RWSvdkcZBHzk0wvpq9rVJddEG3PSmWuVuhWAB68A8fOKd2e7x7xD8RH3J2natKu6TU3obp-T3Ddjv8AV8bf-14aGYVKg3WOVFKHUJRuxnBIUM0De86wi219LMdtMxf8e1zAT7ZwcmnFxuDFtgrlSSQhI7Dkk_YCoHVVnjWZcNmNuJUlRWtRyVHIr6E-qOmOPFloNng9XGP1phqZ_bcpCHXbS-iKs4S7u7_HGD_eqmM-iVHbfaOW3EhST7GovWEaWqKiQy8wq0IKem0yQMcYzwMfqe9MmtVQIFqgKVHfSh1shCGwFbdp24ySKYN-or6LGw050OupxRSE79uMDv2NJY2vPESWmfLtvUWE56-cZOP5a5vrKNSsQJMeZHjI52okqCVElQHYZ9Kx3N666ZucdSrk7Nbd5KF5wcHkYJOPkUnvZV5RXAORmiics9yiePt78Xf0-qgp3Yzj8qyafs3kkJcfr9fc4V7tm3HAGMZPpRRRSyTo5PjFybdcHoJXncEjPf7Agjj2rdYtORrMHFJWp55wbVLUMcegFFFAuf0UnrOmDcXojLvC2gkkEenccfOad2a0R7NE6EfcrJ3LWruo0UUGHUOm_PHmXPFdDpAjHT3Zz-YplcbcxcoSoskEoV9x3SfUUUU-CdGiFFCWHLs-qKlWQzt4HxzjPf7VTw4rUKK3HYTtbbGAKKKBdfdPxr2hBcUpp5HCXE88ehH3FYYmj0pmtybjPenqbxtSsYHHYHJOR7UUUgpaKKKD_9nYGFjupGhkaWdlc3RJRAlmcmFuZG9tUA9pPjTau2jTwZl7J225julxZWxlbWVudElkZW50aWZpZXJyZHJpdmluZ19wcml2aWxlZ2VzbGVsZW1lbnRWYWx1ZYKjdXZlaGljbGVfY2F0ZWdvcnlfY29kZWFBamlzc3VlX2RhdGXZA-xqMjAxOC0wOC0wOWtleHBpcnlfZGF0ZdkD7GoyMDI0LTEwLTIwo3V2ZWhpY2xlX2NhdGVnb3J5X2NvZGVhQmppc3N1ZV9kYXRl2QPsajIwMTctMDItMjNrZXhwaXJ5X2RhdGXZA-xqMjAyNC0xMC0yMNgYWFykaGRpZ2VzdElECmZyYW5kb21Qpq1JLa96_7Bswf38gTD7n3FlbGVtZW50SWRlbnRpZmllcnZ1bl9kaXN0aW5ndWlzaGluZ19zaWdubGVsZW1lbnRWYWx1ZWJBVGppc3N1ZXJBdXRohEOhASahGCFZAgwwggIIMIIBraADAgECAhQOjySvX3i3qy223oovdhycy1xphjAKBggqhkjOPQQDAjAkMQswCQYDVQQGEwJVUzEVMBMGA1UEAwwMRXhhbXBsZSBJQUNBMB4XDTI1MDUyOTA3MTgzOVoXDTI2MDgyOTA3MTgzOVowIjELMAkGA1UEBhMCVVMxEzARBgNVBAMMCkV4YW1wbGUgRFMwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQZYql0veM-g8nnAqBID6PFLaOCn5lQMTqwhBt4Hzq8XRd8QMB_ev8VXIle5hULJVP3kUID_uaUmYjX_G4zKBn6o4G-MIG7MB8GA1UdIwQYMBaAFIwjEbH6JU3iuQ7OjKjlo-TcdNQUMB0GA1UdDgQWBBTtLj0tJ6D97Sid9PjgVVsv-tIqezAOBgNVHQ8BAf8EBAMCB4AwIwYDVR0SBBwwGoYYaHR0cHM6Ly9pYWNhLmV4YW1wbGUuY29tMBUGA1UdJQEB_wQLMAkGByiBjF0FAQIwLQYDVR0fBCYwJDAioCCgHoYcaHR0cHM6Ly9pYWNhLmV4YW1wbGUuY29tL2NybDAKBggqhkjOPQQDAgNJADBGAiEAy5KrvkE8FdcFYa0gx3nt-FGqkp_djOXeV0N2hCwrbEQCIQD92ZYswuIk9XdTOZBLkxQe2frXXPRWUv-TdgB1pUnnV1kC29gYWQLWpmd2ZXJzaW9uYzEuMG9kaWdlc3RBbGdvcml0aG1nU0hBLTI1Nmx2YWx1ZURpZ2VzdHOhcW9yZy5pc28uMTgwMTMuNS4xqwBYIHNVC6DPAq0mroo2WRpzXtDfN5vW3QXIQTvR3SjhfeDqAVggdKUFkM0VCZ7SkY3bLaw3G2cGNdD1rPGoK8FCXl2b8DoCWCCdz6pzn6BUMsu5yxbrG32P7OORp2rvdtRV6IBYSqOOnANYIInPiBX5NuwhKgTDkp17k8zvYzKA08AdhWOebvTLh0IfBFgg05q7rsPiq3k6HV9Wc6Q6sUQ0-_nE9_mbeLvwp0rzzKUFWCDlyPC7lWH2fEVZmsViJHVjq-Q_LQA2rOmetCUZCcxLOQZYIMEfa5Lgyu19AlNcLzt-tXNqZ6UAjvtv-kIusfl0mCIuB1ggImkauCi-JHHO7JAYoA-_tkd0k1qImBg6fTXmo5Zmt8sIWCC_Su75k89yMCqb6AeoMJXSmJbPX5NpPeidOkFcvNLYAAlYIDhCQ42G0AwL6-DKrqQGt0UmRmUoRQlJi3sr-BH5egPUClggnnddhrZlVyEgwqXPJx1SYYj-4c43FFUK5h4Uh3C4aPltZGV2aWNlS2V5SW5mb6FpZGV2aWNlS2V5pAECIAEhWCCmvCVFxH5inATuotKI8ILoMZLu_F6DTkaGT2ReoU2XfCJYIHEghj2QuqD8U5dUdj_XAicEWxqq1AbAjjxgXTpBQCAbZ2RvY1R5cGV1b3JnLmlzby4xODAxMy41LjEubURMbHZhbGlkaXR5SW5mb6Nmc2lnbmVkwHgeMjAyNS0wOS0yNlQwOTo1MzowNi4wMjE4NDg2MDFaaXZhbGlkRnJvbcB4HjIwMjUtMDktMjZUMDk6NTM6MDYuMDIxODQ5MDY3Wmp2YWxpZFVudGlswHgeMjAyNi0wOS0yNlQwOTo1MzowNi4wMjE4NTEyMTdaWEDA9_-9cApvU_wIlTnPXFzfy_ybJTR0anluG0AFaSucXqkbMAFtsBkxe1MRvDQ2sAnX6vIE8UWVrc1ubcow0VhC"

        val expectedMdlNamespaceData = Json.decodeFromString<JsonObject>(
            """
            {
      "family_name": "Doe",
      "given_name": "John",
      "birth_date": "1986-03-22",
      "issue_date": "2019-10-20",
      "expiry_date": "2024-10-20",
      "issuing_country": "AT",
      "issuing_authority": "AT DMV",
      "document_number": "123456789",
      "portrait": "/9j/4AAQSkZJRgABAQEAkACQAAD/2wBDABMNDhEODBMRDxEVFBMXHTAfHRoaHToqLCMwRT1JR0Q9Q0FMVm1dTFFoUkFDX4JgaHF1e3x7SlyGkIV3j214e3b/2wBDARQVFR0ZHTgfHzh2T0NPdnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnZ2dnb/wAARCAAYAGQDASIAAhEBAxEB/8QAGwAAAwEAAwEAAAAAAAAAAAAAAAUGBAECAwf/xAAyEAABAwMDAgUCAwkAAAAAAAABAgMEAAURBhIhEzEUFVFhcSJBB4GhFjVCUnORssHx/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAH/xAAaEQEBAQADAQAAAAAAAAAAAAAAAUERITFh/9oADAMBAAIRAxEAPwClu94i2iMpx9aSvH0NA/Us+w/3Xnp+8+dwlyOh0NrhRt37s8A5zgetK9R6fjLbuN0dUtbvSyhPZKSABn37Ufh/+5X/AOuf8U0hXeZq8InORLfb3py2iQooOO3fGAePet1i1BHvTbmxCmXWuVoUc4HqDUlbkzJ1/mu6dcEUEEqLpBBBPpg9/wBPWvXTS0tM3mMtC/H9FZK92RxkEfOTTC+mr2tUl10Qbc9KZa5W6FYAHrwDx84p3Z7vHvEPxEfcnadq0q7pNTehun5PcN2O/wBXxt/7XhoZhUqDdY5UUodQlG7GcEhQzQN7zrCLbX0sx20zF/x7XMBPtnByacXG4MW2CuVJJCEjsOST9gKgdVWeNZlw2Y24lSVFa1HJUcivoT6o6Y48WWg2eD1cY/WmGpn9tykIddtL6IqzhLu7v8cYP96qYz6JUdt9o5bcSFJPsai9YRpaoqJDLzCrQgp6bTJAxxjPAx+p70ya1VAgWqApUd9KHWyEIbAVt2nbjJIpg36ivosbDTnQ66nFFITv24wO/Y0lja88RJaZ8u29RYTnr5xk4/lrm+so1KxAkx5keMjnaiSoJUSVAdhn0rHc3rrpm5x1KuTs1t3koXnBweRgk4+RSe9lXlFcA5GaKJyz3KJ4+3vxd/T6qCndjOPyrJp+zeSQlx+v19zhXu2bccAYxk+lFFFLJOjk+MXJt1wegledwSM9/sCCOPat1i05GswcUlannnBtUtQxx6AUUUC5/RSes6YNxeiMu8LaCSQR6dxx85p3ZrRHs0ToR9ysnctau6jRRQYdQ6b88eZc8V0OkCMdPdnP5imVxtzFyhKiyQShX3HdJ9RRRT4J0aIUUJYcuz6oqVZDO3gfHOM9/tVPDitQorcdhO1tsYAoooF190/GvaEFxSmnkcJcTzx6EfcVhiaPSma3JuM96epvG1Kxgcdgck5HtRRSClooooP/2Q==",
      "driving_privileges": [
        {
          "vehicle_category_code": "A",
          "issue_date": "2018-08-09",
          "expiry_date": "2024-10-20"
        },
        {
          "vehicle_category_code": "B",
          "issue_date": "2017-02-23",
          "expiry_date": "2024-10-20"
        }
      ],
      "un_distinguishing_sign": "AT"
    }
        """.trimIndent()
        )

        val asitIssuerSigned =
            coseCompliantSerializer.decodeFromByteArray<IssuerSigned>(Base64.UrlSafe.decode(waltIdIssuerSignedBase64Url))

        val asitNamespaces = assertNotNull(asitIssuerSigned.namespaces)

        val asitMdlNamespace = assertNotNull(asitNamespaces[MDL_NAMESPACE_ID])

        val asitMdlNamespaceElementMap = asitMdlNamespace.entries.map { encodedItem ->
            encodedItem
        }.associateBy {
            it.value.elementIdentifier
        }

        assertEquals(
            expected = expectedMdlNamespaceData.keys,
            actual = asitMdlNamespaceElementMap.keys,
        )

        assertEquals(
            expected = expectedMdlNamespaceData["family_name"]!!.jsonPrimitive.content,
            actual = asitMdlNamespaceElementMap["family_name"]!!.value.elementValue as String,
        )

        assertEquals(
            expected = expectedMdlNamespaceData["given_name"]!!.jsonPrimitive.content,
            actual = asitMdlNamespaceElementMap["given_name"]!!.value.elementValue as String,
        )

        assertEquals(
            expected = LocalDate.parse(expectedMdlNamespaceData["birth_date"]!!.jsonPrimitive.content),
            actual = asitMdlNamespaceElementMap["birth_date"]!!.value.elementValue as LocalDate,
        )

        assertEquals(
            expected = LocalDate.parse(expectedMdlNamespaceData["issue_date"]!!.jsonPrimitive.content),
            actual = asitMdlNamespaceElementMap["issue_date"]!!.value.elementValue as LocalDate,
        )

        assertEquals(
            expected = LocalDate.parse(expectedMdlNamespaceData["expiry_date"]!!.jsonPrimitive.content),
            actual = asitMdlNamespaceElementMap["expiry_date"]!!.value.elementValue as LocalDate,
        )

        assertEquals(
            expected = expectedMdlNamespaceData["issuing_country"]!!.jsonPrimitive.content,
            actual = asitMdlNamespaceElementMap["issuing_country"]!!.value.elementValue as String,
        )

        assertEquals(
            expected = expectedMdlNamespaceData["issuing_authority"]!!.jsonPrimitive.content,
            actual = asitMdlNamespaceElementMap["issuing_authority"]!!.value.elementValue as String,
        )

        assertEquals(
            expected = expectedMdlNamespaceData["document_number"]!!.jsonPrimitive.content,
            actual = asitMdlNamespaceElementMap["document_number"]!!.value.elementValue as String,
        )

        assertEquals(
            expected = expectedMdlNamespaceData["un_distinguishing_sign"]!!.jsonPrimitive.content,
            actual = asitMdlNamespaceElementMap["un_distinguishing_sign"]!!.value.elementValue as String,
        )

        assertEquals(
            expected = expectedMdlNamespaceData["portrait"]!!.jsonPrimitive.content,
            actual = Base64.encode(asitMdlNamespaceElementMap["portrait"]!!.value.elementValue as ByteArray),
        )


    }

    companion object {

        private const val MDL_NAMESPACE_ID = "org.iso.18013.5.1"

        @JvmStatic
        @BeforeAll
        fun initializeVck() {
            at.asitplus.wallet.mdl.Initializer.initWithVCK()
        }

    }
}
