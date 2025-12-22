@file:OptIn(ExperimentalSerializationApi::class)

import id.walt.cose.CoseSign1
import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.credsdata.DrivingPrivilege
import id.walt.mdoc.credsdata.Mdl
import id.walt.mdoc.objects.deviceretrieval.DeviceRequest
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mdoc.objects.deviceretrieval.ItemsRequestList
import id.walt.mdoc.objects.digest.ValueDigestList
import id.walt.mdoc.objects.elements.IssuerSignedItem
import id.walt.mdoc.objects.elements.IssuerSignedItemSerializer
import id.walt.mdoc.objects.elements.IssuerSignedList
import id.walt.mdoc.objects.mso.MobileSecurityObject
import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class MdlCborTest {

    @Test
    fun mDL() {
        val mdl = Mdl(
            familyName = "Mustermann",
            givenName = "Max",
            birthDate = LocalDate.parse("1970-01-01"),
            issueDate = LocalDate.parse("2018-08-09"),
            expiryDate = LocalDate.parse("2024-10-20"),
            issuingCountry = "AT",
            issuingAuthority = "LPD Steiermark",
            documentNumber = "A/3f984/019",
            portrait = Random.nextBytes(16),
            drivingPrivileges = listOf(
                DrivingPrivilege(
                    vehicleCategoryCode = "A",
                    issueDate = LocalDate.parse("2018-08-09"),
                    expiryDate = LocalDate.parse("2024-10-20")
                )
            ),
            unDistinguishingSign = "AT",
        )

        val serialized = coseCompliantCbor.encodeToByteArray(mdl).toHexString().uppercase()

        assertContains(serialized, "76656869636C655F63617465676F72795F636F6465")// "vehicle_category_code"
        assertContains(serialized, "69737375655F64617465") // "issue_date"
        assertContains(serialized, "323031382D30382D3039") // "2018-08-09"
        assertContains(serialized, "6578706972795F64617465") // "expiry_date"
        assertContains(serialized, "323032342D31302D3230")  // "2024-10-20"
    }

    @Test
    // From ISO/IEC 18013-5:2021(E), D4.1.1, page 115
    fun mdocRequest() {
        /**
         * a2                                      # map(2)
         *    67                                   # text(7)
         *       76657273696f6e                    # "version"
         *    63                                   # text(3)
         *       312e30                            # "1.0"
         *    6b                                   # text(11)
         *       646f635265717565737473            # "docRequests"
         *    81                                   # array(1)
         *       a2                                # map(2)
         *          6c                             # text(12)
         *             6974656d7352657175657374    # "itemsRequest"
         *          d8 18                          # tag(24)
         *             58 93                       # bytes(147)
         *                a267646f6354797065756f72672e69736f2e31383031332e352e312e6d444c6a6e616d65537061636573a1716f72672e69736f2e31383031332e352e31a66b66616d696c795f6e616d65f56f646f63756d656e745f6e756d626572f57264726976696e675f70726976696c65676573f56a69737375655f64617465f56b6578706972795f64617465f568706f727472616974f4
         *          6a                             # text(10)
         *             72656164657241757468        # "readerAuth"
         *          84                             # array(4)
         *             43                          # bytes(3)
         *                a10126                   # "\xA1\u0001&"
         *             a1                          # map(1)
         *                18 21                    # unsigned(33)
         *                59 01b7                  # bytes(439)
         *                   308201b330820158a00302010202147552715f6add323d4934a1ba175dc945755d8b50300a06082a8648ce3d04030230163114301206035504030c0b72656164657220726f6f74301e170d3230313030313030303030305a170d3233313233313030303030305a3011310f300d06035504030c067265616465723059301306072a8648ce3d020106082a8648ce3d03010703420004f8912ee0f912b6be683ba2fa0121b2630e601b2b628dff3b44f6394eaa9abdbcc2149d29d6ff1a3e091135177e5c3d9c57f3bf839761eed02c64dd82ae1d3bbfa38188308185301c0603551d1f041530133011a00fa00d820b6578616d706c652e636f6d301d0603551d0e04160414f2dfc4acafc5f30b464fada20bfcd533af5e07f5301f0603551d23041830168014cfb7a881baea5f32b6fb91cc29590c50dfac416e300e0603551d0f0101ff04040302078030150603551d250101ff040b3009060728818c5d050106300a06082a8648ce3d0403020349003046022100fb9ea3b686fd7ea2f0234858ff8328b4efef6a1ef71ec4aae4e307206f9214930221009b94f0d739dfa84cca29efed529dd4838acfd8b6bee212dc6320c46feb839a35
         *             f6                          # primitive(22)
         *             58 40                       # bytes(64)
         *                1f3400069063c189138bdcd2f631427c589424113fc9ec26cebcacacfcdb9695d28e99953becabc4e30ab4efacc839a81f9159933d192527ee91b449bb7f80bf
         */
        val input = """
            a26776657273696f6e63312e306b646f63526571756573747381a26c6974656d7352657175657374d8185893
            a267646f6354797065756f72672e69736f2e31383031332e352e312e6d444c6a6e616d65537061636573a171
            6f72672e69736f2e31383031332e352e31a66b66616d696c795f6e616d65f56f646f63756d656e745f6e756d
            626572f57264726976696e675f70726976696c65676573f56a69737375655f64617465f56b6578706972795f
            64617465f568706f727472616974f46a726561646572417574688443a10126a118215901b7308201b3308201
            58a00302010202147552715f6add323d4934a1ba175dc945755d8b50300a06082a8648ce3d0403023016311430
            1206035504030c0b72656164657220726f6f74301e170d3230313030313030303030305a170d3233313233313
            030303030305a3011310f300d06035504030c067265616465723059301306072a8648ce3d020106082a8648ce
            3d03010703420004f8912ee0f912b6be683ba2fa0121b2630e601b2b628dff3b44f6394eaa9abdbcc2149d29d6f
            f1a3e091135177e5c3d9c57f3bf839761eed02c64dd82ae1d3bbfa38188308185301c0603551d1f04153013301
            1a00fa00d820b6578616d706c652e636f6d301d0603551d0e04160414f2dfc4acafc5f30b464fada20bfcd533a
            f5e07f5301f0603551d23041830168014cfb7a881baea5f32b6fb91cc29590c50dfac416e300e0603551d0f010
            1ff04040302078030150603551d250101ff040b3009060728818c5d050106300a06082a8648ce3d040302034900
            3046022100fb9ea3b686fd7ea2f0234858ff8328b4efef6a1ef71ec4aae4e307206f9214930221009b94f0d739
            dfa84cca29efed529dd4838acfd8b6bee212dc6320c46feb839a35f658401f3400069063c189138bdcd2f6314
            27c589424113fc9ec26cebcacacfcdb9695d28e99953becabc4e30ab4efacc839a81f9159933d192527ee91b44
            9bb7f80bf
        """.trimIndent().replace("\n", "").uppercase()

        val deviceRequest = coseCompliantCbor.decodeFromByteArray(
            DeviceRequest.serializer(),
            input.hexToByteArray()
        )
        assertNotNull(deviceRequest)

        assertEquals(deviceRequest.version, "1.0")
        val docRequest = deviceRequest.docRequests.first()
        assertNotNull(docRequest)

        assertEquals(docRequest.itemsRequest.value.docType, "org.iso.18013.5.1.mDL")
        val itemsRequestList = docRequest.itemsRequest.value.namespaces["org.iso.18013.5.1"]
        assertNotNull(itemsRequestList)
        assertEquals(itemsRequestList.findItem("family_name"), true)
        assertEquals(itemsRequestList.findItem("document_number"), true)
        assertEquals(itemsRequestList.findItem("driving_privileges"), true)
        assertEquals(itemsRequestList.findItem("expiry_date"), true)
        assertEquals(itemsRequestList.findItem("portrait"), false)

        assertNotNull(docRequest.readerAuth)
        assertNotNull(docRequest.readerAuth.unprotected.x5chain)

        assertEquals(
            coseCompliantCbor.encodeToByteArray(DeviceRequest.serializer(), deviceRequest)
                .toHexString().uppercase(), input
        )
    }

    @Test
    // From ISO/IEC 18013-5:2021(E), D4.1.2, page 116
    fun mdocResponse() {
        /**
         * a3                                      # map(3)
         *    67                                   # text(7)
         *       76657273696f6e                    # "version"
         *    63                                   # text(3)
         *       312e30                            # "1.0"
         *    69                                   # text(9)
         *       646f63756d656e7473                # "documents"
         *    81                                   # array(1)
         *       a3                                # map(3)
         *          67                             # text(7)
         *             646f6354797065              # "docType"
         *          75                             # text(21)
         *             6f72672e69736f2e31383031332e352e312e6d444c # "org.iso.18013.5.1.mDL"
         *          6c                             # text(12)
         *             6973737565725369676e6564    # "issuerSigned"
         *          a2                             # map(2)
         *             6a                          # text(10)
         *                6e616d65537061636573     # "nameSpaces"
         *             a1                          # map(1)
         *                71                       # text(17)
         *                   6f72672e69736f2e31383031332e352e31 # "org.iso.18013.5.1"
         *                86                       # array(6)
         *                   d8 18                 # tag(24)
         *                      58 63              # bytes(99)
         *                         a4686469676573744944006672616e646f6d58208798645b20ea200e19ffabac92624bee6aec63aceedecfb1b80077d22bfc20e971656c656d656e744964656e7469666965726b66616d696c795f6e616d656c656c656d656e7456616c756563446f65
         *                   d8 18                 # tag(24)
         *                      58 6c              # bytes(108)
         *                         a4686469676573744944036672616e646f6d5820b23f627e8999c706df0c0a4ed98ad74af988af619b4bb078b89058553f44615d71656c656d656e744964656e7469666965726a69737375655f646174656c656c656d656e7456616c7565d903ec6a323031392d31302d3230
         *                   d8 18                 # tag(24)
         *                      58 6d              # bytes(109)
         *                         a4686469676573744944046672616e646f6d5820c7ffa307e5de921e67ba5878094787e8807ac8e7b5b3932d2ce80f00f3e9abaf71656c656d656e744964656e7469666965726b6578706972795f646174656c656c656d656e7456616c7565d903ec6a323032342d31302d3230
         *                   d8 18                 # tag(24)
         *                      58 6d              # bytes(109)
         *                         a4686469676573744944076672616e646f6d582026052a42e5880557a806c1459af3fb7eb505d3781566329d0b604b845b5f9e6871656c656d656e744964656e7469666965726f646f63756d656e745f6e756d6265726c656c656d656e7456616c756569313233343536373839
         *                   d8 18                 # tag(24)
         *                      59 0471            # bytes(1137)
         *                         a4686469676573744944086672616e646f6d5820d094dad764a2eb9deb5210e9d899643efbd1d069cc311d3295516ca0b024412d71656c656d656e744964656e74696669657268706f7274726169746c656c656d656e7456616c7565590412ffd8ffe000104a46494600010101009000900000ffdb004300130d0e110e0c13110f11151413171d301f1d1a1a1d3a2a2c2330453d4947443d43414c566d5d4c51685241435f82606871757b7c7b4a5c869085778f6d787b76ffdb0043011415151d191d381f1f38764f434f7676767676767676767676767676767676767676767676767676767676767676767676767676767676767676767676767676ffc00011080018006403012200021101031101ffc4001b00000301000301000000000000000000000005060401020307ffc400321000010303030205020309000000000000010203040005110612211331141551617122410781a1163542527391b2c1f1ffc4001501010100000000000000000000000000000001ffc4001a110101010003010000000000000000000000014111213161ffda000c03010002110311003f00a5bbde22da2329c7d692bc7d0d03f52cfb0ff75e7a7ef3e7709723a1d0dae146ddfbb3c039ce07ad2bd47a7e32dbb8dd1d52d6ef4b284f64a480067dfb51f87ffb95ff00eb9ff14d215de66af089ce44b7dbde9cb6890a2838eddf18078f7add62d411ef4db9b10a65d6b95a147381ea0d495b933275fe6bba75c114104a8ba410413e983dff004f5af5d34b4b4cde632d0bf1fd1592bdd91c6411f3934c2fa6af6b54975d106dcf4a65ae56e856001ebc03c7ce29dd9eef1ef10fc447dc9da76ad2aee93537a1ba7e4f70dd8eff0057c6dffb5e1a19854a83758e54528750946ec6704850cd037bceb08b6d7d2cc76d3317fc7b5cc04fb6707269c5c6e0c5b60ae549242123b0e493f602a075559e359970d98db89525456b51c951c8afa13ea8e98e3c596836783d5c63f5a61a99fdb7290875db4be88ab384bbbbbfc7183fdeaa633e8951db7da396dc48524fb1a8bd611a5aa2a2432f30ab420a7a6d3240c718cf031fa9ef4c9ad550205aa02951df4a1d6c8421b015b769db8c9229837ea2be8b1b0d39d0eba9c51484efdb8c0efd8d258daf3c449699f2edbd4584e7af9c64e3f96b9beb28d4ac40931e6478c8e76a24a825449501d867d2b1dcdebae99b9c752ae4ecd6dde4a179c1c1e460938f9149ef655e515c03919a289cb3dca278fb7bf177f4faa829dd8ce3f2ac9a7ecde490971fafd7dce15eed9b71c018c64fa514514b24e8e4f8c5c9b75c1e82579dc1233dfec08238f6add62d391acc1c5256a79e706d52d431c7a0145140b9fd149eb3a60dc5e88cbbc2da092411e9dc71f39a7766b447b344e847dcac9dcb5abba8d145061d43a6fcf1e65cf15d0e90231d3dd9cfe62995c6dcc5ca12a2c904a15f71dd27d451453e09d1a21450961cbb3ea8a956433b781f1ce33dfed54f0e2b50a2b71d84ed6db18028a28175f74fc6bda105c529a791c25c4f3c7a11f71586268f4a66b726e33de9ea6f1b52b181c760724e47b514520a5a28a283ffd9
         *                   d8 18                 # tag(24)
         *                      58 ff              # bytes(255)
         *                         a4686469676573744944096672616e646f6d58204599f81beaa2b20bd0ffcc9aa03a6f985befab3f6beaffa41e6354cdb2ab2ce471656c656d656e744964656e7469666965727264726976696e675f70726976696c656765736c656c656d656e7456616c756582a37576656869636c655f63617465676f72795f636f646561416a69737375655f64617465d903ec6a323031382d30382d30396b6578706972795f64617465d903ec6a323032342d31302d3230a37576656869636c655f63617465676f72795f636f646561426a69737375655f64617465d903ec6a323031372d30322d32336b6578706972795f64617465d903ec6a323032342d31302d3230
         *             6a                          # text(10)
         *                69737375657241757468     # "issuerAuth"
         *             84                          # array(4)
         *                43                       # bytes(3)
         *                   a10126                # "\xA1\u0001&"
         *                a1                       # map(1)
         *                   18 21                 # unsigned(33)
         *                   59 01f3               # bytes(499)
         *                      308201ef30820195a00302010202143c4416eed784f3b413e48f56f075abfa6d87eb84300a06082a8648ce3d04030230233114301206035504030c0b75746f7069612069616361310b3009060355040613025553301e170d3230313030313030303030305a170d3231313030313030303030305a30213112301006035504030c0975746f706961206473310b30090603550406130255533059301306072a8648ce3d020106082a8648ce3d03010703420004ace7ab7340e5d9648c5a72a9a6f56745c7aad436a03a43efea77b5fa7b88f0197d57d8983e1b37d3a539f4d588365e38cbbf5b94d68c547b5bc8731dcd2f146ba381a83081a5301e0603551d120417301581136578616d706c65406578616d706c652e636f6d301c0603551d1f041530133011a00fa00d820b6578616d706c652e636f6d301d0603551d0e0416041414e29017a6c35621ffc7a686b7b72db06cd12351301f0603551d2304183016801454fa2383a04c28e0d930792261c80c4881d2c00b300e0603551d0f0101ff04040302078030150603551d250101ff040b3009060728818c5d050102300a06082a8648ce3d040302034800304502210097717ab9016740c8d7bcdaa494a62c053bbdecce1383c1aca72ad08dbc04cbb202203bad859c13a63c6d1ad67d814d43e2425caf90d422422c04a8ee0304c0d3a68d
         *                59 03a2                  # bytes(930)
         *                   d81859039da66776657273696f6e63312e306f646967657374416c676f726974686d675348412d3235366c76616c756544696765737473a2716f72672e69736f2e31383031332e352e31ad00582075167333b47b6c2bfb86eccc1f438cf57af055371ac55e1e359e20f254adcebf01582067e539d6139ebd131aef441b445645dd831b2b375b390ca5ef6279b205ed45710258203394372ddb78053f36d5d869780e61eda313d44a392092ad8e0527a2fbfe55ae0358202e35ad3c4e514bb67b1a9db51ce74e4cb9b7146e41ac52dac9ce86b8613db555045820ea5c3304bb7c4a8dcb51c4c13b65264f845541341342093cca786e058fac2d59055820fae487f68b7a0e87a749774e56e9e1dc3a8ec7b77e490d21f0e1d3475661aa1d0658207d83e507ae77db815de4d803b88555d0511d894c897439f5774056416a1c7533075820f0549a145f1cf75cbeeffa881d4857dd438d627cf32174b1731c4c38e12ca936085820b68c8afcb2aaf7c581411d2877def155be2eb121a42bc9ba5b7312377e068f660958200b3587d1dd0c2a07a35bfb120d99a0abfb5df56865bb7fa15cc8b56a66df6e0c0a5820c98a170cf36e11abb724e98a75a5343dfa2b6ed3df2ecfbb8ef2ee55dd41c8810b5820b57dd036782f7b14c6a30faaaae6ccd5054ce88bdfa51a016ba75eda1edea9480c5820651f8736b18480fe252a03224ea087b5d10ca5485146c67c74ac4ec3112d4c3a746f72672e69736f2e31383031332e352e312e5553a4005820d80b83d25173c484c5640610ff1a31c949c1d934bf4cf7f18d5223b15dd4f21c0158204d80e1e2e4fb246d97895427ce7000bb59bb24c8cd003ecf94bf35bbd2917e340258208b331f3b685bca372e85351a25c9484ab7afcdf0d2233105511f778d98c2f544035820c343af1bd1690715439161aba73702c474abf992b20c9fb55c36a336ebe01a876d6465766963654b6579496e666fa1696465766963654b6579a40102200121582096313d6c63e24e3372742bfdb1a33ba2c897dcd68ab8c753e4fbd48dca6b7f9a2258201fb3269edd418857de1b39a4e4a44b92fa484caa722c228288f01d0c03a2c3d667646f6354797065756f72672e69736f2e31383031332e352e312e6d444c6c76616c6964697479496e666fa3667369676e6564c074323032302d31302d30315431333a33303a30325a6976616c696446726f6dc074323032302d31302d30315431333a33303a30325a6a76616c6964556e74696cc074323032312d31302d30315431333a33303a30325a
         *                58 40                    # bytes(64)
         *                   59e64205df1e2f708dd6db0847aed79fc7c0201d80fa55badcaf2e1bcf5902e1e5a62e4832044b890ad85aa53f129134775d733754d7cb7a413766aeff13cb2e
         *          6c                             # text(12)
         *             6465766963655369676e6564    # "deviceSigned"
         *          a2                             # map(2)
         *             6a                          # text(10)
         *                6e616d65537061636573     # "nameSpaces"
         *             d8 18                       # tag(24)
         *                41                       # bytes(1)
         *                   a0                    # "\xA0"
         *             6a                          # text(10)
         *                64657669636541757468     # "deviceAuth"
         *             a1                          # map(1)
         *                69                       # text(9)
         *                   6465766963654d6163    # "deviceMac"
         *                84                       # array(4)
         *                   43                    # bytes(3)
         *                      a10105             # "\xA1\u0001\u0005"
         *                   a0                    # map(0)
         *                   f6                    # primitive(22)
         *                   58 20                 # bytes(32)
         *                      e99521a85ad7891b806a07f8b5388a332d92c189a7bf293ee1f543405ae6824d
         *    66                                   # text(6)
         *       737461747573                      # "status"
         *    00                                   # unsigned(0)
         *
         */
        val input = """
            a36776657273696f6e63312e3069646f63756d656e747381a367646f6354797065756f72672e69736f2e313
            83031332e352e312e6d444c6c6973737565725369676e6564a26a6e616d65537061636573a1716f72672e69
            736f2e31383031332e352e3186d8185863a4686469676573744944006672616e646f6d58208798645b20ea
            200e19ffabac92624bee6aec63aceedecfb1b80077d22bfc20e971656c656d656e744964656e74696669657
            26b66616d696c795f6e616d656c656c656d656e7456616c756563446f65d818586ca468646967657374494
            4036672616e646f6d5820b23f627e8999c706df0c0a4ed98ad74af988af619b4bb078b89058553f44615d7
            1656c656d656e744964656e7469666965726a69737375655f646174656c656c656d656e7456616c7565d90
            3ec6a323031392d31302d3230d818586da4686469676573744944046672616e646f6d5820c7ffa307e5de92
            1e67ba5878094787e8807ac8e7b5b3932d2ce80f00f3e9abaf71656c656d656e744964656e746966696572
            6b6578706972795f646174656c656c656d656e7456616c7565d903ec6a323032342d31302d3230d818586d
            a4686469676573744944076672616e646f6d582026052a42e5880557a806c1459af3fb7eb505d378156632
            9d0b604b845b5f9e6871656c656d656e744964656e7469666965726f646f63756d656e745f6e756d626572
            6c656c656d656e7456616c756569313233343536373839d818590471a4686469676573744944086672616e
            646f6d5820d094dad764a2eb9deb5210e9d899643efbd1d069cc311d3295516ca0b024412d71656c656d65
            6e744964656e74696669657268706f7274726169746c656c656d656e7456616c7565590412ffd8ffe000104a
            46494600010101009000900000ffdb004300130d0e110e0c13110f11151413171d301f1d1a1a1d3a2a2c233
            0453d4947443d43414c566d5d4c51685241435f82606871757b7c7b4a5c869085778f6d787b76ffdb004301
            1415151d191d381f1f38764f434f7676767676767676767676767676767676767676767676767676767676
            767676767676767676767676767676767676767676ffc00011080018006403012200021101031101ffc4001
            b00000301000301000000000000000000000005060401020307ffc400321000010303030205020309000000000
            000010203040005110612211331141551617122410781a1163542527391b2c1f1ffc40015010101000000000
            00000000000000000000001ffc4001a110101010003010000000000000000000000014111213161ffda000c03010
            002110311003f00a5bbde22da2329c7d692bc7d0d03f52cfb0ff75e7a7ef3e7709723a1d0dae146ddfbb3c039ce
            07ad2bd47a7e32dbb8dd1d52d6ef4b284f64a480067dfb51f87ffb95ff00eb9ff14d215de66af089ce44b7dbde9cb
            6890a2838eddf18078f7add62d411ef4db9b10a65d6b95a147381ea0d495b933275fe6bba75c114104a8ba4104
            13e983dff004f5af5d34b4b4cde632d0bf1fd1592bdd91c6411f3934c2fa6af6b54975d106dcf4a65ae56e85600
            1ebc03c7ce29dd9eef1ef10fc447dc9da76ad2aee93537a1ba7e4f70dd8eff0057c6dffb5e1a19854a83758e5452
            8750946ec6704850cd037bceb08b6d7d2cc76d3317fc7b5cc04fb6707269c5c6e0c5b60ae549242123b0e493f6
            02a075559e359970d98db89525456b51c951c8afa13ea8e98e3c596836783d5c63f5a61a99fdb7290875db4be8
            8ab384bbbbbfc7183fdeaa633e8951db7da396dc48524fb1a8bd611a5aa2a2432f30ab420a7a6d3240c718cf03
            1fa9ef4c9ad550205aa02951df4a1d6c8421b015b769db8c9229837ea2be8b1b0d39d0eba9c51484efdb8c0efd
            8d258daf3c449699f2edbd4584e7af9c64e3f96b9beb28d4ac40931e6478c8e76a24a825449501d867d2b1dcde
            bae99b9c752ae4ecd6dde4a179c1c1e460938f9149ef655e515c03919a289cb3dca278fb7bf177f4faa829dd8c
            e3f2ac9a7ecde490971fafd7dce15eed9b71c018c64fa514514b24e8e4f8c5c9b75c1e82579dc1233dfec08238
            f6add62d391acc1c5256a79e706d52d431c7a0145140b9fd149eb3a60dc5e88cbbc2da092411e9dc71f39a7766
            b447b344e847dcac9dcb5abba8d145061d43a6fcf1e65cf15d0e90231d3dd9cfe62995c6dcc5ca12a2c904a15f
            71dd27d451453e09d1a21450961cbb3ea8a956433b781f1ce33dfed54f0e2b50a2b71d84ed6db18028a28175f7
            4fc6bda105c529a791c25c4f3c7a11f71586268f4a66b726e33de9ea6f1b52b181c760724e47b514520a5a28a2
            83ffd9d81858ffa4686469676573744944096672616e646f6d58204599f81beaa2b20bd0ffcc9aa03a6f985befab3
            f6beaffa41e6354cdb2ab2ce471656c656d656e744964656e7469666965727264726976696e675f70726976696c
            656765736c656c656d656e7456616c756582a37576656869636c655f63617465676f72795f636f646561416a69
            737375655f64617465d903ec6a323031382d30382d30396b6578706972795f64617465d903ec6a323032342d31
            302d3230a37576656869636c655f63617465676f72795f636f646561426a69737375655f64617465d903ec6a32
            3031372d30322d32336b6578706972795f64617465d903ec6a323032342d31302d32306a697373756572417574
            688443a10126a118215901f3308201ef30820195a00302010202143c4416eed784f3b413e48f56f075abfa6d87
            eb84300a06082a8648ce3d04030230233114301206035504030c0b75746f7069612069616361310b3009060355
            040613025553301e170d3230313030313030303030305a170d3231313030313030303030305a30213112301006
            035504030c0975746f706961206473310b30090603550406130255533059301306072a8648ce3d020106082a86
            48ce3d03010703420004ace7ab7340e5d9648c5a72a9a6f56745c7aad436a03a43efea77b5fa7b88f0197d57d8
            983e1b37d3a539f4d588365e38cbbf5b94d68c547b5bc8731dcd2f146ba381a83081a5301e0603551d12041730
            1581136578616d706c65406578616d706c652e636f6d301c0603551d1f041530133011a00fa00d820b6578616d
            706c652e636f6d301d0603551d0e0416041414e29017a6c35621ffc7a686b7b72db06cd12351301f0603551d230
            4183016801454fa2383a04c28e0d930792261c80c4881d2c00b300e0603551d0f0101ff04040302078030150603
            551d250101ff040b3009060728818c5d050102300a06082a8648ce3d040302034800304502210097717ab901674
            0c8d7bcdaa494a62c053bbdecce1383c1aca72ad08dbc04cbb202203bad859c13a63c6d1ad67d814d43e2425ca
            f90d422422c04a8ee0304c0d3a68d5903a2d81859039da66776657273696f6e63312e306f646967657374416c6
            76f726974686d675348412d3235366c76616c756544696765737473a2716f72672e69736f2e31383031332e352
            e31ad00582075167333b47b6c2bfb86eccc1f438cf57af055371ac55e1e359e20f254adcebf01582067e539d61
            39ebd131aef441b445645dd831b2b375b390ca5ef6279b205ed45710258203394372ddb78053f36d5d869780e6
            1eda313d44a392092ad8e0527a2fbfe55ae0358202e35ad3c4e514bb67b1a9db51ce74e4cb9b7146e41ac52dac
            9ce86b8613db555045820ea5c3304bb7c4a8dcb51c4c13b65264f845541341342093cca786e058fac2d5905582
            0fae487f68b7a0e87a749774e56e9e1dc3a8ec7b77e490d21f0e1d3475661aa1d0658207d83e507ae77db815de
            4d803b88555d0511d894c897439f5774056416a1c7533075820f0549a145f1cf75cbeeffa881d4857dd438d627c
            f32174b1731c4c38e12ca936085820b68c8afcb2aaf7c581411d2877def155be2eb121a42bc9ba5b7312377e06
            8f660958200b3587d1dd0c2a07a35bfb120d99a0abfb5df56865bb7fa15cc8b56a66df6e0c0a5820c98a170cf3
            6e11abb724e98a75a5343dfa2b6ed3df2ecfbb8ef2ee55dd41c8810b5820b57dd036782f7b14c6a30faaaae6cc
            d5054ce88bdfa51a016ba75eda1edea9480c5820651f8736b18480fe252a03224ea087b5d10ca5485146c67c74
            ac4ec3112d4c3a746f72672e69736f2e31383031332e352e312e5553a4005820d80b83d25173c484c5640610ff1
            a31c949c1d934bf4cf7f18d5223b15dd4f21c0158204d80e1e2e4fb246d97895427ce7000bb59bb24c8cd003ec
            f94bf35bbd2917e340258208b331f3b685bca372e85351a25c9484ab7afcdf0d2233105511f778d98c2f544035
            820c343af1bd1690715439161aba73702c474abf992b20c9fb55c36a336ebe01a876d6465766963654b6579496
            e666fa1696465766963654b6579a40102200121582096313d6c63e24e3372742bfdb1a33ba2c897dcd68ab8c75
            3e4fbd48dca6b7f9a2258201fb3269edd418857de1b39a4e4a44b92fa484caa722c228288f01d0c03a2c3d6676
            46f6354797065756f72672e69736f2e31383031332e352e312e6d444c6c76616c6964697479496e666fa366736
            9676e6564c074323032302d31302d30315431333a33303a30325a6976616c696446726f6dc074323032302d313
            02d30315431333a33303a30325a6a76616c6964556e74696cc074323032312d31302d30315431333a33303a303
            25a584059e64205df1e2f708dd6db0847aed79fc7c0201d80fa55badcaf2e1bcf5902e1e5a62e4832044b890ad
            85aa53f129134775d733754d7cb7a413766aeff13cb2e6c6465766963655369676e6564a26a6e616d6553706163
            6573d81841a06a64657669636541757468a1696465766963654d61638443a10105a0f65820e99521a85ad7891b
            806a07f8b5388a332d92c189a7bf293ee1f543405ae6824d6673746174757300
        """.trimIndent().replace("\n", "").uppercase()
        println("Signed: $input")

        println("Decoding DeviceResponse from: ${input.hexToByteArray().toHexString()}")
        val deviceResponse = coseCompliantCbor.decodeFromByteArray<DeviceResponse>(input.hexToByteArray())
        println("Namespaces for document 1:")
        println(deviceResponse.documents!!.first().issuerSigned.namespacesToJson())

        assertEquals(deviceResponse.version, "1.0")
        val document = deviceResponse.documents[0]
        assertNotNull(document)
        assertEquals(document.docType, "org.iso.18013.5.1.mDL")
        val issuerSignedList = document.issuerSigned.namespaces?.get("org.iso.18013.5.1")
        assertNotNull(issuerSignedList)
        assertEquals(issuerSignedList.findItem(0U).elementIdentifier, "family_name")
        assertEquals(issuerSignedList.findItem(0U).elementValue, "Doe")
        assertEquals(issuerSignedList.findItem(3U).elementIdentifier, "issue_date")
        assertEquals(issuerSignedList.findItem(3U).elementValue, LocalDate.parse("2019-10-20"))
        assertEquals(issuerSignedList.findItem(4U).elementIdentifier, "expiry_date")
        assertEquals(issuerSignedList.findItem(4U).elementValue, LocalDate.parse("2024-10-20"))
        assertEquals(issuerSignedList.findItem(7U).elementIdentifier, "document_number")
        assertEquals(issuerSignedList.findItem(7U).elementValue, "123456789")
        assertEquals(issuerSignedList.findItem(8U).elementIdentifier, "portrait")

        assertNotNull(issuerSignedList.findItem(8U).elementValue)

        assertEquals(issuerSignedList.findItem(9U).elementIdentifier, "driving_privileges")
        val drivingPrivilege = issuerSignedList.findItem(9U).elementValue as List<DrivingPrivilege>
        assertNotNull(drivingPrivilege)
        assertContains(
            drivingPrivilege, DrivingPrivilege(
                vehicleCategoryCode = "A",
                issueDate = LocalDate.parse("2018-08-09"),
                expiryDate = LocalDate.parse("2024-10-20")
            )
        )
        assertContains(
            drivingPrivilege, DrivingPrivilege(
                vehicleCategoryCode = "B",
                issueDate = LocalDate.parse("2017-02-23"),
                expiryDate = LocalDate.parse("2024-10-20")
            )
        )
        val mso = document.issuerSigned.issuerAuth.decodeIsoPayload<MobileSecurityObject>()

        assertEquals(mso.version, "1.0")
        assertEquals(mso.digestAlgorithm, "SHA-256")
        assertEquals(mso.docType, "org.iso.18013.5.1.mDL")
        assertEquals(mso.validityInfo.signed, Instant.parse("2020-10-01T13:30:02Z"))
        assertEquals(mso.validityInfo.validFrom, Instant.parse("2020-10-01T13:30:02Z"))
        assertEquals(mso.validityInfo.validUntil, Instant.parse("2021-10-01T13:30:02Z"))
        val valueDigestList = mso.valueDigests["org.iso.18013.5.1"]
        assertNotNull(valueDigestList)
        assertEquals(valueDigestList.findItem(0U).toHexString().uppercase(), "75167333B47B6C2BFB86ECCC1F438CF57AF055371AC55E1E359E20F254ADCEBF")
        assertEquals(valueDigestList.findItem(1U).toHexString().uppercase(), "67E539D6139EBD131AEF441B445645DD831B2B375B390CA5EF6279B205ED4571")
        val valueDigestListUs = mso.valueDigests["${"org.iso.18013.5.1"}.US"]
        assertNotNull(valueDigestListUs)
        assertEquals(valueDigestListUs.findItem(0U).toHexString().uppercase(), "D80B83D25173C484C5640610FF1A31C949C1D934BF4CF7F18D5223B15DD4F21C")
        assertEquals(valueDigestListUs.findItem(1U).toHexString().uppercase(), "4D80E1E2E4FB246D97895427CE7000BB59BB24C8CD003ECF94BF35BBD2917E34")

        assertNotNull(document.deviceSigned?.deviceAuth?.deviceMac)

        assertEquals(coseCompliantCbor.encodeToByteArray(deviceResponse).toHexString().uppercase(), input)
    }

    @Test
    fun drivingPrivilege() {
        val drivingPrivilege = DrivingPrivilege(
            vehicleCategoryCode = "A",
            issueDate = LocalDate.parse("2018-08-09"),
            expiryDate = LocalDate.parse("2024-10-20")
        )

        val serialized = coseCompliantCbor.encodeToByteArray(drivingPrivilege).toHexString().uppercase()

        assertContains(serialized, "76656869636C655F63617465676F72795F636F6465") // "vehicle_category_code"
        assertContains(serialized, "69737375655F64617465") // "issue_date"
        assertContains(serialized, "D903EC") // ISO mDL defines tag(1004) for CBOR type 6 for full-dates
        assertContains(serialized, "323031382D30382D3039") // "2018-08-09"
        assertContains(serialized, "6578706972795F64617465") // "expiry_date"
        assertContains(serialized, "323032342D31302D3230")  // "2024-10-20"
    }

    @Test
    fun drivingPrivilegeDeserialization() {
        val input = "a37576656869636c655f63617465676f72795f636f646561416a69737375655f64617465d903ec6a323031382d30382d" +
                "30396b6578706972795f64617465d903ec6a323032342d31302d3230"

        val deserialized = coseCompliantCbor.decodeFromByteArray<DrivingPrivilege>(input.uppercase().hexToByteArray())
        println("Deserialized: $deserialized")

        assertEquals(deserialized.vehicleCategoryCode, "A")
        assertEquals(deserialized.issueDate, LocalDate.parse("2018-08-09"))
        assertEquals(deserialized.expiryDate, LocalDate.parse("2024-10-20"))
    }


    @Test
    fun dateInIssuerSignedItemFromISOExample() {
        val input = """
            a4686469676573744944036672616e646f6d5820b23f627e8999c706df0c0a4ed98ad74af988af619b4bb078b89058553f44615d7165
            6c656d656e744964656e7469666965726a69737375655f646174656c656c656d656e7456616c7565d903ec6a323031392d31302d3230
        """.trimIndent().replace("\n", "").uppercase()

        val inputDecoded = input.hexToByteArray()

        val deserialized = coseCompliantCbor.decodeFromByteArray(
            IssuerSignedItemSerializer(
                "org.iso.18013.5.1",
                "issue_date"
            ), inputDecoded
        )
        val serialized = deserialized.serialize("org.iso.18013.5.1")

        assertEquals(serialized.toHexString().uppercase(), input)
    }

    @Test
    fun drivingPrivilegeInIssuerSignedItemFromISOExample() {
        /**
         * A4                                      # map(4)
         *    68                                   # text(8)
         *       6469676573744944                  # "digestID"
         *    09                                   # unsigned(9)
         *    66                                   # text(6)
         *       72616E646F6D                      # "random"
         *    58 20                                # bytes(32)
         *       4599F81BEAA2B20BD0FFCC9AA03A6F985BEFAB3F6BEAFFA41E6354CDB2AB2CE4 # "E\x99\xF8\eꢲ\v\xD0\xFF̚\xA0:o\x98[\xEF\xAB?k\xEA\xFF\xA4\u001EcTͲ\xAB,\xE4"
         *    71                                   # text(17)
         *       656C656D656E744964656E746966696572 # "elementIdentifier"
         *    72                                   # text(18)
         *       64726976696E675F70726976696C65676573 # "driving_privileges"
         *    6C                                   # text(12)
         *       656C656D656E7456616C7565          # "elementValue"
         *    82                                   # array(2)
         *       A3                                # map(3)
         *          75                             # text(21)
         *             76656869636C655F63617465676F72795F636F6465 # "vehicle_category_code"
         *          61                             # text(1)
         *             41                          # "A"
         *          6A                             # text(10)
         *             69737375655F64617465        # "issue_date"
         *          D9 03EC                        # tag(1004)
         *             6A                          # text(10)
         *                323031382D30382D3039     # "2018-08-09"
         *          6B                             # text(11)
         *             6578706972795F64617465      # "expiry_date"
         *          D9 03EC                        # tag(1004)
         *             6A                          # text(10)
         *                323032342D31302D3230     # "2024-10-20"
         *       A3                                # map(3)
         *          75                             # text(21)
         *             76656869636C655F63617465676F72795F636F6465 # "vehicle_category_code"
         *          61                             # text(1)
         *             42                          # "B"
         *          6A                             # text(10)
         *             69737375655F64617465        # "issue_date"
         *          D9 03EC                        # tag(1004)
         *             6A                          # text(10)
         *                323031372D30322D3233     # "2017-02-23"
         *          6B                             # text(11)
         *             6578706972795F64617465      # "expiry_date"
         *          D9 03EC                        # tag(1004)
         *             6A                          # text(10)
         *                323032342D31302D3230     # "2024-10-20"
         */
        val input = """
            A4686469676573744944096672616E646F6D58204599F81BEAA2B20BD0FFCC9AA03A6F985BEFAB3F6BEAFFA41E6354CDB2AB2CE47165
            6C656D656E744964656E7469666965727264726976696E675F70726976696C656765736C656C656D656E7456616C756582A375766568
            69636C655F63617465676F72795F636F646561416A69737375655F64617465D903EC6A323031382D30382D30396B6578706972795F64
            617465D903EC6A323032342D31302D3230A37576656869636C655F63617465676F72795F636F646561426A69737375655F64617465D9
            03EC6A323031372D30322D32336B6578706972795F64617465D903EC6A323032342D31302D3230
        """.trimIndent().replace("\n", "")

        val inputDecoded = input.hexToByteArray()
        val deserialized = coseCompliantCbor.decodeFromByteArray(
            IssuerSignedItemSerializer(
                "org.iso.18013.5.1",
                "driving_privileges"
            ), inputDecoded
        )
        val serialized = deserialized.serialize("org.iso.18013.5.1")

        assertEquals(serialized.toHexString().uppercase(), input)
    }

    @Test
    // From ISO/IEC 18013-5:2021(E), page 130
    fun issuerAuthDeserialization() {
        /**
         * In diagnostic notation:
         * [
         *   << {1: -7} >>,
         *   {
         *     33: h'308201EF30820195A00302010202143C4416EED784F3B413E48F56F075ABFA6D87EB84300A06082A
         *           8648CE3D04030230233114301206035504030C0B75746F7069612069616361310B300906035504061302555330
         *           1E170D3230313030313030303030305A170D3231313030313030303030305A30213112301006035504030C0975
         *           746F706961206473310B30090603550406130255533059301306072A8648CE3D020106082A8648CE3D03010703
         *           420004ACE7AB7340E5D9648C5A72A9A6F56745C7AAD436A03A43EFEA77B5FA7B88F0197D57D8983E1B37D3A539
         *           F4D588365E38CBBF5B94D68C547B5BC8731DCD2F146BA381A83081A5301E0603551D120417301581136578616D
         *           706C65406578616D706C652E636F6D301C0603551D1F041530133011A00FA00D820B6578616D706C652E636F6D
         *           301D0603551D0E0416041414E29017A6C35621FFC7A686B7B72DB06CD12351301F0603551D2304183016801454
         *           FA2383A04C28E0D930792261C80C4881D2C00B300E0603551D0F0101FF04040302078030150603551D250101FF
         *           040B3009060728818C5D050102300A06082A8648CE3D040302034800304502210097717AB9016740C8D7BCDAA4
         *           94A62C053BBDECCE1383C1ACA72AD08DBC04CBB202203BAD859C13A63C6D1AD67D814D43E2425CAF90D422422C
         *           04A8EE0304C0D3A68D'
         *   },
         *   << 24(<<
         *     {
         *       "version": "1.0",
         *       "digestAlgorithm": "SHA-256",
         *       "valueDigests":
         *       {
         *         "org.iso.18013.5.1":
         *         {
         *           0: h'75167333B47B6C2BFB86ECCC1F438CF57AF055371AC55E1E359E20F254ADCEBF',
         *           1: h'67E539D6139EBD131AEF441B445645DD831B2B375B390CA5EF6279B205ED4571',
         *           2: h'3394372DDB78053F36D5D869780E61EDA313D44A392092AD8E0527A2FBFE55AE',
         *           3: h'2E35AD3C4E514BB67B1A9DB51CE74E4CB9B7146E41AC52DAC9CE86B8613DB555',
         *           4: h'EA5C3304BB7C4A8DCB51C4C13B65264F845541341342093CCA786E058FAC2D59',
         *           5: h'FAE487F68B7A0E87A749774E56E9E1DC3A8EC7B77E490D21F0E1D3475661AA1D',
         *           6: h'7D83E507AE77DB815DE4D803B88555D0511D894C897439F5774056416A1C7533',
         *           7: h'F0549A145F1CF75CBEEFFA881D4857DD438D627CF32174B1731C4C38E12CA936',
         *           8: h'B68C8AFCB2AAF7C581411D2877DEF155BE2EB121A42BC9BA5B7312377E068F66',
         *           9: h'0B3587D1DD0C2A07A35BFB120D99A0ABFB5DF56865BB7FA15CC8B56A66DF6E0C',
         *           10: h'C98A170CF36E11ABB724E98A75A5343DFA2B6ED3DF2ECFBB8EF2EE55DD41C881',
         *           11: h'B57DD036782F7B14C6A30FAAAAE6CCD5054CE88BDFA51A016BA75EDA1EDEA948',
         *           12: h'651F8736B18480FE252A03224EA087B5D10CA5485146C67C74AC4EC3112D4C3A'
         *         },
         *         "org.iso.18013.5.1.US":
         *         {
         *           0: h'D80B83D25173C484C5640610FF1A31C949C1D934BF4CF7F18D5223B15DD4F21C',
         *           1: h'4D80E1E2E4FB246D97895427CE7000BB59BB24C8CD003ECF94BF35BBD2917E34',
         *           2: h'8B331F3B685BCA372E85351A25C9484AB7AFCDF0D2233105511F778D98C2F544',
         *           3: h'C343AF1BD1690715439161ABA73702C474ABF992B20C9FB55C36A336EBE01A87'
         *         }
         *       },
         *       "deviceKeyInfo":
         *       {
         *         "deviceKey":
         *         {
         *           1: 2,
         *          -1: 1,
         *          -2: h'96313D6C63E24E3372742BFDB1A33BA2C897DCD68AB8C753E4FBD48DCA6B7F9A',
         *          -3: h'1FB3269EDD418857DE1B39A4E4A44B92FA484CAA722C228288F01D0C03A2C3D6'
         *         }
         *       },
         *       "docType": "org.iso.18013.5.1.mDL",
         *       "validityInfo":
         *       {
         *         "signed": 0("2020-10-01T13:30:02Z"),
         *         "validFrom": 0("2020-10-01T13:30:02Z"),
         *         "validUntil": 0("2021-10-01T13:30:02Z")
         *       }
         *     }
         *   >>)>>,
         *   h'59E64205DF1E2F708DD6DB0847AED79FC7C0201D80FA55BADCAF2E1BCF5902E1E5A62E4
         *   832044B890AD85AA53F129134775D733754D7CB7A413766AEFF13CB2E'
         * ]
         */
        val input = """
            8443a10126a118215901f3308201ef30820195a00302010202143c4416eed784f3b413e48f56f075abfa6d87e
            b84300a06082a8648ce3d04030230233114301206035504030c0b75746f7069612069616361310b3009060355
            040613025553301e170d3230313030313030303030305a170d3231313030313030303030305a302131123010
            06035504030c0975746f706961206473310b30090603550406130255533059301306072a8648ce3d020106082
            a8648ce3d03010703420004ace7ab7340e5d9648c5a72a9a6f56745c7aad436a03a43efea77b5fa7b88f0197d
            57d8983e1b37d3a539f4d588365e38cbbf5b94d68c547b5bc8731dcd2f146ba381a83081a5301e0603551d120
            417301581136578616d706c65406578616d706c652e636f6d301c0603551d1f041530133011a00fa00d820b65
            78616d706c652e636f6d301d0603551d0e0416041414e29017a6c35621ffc7a686b7b72db06cd12351301f0603
            551d2304183016801454fa2383a04c28e0d930792261c80c4881d2c00b300e0603551d0f0101ff040403020780
            30150603551d250101ff040b3009060728818c5d050102300a06082a8648ce3d04030203480030450221009771
            7ab9016740c8d7bcdaa494a62c053bbdecce1383c1aca72ad08dbc04cbb202203bad859c13a63c6d1ad67d814d
            43e2425caf90d422422c04a8ee0304c0d3a68d5903a2d81859039da66776657273696f6e63312e306f64696765
            7374416c676f726974686d675348412d3235366c76616c756544696765737473a2716f72672e69736f2e313830
            31332e352e31ad00582075167333b47b6c2bfb86eccc1f438cf57af055371ac55e1e359e20f254adcebf015820
            67e539d6139ebd131aef441b445645dd831b2b375b390ca5ef6279b205ed45710258203394372ddb78053f36d5
            d869780e61eda313d44a392092ad8e0527a2fbfe55ae0358202e35ad3c4e514bb67b1a9db51ce74e4cb9b7146e
            41ac52dac9ce86b8613db555045820ea5c3304bb7c4a8dcb51c4c13b65264f845541341342093cca786e058fac
            2d59055820fae487f68b7a0e87a749774e56e9e1dc3a8ec7b77e490d21f0e1d3475661aa1d0658207d83e507ae
            77db815de4d803b88555d0511d894c897439f5774056416a1c7533075820f0549a145f1cf75cbeeffa881d4857d
            d438d627cf32174b1731c4c38e12ca936085820b68c8afcb2aaf7c581411d2877def155be2eb121a42bc9ba5b7
            312377e068f660958200b3587d1dd0c2a07a35bfb120d99a0abfb5df56865bb7fa15cc8b56a66df6e0c0a5820c
            98a170cf36e11abb724e98a75a5343dfa2b6ed3df2ecfbb8ef2ee55dd41c8810b5820b57dd036782f7b14c6a30
            faaaae6ccd5054ce88bdfa51a016ba75eda1edea9480c5820651f8736b18480fe252a03224ea087b5d10ca5485
            146c67c74ac4ec3112d4c3a746f72672e69736f2e31383031332e352e312e5553a4005820d80b83d25173c484c
            5640610ff1a31c949c1d934bf4cf7f18d5223b15dd4f21c0158204d80e1e2e4fb246d97895427ce7000bb59bb24
            c8cd003ecf94bf35bbd2917e340258208b331f3b685bca372e85351a25c9484ab7afcdf0d2233105511f778d98
            c2f544035820c343af1bd1690715439161aba73702c474abf992b20c9fb55c36a336ebe01a876d646576696365
            4b6579496e666fa1696465766963654b6579a40102200121582096313d6c63e24e3372742bfdb1a33ba2c897dc
            d68ab8c753e4fbd48dca6b7f9a2258201fb3269edd418857de1b39a4e4a44b92fa484caa722c228288f01d0c03
            a2c3d667646f6354797065756f72672e69736f2e31383031332e352e312e6d444c6c76616c6964697479496e66
            6fa3667369676e6564c074323032302d31302d30315431333a33303a30325a6976616c696446726f6dc0743230
            32302d31302d30315431333a33303a30325a6a76616c6964556e74696cc074323032312d31302d30315431333a
            33303a30325a584059e64205df1e2f708dd6db0847aed79fc7c0201d80fa55badcaf2e1bcf5902e1e5a62e4832
            044b890ad85aa53f129134775d733754d7cb7a413766aeff13cb2e
        """.trimIndent().replace("\n", "").uppercase()

        val coseSigned = CoseSign1.fromTagged(input)
        val mso = coseSigned.decodeIsoPayload<MobileSecurityObject>()

        assertEquals(mso.version, "1.0")
        assertEquals(mso.digestAlgorithm, "SHA-256")
        assertEquals(mso.docType, "org.iso.18013.5.1.mDL")
        assertEquals(mso.validityInfo.signed, Instant.parse("2020-10-01T13:30:02Z"))
        assertEquals(mso.validityInfo.validFrom, Instant.parse("2020-10-01T13:30:02Z"))
        assertEquals(mso.validityInfo.validUntil, Instant.parse("2021-10-01T13:30:02Z"))
        val valueDigestList = mso.valueDigests["org.iso.18013.5.1"]
        assertNotNull(valueDigestList)
        assertEquals(valueDigestList.findItem(0U).toHexString().uppercase(), "75167333B47B6C2BFB86ECCC1F438CF57AF055371AC55E1E359E20F254ADCEBF")
        assertEquals(valueDigestList.findItem(1U).toHexString().uppercase(), "67E539D6139EBD131AEF441B445645DD831B2B375B390CA5EF6279B205ED4571")
        val valueDigestListUs = mso.valueDigests["${"org.iso.18013.5.1"}.US"]
        assertNotNull(valueDigestListUs)
        assertEquals(valueDigestListUs.findItem(0U).toHexString().uppercase(), "D80B83D25173C484C5640610FF1A31C949C1D934BF4CF7F18D5223B15DD4F21C")
        assertEquals(valueDigestListUs.findItem(1U).toHexString().uppercase(), "4D80E1E2E4FB246D97895427CE7000BB59BB24C8CD003ECF94BF35BBD2917E34")

        assertEquals(coseSigned.serialize().toHexString().uppercase(), input)
    }

}

private fun ItemsRequestList.findItem(key: String) =
    entries.first { it.key == key }.value

private fun ValueDigestList.findItem(digestId: UInt) =
    entries.first { it.key == digestId }.value

private fun IssuerSignedList.findItem(digestId: UInt) =
    entries.first { it.value.digestId == digestId }.value


private fun IssuerSignedItem.serialize(namespace: String): ByteArray =
    coseCompliantCbor.encodeToByteArray(IssuerSignedItemSerializer(namespace, elementIdentifier), this)
