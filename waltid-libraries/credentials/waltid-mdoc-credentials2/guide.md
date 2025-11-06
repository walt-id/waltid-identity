# Preview: New ISO credentials stack

The following guide will allow you to play through the new OpenID4VP 1.0
flows for ISO credentials.

Containers:
- Enterprise API: `waltid/waltid-enterprise-api:dcql-2025-09-19` (preview of Enterprise with the wallet supporting mdocs via OpenID4VP 1.0)
- OSS Verifier2: `waltid/verifier-api2:dcql-2025-09-19` (new OSS Verifier2, with iso/mdocs credentials preview)
- OSS Issuer: `waltid/issuer-api:dcql-2025-09-19` (usual OSS Issuer, old iso/mdocs credentials stack)

Hosted:
- Enterprise API: https://enterprise.mdoc-test.waltid.cloud/swagger
- OSS Verifier2: https://verifier.mdoc-test.waltid.cloud/swagger/index.html
- OSS Issuer: https://issuer.mdoc-test.waltid.cloud/swagger/index.html

## 0. (Get credentials into wallet)

To present credentials to a Relying Party with the wallet, one of course
must first have a Verifiable Credential to present in their wallet.

The easiest way is to use the Issuer Service.

However, keep in mind that the issuer service is not yet part of the new
ISO credential stack, and as such is not yet officially supported.
The new Issuer for the new ISO credential stack based on OpenID4VCI 1.0 will be released in the near future.

Below, a simple Photo ID is issued.

### 0.1. With OSS Issuer

#### 0.1.0. Configuration

Add the supported credential type to the issuer (for the open source issuer, edit the config file `credential-issuer-metadata.conf`):
```ini
# config/credential-issuer-metadata.conf
supportedCredentialTypes = {
    ...
    "org.iso.23220.photoid.1" = {
         format = mso_mdoc
         cryptographic_binding_methods_supported = ["cose_key"]
         credential_signing_alg_values_supported = ["ES256"]
         proof_types_supported = { cwt = { proof_signing_alg_values_supported = ["ES256"] } }
         types = ["org.iso.23220.photoid.1"]
         doctype = "org.iso.23220.photoid.1"
     }
}
```

#### 0.1.1. Issue credential
And use the `/openid4vc/mdoc/issue` endpoint:

```shell
curl -X 'POST' \
  'https://issuer.mdoc-test.waltid.cloud/openid4vc/mdoc/issue' \
  -H 'accept: */*' \
  -H 'Content-Type: application/json' \
  -d '{
  "issuerKey": {
    "type": "jwk",
    "jwk": {
      "kty": "EC",
      "d": "-wSIL_tMH7-mO2NAfHn03I8ZWUHNXVzckTTb96Wsc1s",
      "crv": "P-256",
      "kid": "sW5yv0UmZ3S0dQuUrwlR9I3foREBHHFwXhGJGqGEVf0",
      "x": "Pzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5U",
      "y": "6dwhUAzKzKUf0kNI7f40zqhMZNT0c40O_WiqSLCTNZo"
    }
  },
  "credentialConfigurationId": "org.iso.23220.photoid.1",
  "mdocData": {
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
      "document_number": "1234567890",
      "name_at_birth": "Max Mustermann",
      "birthplace": "Baden bei Wien",
      "resident_address_unicode": "Püchlgasse 1D, 4.4.4.",
      "resident_city_unicode": "Vienna",
      "resident_postal_code": "1190",
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
      "travel_document_number": "001122334",
      "resident_state": "VIENNA"
    },
    "org.iso.23220.dtc.1": {
      "dtc_version": "01",
      "dtc_dg1": "P<AUTDOE<<JOHN<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<0011223340AUT2001207M3009129<<<<<<<<<<<<<<06"
    }
  },
  "x5Chain": [
    "-----BEGIN CERTIFICATE-----\nMIICCTCCAbCgAwIBAgIUfqyiArJZoX7M61/473UAVi2/UpgwCgYIKoZIzj0EAwIwKDELMAkGA1UEBhMCQVQxGTAXBgNVBAMMEFdhbHRpZCBUZXN0IElBQ0EwHhcNMjUwNjAyMDY0MTEzWhcNMjYwOTAyMDY0MTEzWjAzMQswCQYDVQQGEwJBVDEkMCIGA1UEAwwbV2FsdGlkIFRlc3QgRG9jdW1lbnQgU2lnbmVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5Xp3CFQDMrMpR/SQ0jt/jTOqExk1PRzjQ79aKpIsJM1mqOBrDCBqTAfBgNVHSMEGDAWgBTxCn2nWMrE70qXb614U14BweY2azAdBgNVHQ4EFgQUx5qkOLC4lpl1xpYZGmF9HLxtp0gwDgYDVR0PAQH/BAQDAgeAMBoGA1UdEgQTMBGGD2h0dHBzOi8vd2FsdC5pZDAVBgNVHSUBAf8ECzAJBgcogYxdBQECMCQGA1UdHwQdMBswGaAXoBWGE2h0dHBzOi8vd2FsdC5pZC9jcmwwCgYIKoZIzj0EAwIDRwAwRAIgHTap3c6yCUNhDVfZWBPMKj9dCWZbrME03kh9NJTbw1ECIAvVvuGll9O21eR16SkJHHAA1pPcovhcTvF9fz9cc66M\n-----END CERTIFICATE-----\n"
  ]
}
'
```

As you can see, in the JSON body you can set the namespaces and the namespace element that shall be issued into the ISO credential.
This request will return an issuance offer URL. Enter this into the "offerUrl" in the wallets `/v1/{wallet}/wallet-service-api/credentials/receive` endpoint:

#### 0.1.2. Receive credential into wallet

For authentication, in development mode, use this user: "user@walt.id 123456". Alternatively you can create another account.

```shell
curl -X 'POST' \
  'https://waltid.enterprise.mdoc-test.waltid.cloud/v1/waltid.test.wallet/wallet-service-api/credentials/receive' \
  -H 'accept: application/json' \
  -H 'Authorization: Bearer eyJhbGciOiJFZERTQSJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0wMDAwMDAwMDAwMDAiLCJzZXNzaW9uIjoiMDM0YzgwYWUtZGI5Zi00ODJjLWI1YTgtOTk5MjhiZmI4ZDkwIiwiZXhwIjoxNzU4ODk1MDI0fQ.5GeGe2ogixj50gqZOMjLOfTCoaig_814cLkhF82-cyMzFYb16Jqz6WMgFAkFWUaG9MXmNcg8mp4P_IrbMzi_Bw' \
  -H 'Content-Type: application/json' \
  -d '{
  "offerUrl": "openid-credential-offer://issuer.mdoc-test.waltid.cloud/draft13/?credential_offer_uri=https%3A%2F%2Fissuer.mdoc-test.waltid.cloud%2Fdraft13%2FcredentialOffer%3Fid%3D868b434c-b8aa-4daa-8921-065fbb48f410",
  "keyReference": "waltid.test.kms.wallet_key"
}'
```

You will find the following credential stored in the credential store (viewable with the `GET /v1/{credentialstore}/credential-store-service-api/credentials/list` endpoint):

```json
{
  "_id": "waltid.test.credentialstore.1ca16f88-aee9-483c-8c7e-6a8d718b27b6",
  "credential": {
    "type": "vc-mdocs",
    "credentialData": {
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
        "document_number": "1234567890",
        "name_at_birth": "Max Mustermann",
        "birthplace": "Baden bei Wien",
        "resident_address_unicode": "Püchlgasse 1D, 4.4.4.",
        "resident_city_unicode": "Vienna",
        "resident_postal_code": "1190",
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
        "travel_document_number": "1122334",
        "resident_state": "VIENNA"
      },
      "org.iso.23220.dtc.1": {
        "dtc_version": 1,
        "dtc_dg1": "P<AUTDOE<<JOHN<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<0011223340AUT2001207M3009129<<<<<<<<<<<<<<06"
      },
      "docType": "org.iso.23220.photoid.1"
    },
    "signed": "a267646f6354797065776f72672e69736f2e32333232302e70686f746f69642e316c6973737565725369676e6564a26a6e616d65537061636573a3716f72672e69736f2e31383031332e352e3195d818585aa4686469676573744944006672616e646f6d5070f33bd9d920c758b40a5a67b0b0301971656c656d656e744964656e7469666965727366616d696c795f6e616d655f756e69636f64656c656c656d656e7456616c756563446f65d818585aa4686469676573744944016672616e646f6d50f3c575106ca46c9c2ff9851a66218cd871656c656d656e744964656e74696669657272676976656e5f6e616d655f756e69636f64656c656c656d656e7456616c7565644a6f686ed8185858a4686469676573744944026672616e646f6d5077be51d654f521cbe0b4d71b6b06e73871656c656d656e744964656e7469666965726a62697274685f646174656c656c656d656e7456616c75656a323030302d30312d3230d8185858a4686469676573744944036672616e646f6d5043951c9c558540977286a38223d7da2671656c656d656e744964656e7469666965726a69737375655f646174656c656c656d656e7456616c75656a323032352d30392d3132d8185859a4686469676573744944046672616e646f6d50bdc7002c6bc0cfa087ad6e3555540e1971656c656d656e744964656e7469666965726b6578706972795f646174656c656c656d656e7456616c75656a323033302d30392d3132d8185869a4686469676573744944056672616e646f6d5083ddcfb8fa8a249e3fed9139dd707b2471656c656d656e744964656e746966696572781969737375696e675f617574686f726974795f756e69636f64656c656c656d656e7456616c75656b4c5044205769656e203232d8185855a4686469676573744944066672616e646f6d50dac3c52ea12467e7fa504d067f819a3e71656c656d656e744964656e7469666965726f69737375696e675f636f756e7472796c656c656d656e7456616c7565624154d8185847a4686469676573744944076672616e646f6d504059cf49f5cafa8992bde228c81a10d771656c656d656e744964656e746966696572637365786c656c656d656e7456616c756501d8185851a4686469676573744944086672616e646f6d502aaf6252abd4be5fde07d5e8ec17764d71656c656d656e744964656e7469666965726b6e6174696f6e616c6974796c656c656d656e7456616c7565624154d8185857a4686469676573744944096672616e646f6d503579bef05340e5b5a4b38fcb23cd6a5b71656c656d656e744964656e7469666965726f646f63756d656e745f6e756d6265726c656c656d656e7456616c75651a499602d2d818585fa46864696765737449440a6672616e646f6d509c1bfb43176dbcc8e494b3a2db4c1f7b71656c656d656e744964656e7469666965726d6e616d655f61745f62697274686c656c656d656e7456616c75656e4d6178204d75737465726d616e6ed818585ca46864696765737449440b6672616e646f6d50f4f4d96ff2742ba24b6127014d91f0f871656c656d656e744964656e7469666965726a6269727468706c6163656c656c656d656e7456616c75656e426164656e20626569205769656ed8185873a46864696765737449440c6672616e646f6d50e2dd66b0bb4a051ab279e46ccf5bccda71656c656d656e744964656e74696669657278187265736964656e745f616464726573735f756e69636f64656c656c656d656e7456616c75657650c3bc63686c67617373652031442c20342e342e342ed818585fa46864696765737449440d6672616e646f6d50bd40101143beb7486e9c549aeb7a1d3d71656c656d656e744964656e746966696572757265736964656e745f636974795f756e69636f64656c656c656d656e7456616c7565665669656e6e61d818585aa46864696765737449440e6672616e646f6d5091a12b534a9e1c2ca8163433194d7ebe71656c656d656e744964656e746966696572747265736964656e745f706f7374616c5f636f64656c656c656d656e7456616c75651904a6d8185856a46864696765737449440f6672616e646f6d5022b3f3bf92edf83f927dcccc5869a64571656c656d656e744964656e746966696572707265736964656e745f636f756e7472796c656c656d656e7456616c7565624154d818584fa4686469676573744944106672616e646f6d50ce9556ef76f563de61fbe58721ba43b971656c656d656e744964656e7469666965726b6167655f6f7665725f31386c656c656d656e7456616c7565f5d8185851a4686469676573744944116672616e646f6d5082894d511da1a2173d73456686eba50471656c656d656e744964656e7469666965726c6167655f696e5f79656172736c656c656d656e7456616c75651819d8185854a4686469676573744944126672616e646f6d50fed9f0d45626eb52884d08b0a73fb3e271656c656d656e744964656e7469666965726e6167655f62697274685f796561726c656c656d656e7456616c75651907d0d8185860a4686469676573744944136672616e646f6d5056be2380676f55d5a0d473bc01954a2071656c656d656e744964656e7469666965727266616d696c795f6e616d655f6c6174696e316c656c656d656e7456616c75656a4d75737465726d616e6ed8185858a4686469676573744944146672616e646f6d50db91f00f2b3cf9db85e7b1693a7d05e071656c656d656e744964656e74696669657271676976656e5f6e616d655f6c6174696e316c656c656d656e7456616c7565634d6178776f72672e69736f2e32333232302e70686f746f69642e3189d8185854a4686469676573744944006672616e646f6d50ae1185ee6305e889942740573f86c98371656c656d656e744964656e74696669657269706572736f6e5f69646c656c656d656e7456616c75656741543132333435d8185853a4686469676573744944016672616e646f6d506191db3e3068cf18655e40a181541be871656c656d656e744964656e7469666965726d62697274685f636f756e7472796c656c656d656e7456616c7565624154d818585ca4686469676573744944026672616e646f6d50201fbe5779cfc9f0432b64f41774493771656c656d656e744964656e7469666965726b62697274685f73746174656c656c656d656e7456616c75656d4c4f5745522041555354524941d818585ca4686469676573744944036672616e646f6d50ab9343a13b066c74d386bd72bd6e0f9f71656c656d656e744964656e7469666965726a62697274685f636974796c656c656d656e7456616c75656e426164656e20626569205769656ed8185862a4686469676573744944046672616e646f6d503c936db5c12d1b058307f5ae291c460a71656c656d656e744964656e7469666965727561646d696e6973747261746976655f6e756d6265726c656c656d656e7456616c756569415441544154313233d818585ea4686469676573744944056672616e646f6d506954b7c846acf278a8b9f749efb78fee71656c656d656e744964656e7469666965726f7265736964656e745f7374726565746c656c656d656e7456616c75656b50c3bc63686c6761737365d8185863a4686469676573744944066672616e646f6d509975cd1814249553bd1327bf06628b3571656c656d656e744964656e746966696572757265736964656e745f686f7573655f6e756d6265726c656c656d656e7456616c75656a31442c20342e342e342ed818585ea4686469676573744944076672616e646f6d50be415b0737fba19317a2fff1d9d158ca71656c656d656e744964656e7469666965727674726176656c5f646f63756d656e745f6e756d6265726c656c656d656e7456616c75651a0011201ed8185858a4686469676573744944086672616e646f6d50177fe489f9714e45631a72bb766ee8da71656c656d656e744964656e7469666965726e7265736964656e745f73746174656c656c656d656e7456616c7565665649454e4e41736f72672e69736f2e32333232302e6474632e3182d818584fa4686469676573744944006672616e646f6d5009577603b0002d3ec24fe6c354332d5471656c656d656e744964656e7469666965726b6474635f76657273696f6e6c656c656d656e7456616c756501d81858a4a4686469676573744944016672616e646f6d5046dd8f831eaf2f8ed620618b898ba38071656c656d656e744964656e746966696572676474635f6467316c656c656d656e7456616c75657858503c415554444f453c3c4a4f484e3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c3c30303131323233333430415554323030313230374d333030393132393c3c3c3c3c3c3c3c3c3c3c3c3c3c30366a697373756572417574688443a10126a1182159020d30820209308201b0a00302010202147eaca202b259a17ecceb5ff8ef7500562dbf5298300a06082a8648ce3d0403023028310b30090603550406130241543119301706035504030c1057616c74696420546573742049414341301e170d3235303630323036343131335a170d3236303930323036343131335a3033310b30090603550406130241543124302206035504030c1b57616c746964205465737420446f63756d656e74205369676e65723059301306072a8648ce3d020106082a8648ce3d030107034200043f3a7a795480757111a80a7cabc3ae0c4865d882c601aa1a4174c9dac0f68395e9dc21500ccacca51fd24348edfe34cea84c64d4f4738d0efd68aa48b093359aa381ac3081a9301f0603551d23041830168014f10a7da758cac4ef4a976fad78535e01c1e6366b301d0603551d0e04160414c79aa438b0b8969975c696191a617d1cbc6da748300e0603551d0f0101ff040403020780301a0603551d1204133011860f68747470733a2f2f77616c742e696430150603551d250101ff040b3009060728818c5d05010230240603551d1f041d301b3019a017a015861368747470733a2f2f77616c742e69642f63726c300a06082a8648ce3d040302034700304402201d36a9ddceb20943610d57d95813cc2a3f5d09665bacc134de487d3494dbc35102200bd5bee1a597d3b6d5e475e929091c7000d693dca2f85c4ef17d7f3f5c73ae8c5905ead8185905e5a66776657273696f6e63312e306f646967657374416c676f726974686d675348412d3235366c76616c756544696765737473a3716f72672e69736f2e31383031332e352e31b5005820aea592ddef2aeb74381ca77566365198bc5ca480a26cbcfd418ae7d0d26367a3015820da6d18218eecebf08547cd49f8a1de48b880b3effd51c5250af4a3dabd21d464025820427f5f2eff1e250b20b058e268f441005bce08c405475e0a19744cdb0cfe16e90358203f3f3707eb36dc80471223aa731506ee291854a6c7d5defb1aaac8b54d21c8b804582031508ec8c92ee15e17d34c74bbd98940a4127003dd55ee22f57af185514b4816055820e7aec50f0a2d93eab8024262044854f5d6542a871d60aed2cc191e7ad55b61f0065820861386931d2ab371cecca31b0909e9934cce35f8eed66d00e856678185c6bc13075820e6f81c95f0e3d15ca6acb9cc1d144b6d6de304bc07e7d610fd76de7291b9bbf208582027e0642f5b9cc4bdee6ed0f605c4360ba019a03d455269f099b6263cb24dfc13095820db9e4f284399a34c4b0f842cd066f084fdf256efcb31948733e153935e30aefc0a582017d65c0db8331a7ade5fc1510c9427f3d6652c4234899d4e4d69768fbec1f39c0b58203f41d0505d4ac5b26c2b4fc22b496f9713f5d6d3ffd0f8897810d75abe9dd1230c5820cf52ef397c33d197c66ddcbf638d70adb43b52377f23e394cba6570e40251d2e0d582073cd6f037f787b104d88760b566a34ffc291a2504dd3282a2b792f2e13ad3bff0e5820146696af06c5e99ac6f0a5c61a6d3f9ede8aaa0ef36c9c2e7d366eadf1acf2460f5820265e582512a1cbb772f346f0d50af589faf30f79d7c9768d7c9425b3a02720fc10582045049fd29c210f009a77f1d8f0f193cddd5678bf6ef5449d186684137f8cc5d51158202611f542a5a16b2b1c2911052d16b2626fbf2fa98ed200d64ee1c4518b58c795125820f67c243f71bb9b392396544ea9c19f0f80005187d6082797fdb3f45fa1392cdb1358208c95bba02bb847e41f5c09edc84871d84eb1ec5538cb9d2f85c6613e3a43ff171458200c33f8ba4ed3b1c84544b670527efbd8bcec13f23835836147ab7e22e3792536776f72672e69736f2e32333232302e70686f746f69642e31a9005820acafe4c641de9c77cd9ef2c2d008c28c2e3eb4d87e9d8ec95cc5943d9cab73c1015820273816ee73a145b57ff3f7a35bb16716935e832a77bc8356720cd3dee9de903c025820e1f47505c2231d9860e58afcef9922587ac878d859afba0e5c648d1ecf5479130358206abaaabdae14cba06914eef4d4f33fab2b1406e42de2e79268dfc3a10a26ca050458204561151f18ed1b8bfd5ccafdda5bed793296b7de3166a58c4d5572253ec8acb9055820decd17239701d27b78b3dd4ba7487f9f3f2670a64292e9eb902235dbd3dc910706582017ae306e813e496df795dca91382a3d55e621db09e4a5dcd7dfd8b8c046a7377075820a356de52bf7d6233fc8c00f124950ac2fe23e5cee8724bda69601ccc719dbe13085820bdff0ed0da08b3c2ffaf6ddfc86c36785c2b621d33c5b39167ab2be0a31e3500736f72672e69736f2e32333232302e6474632e31a2005820c88336a5723e9d9558a10c57662deb7fbd7c9a3f44562639ec3c20aac2a7ea760158205e5506ca68aa6145d53025ba84c9fd74cce4bcc522eafd629f8c5aaf58836ba86d6465766963654b6579496e666fa1696465766963654b6579a4010220012158207934f659dce598e58122d7604a6b2a075fc13faf707eec0e7b5218bda63559d2225820c1b3aedc63f4d8988e548450fee7d62d134e98307ab1e60069b0a6b0605faffe67646f6354797065776f72672e69736f2e32333232302e70686f746f69642e316c76616c6964697479496e666fa3667369676e6564c0781e323032352d30392d31395430363a31383a30382e3335363839303335325a6976616c696446726f6dc0781e323032352d30392d31395430363a31383a30382e3335363839313137335a6a76616c6964556e74696cc0781e323032362d30392d31395430363a31383a30382e3335363839313239345a5840d8435590fa4518710d6b9fdc6706730a4603870afc174eabb273b393ac43ae187a5590809e87414fa7d5dce4447d9c8c61253e2347d6057cae3dab8a6189f3a6",
    "docType": "org.iso.23220.photoid.1",
    "signature": {
      "type": "signature-cose",
      "x5cList": [
        "MIICCTCCAbCgAwIBAgIUfqyiArJZoX7M61/473UAVi2/UpgwCgYIKoZIzj0EAwIwKDELMAkGA1UEBhMCQVQxGTAXBgNVBAMMEFdhbHRpZCBUZXN0IElBQ0EwHhcNMjUwNjAyMDY0MTEzWhcNMjYwOTAyMDY0MTEzWjAzMQswCQYDVQQGEwJBVDEkMCIGA1UEAwwbV2FsdGlkIFRlc3QgRG9jdW1lbnQgU2lnbmVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5Xp3CFQDMrMpR/SQ0jt/jTOqExk1PRzjQ79aKpIsJM1mqOBrDCBqTAfBgNVHSMEGDAWgBTxCn2nWMrE70qXb614U14BweY2azAdBgNVHQ4EFgQUx5qkOLC4lpl1xpYZGmF9HLxtp0gwDgYDVR0PAQH/BAQDAgeAMBoGA1UdEgQTMBGGD2h0dHBzOi8vd2FsdC5pZDAVBgNVHSUBAf8ECzAJBgcogYxdBQECMCQGA1UdHwQdMBswGaAXoBWGE2h0dHBzOi8vd2FsdC5pZC9jcmwwCgYIKoZIzj0EAwIDRwAwRAIgHTap3c6yCUNhDVfZWBPMKj9dCWZbrME03kh9NJTbw1ECIAvVvuGll9O21eR16SkJHHAA1pPcovhcTvF9fz9cc66M"
      ],
      "signerKey": {
        "type": "jwk",
        "jwk": {
          "kty": "EC",
          "crv": "P-256",
          "x": "Pzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5U",
          "y": "6dwhUAzKzKUf0kNI7f40zqhMZNT0c40O_WiqSLCTNZo"
        }
      }
    }
  },
  "format": "mso_mdoc"
},
"parent": "waltid.test.credentialstore"
}
```
As you can see, the wallet decodes the signed CBOR format back into the human-readable JSON values,
similar to how they were entered in the issuer.

As already mentioned, keep in mind that the issuance of ISO credentials with the Issuer is
not yet part of the new credential stack and is only demonstrated here for easier testing
of the Wallet + Verifier flow. Especially non-primitive values for ISO credentials will only
be work with the new Issuer2 interface (based on OpenID4VCI 1.0), as the old Issuer does not
process them correctly.


### 0.2. With Enterprise Issuer

Besides the OSS issue, the Enterprise Issuer can be used (with a slightly different API):

```shell
# Create KMS for Issuer
curl -X 'POST' \
  'https://waltid.enterprise.mdoc-test.waltid.cloud/v1/issuerkms/resource-api/services/create' \
  -H 'accept: */*' \
  -H 'Content-Type: application/json' \
  -d '{ "type": "kms" }'

# Import Key into KMS
curl -X 'POST' \
  'https://waltid.enterprise.mdoc-test.waltid.cloud/v1/issuerkms.key1/kms-service-api/keys/store' \
  -H 'accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
    "type": "jwk",
    "jwk": {
      "kty": "EC",
      "d": "-wSIL_tMH7-mO2NAfHn03I8ZWUHNXVzckTTb96Wsc1s",
      "crv": "P-256",
      "kid": "sW5yv0UmZ3S0dQuUrwlR9I3foREBHHFwXhGJGqGEVf0",
      "x": "Pzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5U",
      "y": "6dwhUAzKzKUf0kNI7f40zqhMZNT0c40O_WiqSLCTNZo"
    }
  }'

# (Or generate Key yourself (also create x5cchain then)):
curl -X 'POST' \
  'https://waltid.enterprise.mdoc-test.waltid.cloud/v1/issuerkms.key1/kms-service-api/keys/generate' \
  -H 'accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{ "keyType": "secp256r1" }'

# Create Issuer
curl -X 'POST' \
  'https://waltid.enterprise.mdoc-test.waltid.cloud/v1/issuer/resource-api/services/create' \
  -H 'accept: */*' \
  -H 'Content-Type: application/json' \
  -d '{
  "type": "issuer",
  "supportedCredentialTypes": {
    "org.iso.23220.photoid.1": {
      "format": "mso_mdoc",
      "cryptographic_binding_methods_supported": [
        "cose_key"
      ],
      "credential_signing_alg_values_supported": [
        "ES256"
      ],
      "credential_definition": {
        "type": [
          "org.iso.23220.photoid.1"
        ]
      },
      "doctype": "org.iso.23220.photoid.1"
    }
  },
  "tokenKeyId": "waltid.issuerkms.key1",
  "kms": "waltid.issuerkms"
}'

# Issue Mdoc
curl -X 'POST' \
  'https://issuer.mdoc-test.waltid.cloud/openid4vc/mdoc/issue' \
  -H 'accept: */*' \
  -H 'Content-Type: application/json' \
  -d '{
  "issuerKey": {
    "type": "jwk",
    "jwk": {
      "kty": "EC",
      "d": "-wSIL_tMH7-mO2NAfHn03I8ZWUHNXVzckTTb96Wsc1s",
      "crv": "P-256",
      "kid": "sW5yv0UmZ3S0dQuUrwlR9I3foREBHHFwXhGJGqGEVf0",
      "x": "Pzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5U",
      "y": "6dwhUAzKzKUf0kNI7f40zqhMZNT0c40O_WiqSLCTNZo"
    }
  },
  "credentialConfigurationId": "org.iso.23220.photoid.1",
  "mdocData": {
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
      "document_number": "1234567890",
      "name_at_birth": "Max Mustermann",
      "birthplace": "Baden bei Wien",
      "resident_address_unicode": "Püchlgasse 1D, 4.4.4.",
      "resident_city_unicode": "Vienna",
      "resident_postal_code": "1190",
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
      "travel_document_number": "001122334",
      "resident_state": "VIENNA"
    },
    "org.iso.23220.dtc.1": {
      "dtc_version": "01",
      "dtc_dg1": "P<AUTDOE<<JOHN<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<0011223340AUT2001207M3009129<<<<<<<<<<<<<<06"
    }
  },
  "x5Chain": [
    "-----BEGIN CERTIFICATE-----\nMIICCTCCAbCgAwIBAgIUfqyiArJZoX7M61/473UAVi2/UpgwCgYIKoZIzj0EAwIwKDELMAkGA1UEBhMCQVQxGTAXBgNVBAMMEFdhbHRpZCBUZXN0IElBQ0EwHhcNMjUwNjAyMDY0MTEzWhcNMjYwOTAyMDY0MTEzWjAzMQswCQYDVQQGEwJBVDEkMCIGA1UEAwwbV2FsdGlkIFRlc3QgRG9jdW1lbnQgU2lnbmVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5Xp3CFQDMrMpR/SQ0jt/jTOqExk1PRzjQ79aKpIsJM1mqOBrDCBqTAfBgNVHSMEGDAWgBTxCn2nWMrE70qXb614U14BweY2azAdBgNVHQ4EFgQUx5qkOLC4lpl1xpYZGmF9HLxtp0gwDgYDVR0PAQH/BAQDAgeAMBoGA1UdEgQTMBGGD2h0dHBzOi8vd2FsdC5pZDAVBgNVHSUBAf8ECzAJBgcogYxdBQECMCQGA1UdHwQdMBswGaAXoBWGE2h0dHBzOi8vd2FsdC5pZC9jcmwwCgYIKoZIzj0EAwIDRwAwRAIgHTap3c6yCUNhDVfZWBPMKj9dCWZbrME03kh9NJTbw1ECIAvVvuGll9O21eR16SkJHHAA1pPcovhcTvF9fz9cc66M\n-----END CERTIFICATE-----\n"
  ]
}'
```
(make sure x5Chain fits to the generated key)

The wallet receive side is the same as in the [Receive credential into wallet section](#012-receive-credential-into-wallet).

### 0.3. Manual import

Alternatively, one can also manually store such credential into the wallet with the `/v1/{target}/credential-store-service-api/credentials/store` endpoint.

## 1. ISO/Mdocs Credentials Presentation Flow

Use the Verifier2 interface (OSS or Enterprise) for OpenID4VP 1.0 presentation flows.

### 1.1. With OSS Verifier (waltid-verifier-api2)

#### 1.1.0. Setup

Config files:

`_features.conf`:
```hocon
enabledFeatures = [
    # entra,
    # ...
]
disabledFeatures = [
    # ...
    # debug-endpoints
]
```
(you can leave this default)

`verifier-service.conf`:
```hocon
clientId: "verifier2"
clientMetadata: {
    clientName: "Verifier2"
    logoUri: "https://images.squarespace-cdn.com/content/v1/609c0ddf94bcc0278a7cbdb4/4d493ccf-c893-4882-925f-fda3256c38f4/Walt.id_Logo_transparent.png"
}
urlPrefix:  "http://localhost:7003/verification-session/"
```
Set the `urlPrefix` to the publicly accessible URL for the verification-session API used by the wallet
(the wallet calls the endpoints `/verification-session/{verification-session}/request` and `/verification-session/{verification-session}/response`).

`web.conf`:
```hocon
webHost = "0.0.0.0"
webPort = "7003"
```

#### 1.1.1 Create verification request

With OpenID4VP 1.0, the credential query is specified with "DCQL", see this page for examples: https://openid.github.io/OpenID4VP/openid-4-verifiable-presentations-wg-draft.html#more_dcql_query_examples

Create a verification session with the `/verification-session/create` endpoint:
```json
{
  "dcql_query": {
    "credentials": [
      {
        "id": "my_photoid",
        "format": "mso_mdoc",
        "meta": {
          "doctype_value": "org.iso.23220.photoid.1"
        },
        "claims": [
          { "path": [ "org.iso.18013.5.1", "family_name_unicode" ] },
          { "path": [ "org.iso.18013.5.1", "given_name_unicode" ] },
          { "path": [ "org.iso.18013.5.1", "issuing_authority_unicode" ] },
          {
            "path": [ "org.iso.18013.5.1", "resident_postal_code" ],
            "values": [ 1180, 1190, 1200, 1210 ]
          },
          {
            "path": [ "org.iso.18013.5.1", "issuing_country" ],
            "values": [ "AT" ]
          },

          { "path": [ "org.iso.23220.photoid.1", "person_id" ] },
          { "path": [ "org.iso.23220.photoid.1", "resident_street" ] },
          { "path": [ "org.iso.23220.photoid.1", "administrative_number" ] },
          { "path": [ "org.iso.23220.photoid.1", "travel_document_number" ] },

          { "path": [ "org.iso.23220.dtc.1", "dtc_version" ] },
          { "path": [ "org.iso.23220.dtc.1", "dtc_dg1" ] }
        ]
      }
    ]
  },
  "policies": {
    "vcPolicies": [
      { "policy": "signature" },
      { "policy": "regex", "path": "$.['org.iso.23220.dtc.1'].dtc_version", "regex": "^(\"[0-9]+\"|-?[0-9]+(\\.[0-9]+)?)$" }
    ]
  }
}
```
This is an example of a verification session creation to request an ISO 23220-4 Photo ID, and apply
certain policies.

```shell
curl -X 'POST' \
  'https://waltid.enterprise.mdoc-test.waltid.cloud/v1/v2/verifier2-service-api/verification-session/create' \
  -H 'accept: application/json' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer eyJhbGciOiJFZERTQSJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0wMDAwMDAwMDAwMDAiLCJzZXNzaW9uIjoiMDM0YzgwYWUtZGI5Zi00ODJjLWI1YTgtOTk5MjhiZmI4ZDkwIiwiZXhwIjoxNzU4ODk1MDI0fQ.5GeGe2ogixj50gqZOMjLOfTCoaig_814cLkhF82-cyMzFYb16Jqz6WMgFAkFWUaG9MXmNcg8mp4P_IrbMzi_Bw' \
  -d '{
  "dcql_query": {
    "credentials": [
      {
        "id": "my_photoid",
        "format": "mso_mdoc",
        "meta": {
          "doctype_value": "org.iso.23220.photoid.1"
        },
        "claims": [
          { "path": [ "org.iso.18013.5.1", "family_name_unicode" ] },
          { "path": [ "org.iso.18013.5.1", "given_name_unicode" ] },
          { "path": [ "org.iso.18013.5.1", "issuing_authority_unicode" ] },
          {
            "path": [ "org.iso.18013.5.1", "resident_postal_code" ],
            "values": [ 1180, 1190, 1200, 1210 ]
          },
          {
            "path": [ "org.iso.18013.5.1", "issuing_country" ],
            "values": [ "AT" ]
          },

          { "path": [ "org.iso.23220.photoid.1", "person_id" ] },
          { "path": [ "org.iso.23220.photoid.1", "resident_street" ] },
          { "path": [ "org.iso.23220.photoid.1", "administrative_number" ] },
          { "path": [ "org.iso.23220.photoid.1", "travel_document_number" ] },

          { "path": [ "org.iso.23220.dtc.1", "dtc_version" ] },
          { "path": [ "org.iso.23220.dtc.1", "dtc_dg1" ] }
        ]
      }
    ]
  },
  "policies": {
    "vcPolicies": [
      { "policy": "signature" },
      { "policy": "regex", "path": "$.['\''org.iso.23220.dtc.1'\''].dtc_version", "regex": "^(\"[0-9]+\"|-?[0-9]+(\\.[0-9]+)?)$" }
    ]
  }
}'

```

Response:
```json
{
  "sessionId": "8939646d-6eb4-4fcc-bb19-6ac3346de241",
  "bootstrapAuthorizationRequestUrl": "openid4vp://authorize?client_id=verifier2&request_uri=http%3A%2F%2Flocalhost%3A7003%2Fverification-session%2F%2F8939646d-6eb4-4fcc-bb19-6ac3346de241%2Frequest",
  "fullAuthorizationRequestUrl": "openid4vp://authorize?response_type=vp_token&client_id=verifier2&state=29026a29-431d-4386-a3c3-4ee48edb0301&response_mode=direct_post&nonce=6d80e6b5-cc19-4dde-b944-a313a1965fed&response_uri=http%3A%2F%2Flocalhost%3A7003%2Fverification-session%2F%2F8939646d-6eb4-4fcc-bb19-6ac3346de241%2Fresponse&dcql_query=%7B%22credentials%22%3A%5B%7B%22id%22%3A%22my_photoid%22%2C%22format%22%3A%22mso_mdoc%22%2C%22meta%22%3A%7B%22doctype_value%22%3A%22org.iso.23220.photoid.1%22%7D%2C%22claims%22%3A%5B%7B%22path%22%3A%5B%22org.iso.18013.5.1%22%2C%22family_name_unicode%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.18013.5.1%22%2C%22given_name_unicode%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.18013.5.1%22%2C%22issuing_authority_unicode%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.18013.5.1%22%2C%22resident_postal_code%22%5D%2C%22values%22%3A%5B1180%2C1190%2C1200%2C1210%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.18013.5.1%22%2C%22issuing_country%22%5D%2C%22values%22%3A%5B%22AT%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.23220.photoid.1%22%2C%22person_id%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.23220.photoid.1%22%2C%22resident_street%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.23220.photoid.1%22%2C%22administrative_number%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.23220.photoid.1%22%2C%22travel_document_number%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.23220.dtc.1%22%2C%22dtc_version%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.23220.dtc.1%22%2C%22dtc_dg1%22%5D%7D%5D%7D%5D%7D&client_metadata=%7B%22client_name%22%3A%22Verifier2%22%2C%22logo_uri%22%3A%22https%3A%2F%2Fimages.squarespace-cdn.com%2Fcontent%2Fv1%2F609c0ddf94bcc0278a7cbdb4%2F4d493ccf-c893-4882-925f-fda3256c38f4%2FWalt.id_Logo_transparent.png%22%7D"
}
```
The created verification session request URL is contained in the response, this is what is then
provided to the wallet.



#### 1.1.2. Present to Verifier with wallet
Use the verification request URL generated with the Verifier to call the `/v1/{wallet}/wallet-service-api/credentials/present` endpoint:

```shell
curl -X 'POST' \
  'https://waltid.enterprise.mdoc-test.waltid.cloud/v1/waltid.test.wallet/wallet-service-api/credentials/present' \
  -H 'Authorization: Bearer eyJhbGciOiJFZERTQSJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTExMTEtMTExMS0wMDAwMDAwMDAwMDAiLCJzZXNzaW9uIjoiMDM0YzgwYWUtZGI5Zi00ODJjLWI1YTgtOTk5MjhiZmI4ZDkwIiwiZXhwIjoxNzU4ODk1MDI0fQ.5GeGe2ogixj50gqZOMjLOfTCoaig_814cLkhF82-cyMzFYb16Jqz6WMgFAkFWUaG9MXmNcg8mp4P_IrbMzi_Bw' \
  -H 'accept: */*' \
  -H 'Content-Type: application/json' \
  -d '{
  "requestUrl": "openid4vp://authorize?client_id=verifier2&request_uri=http%3A%2F%2Flocalhost%3A7003%2Fverification-session%2F%2F8939646d-6eb4-4fcc-bb19-6ac3346de241%2Frequest",
  "keyReference": "waltid.test.kms.wallet_key"
}'
```
The wallet will then automatically figure out from the connected credential stores which credentials
match the requested DCQL Query of the provided Authorization Request, including the claims
specified within the DCQL Query. In the case of a DCQL query for a credential type that supports
selective disclosure (this includes SD-JWT VC and mdocs/ISO credentials), the wallet will also
figure out what claims have to be presented with selective disclosure so that the Relying Party
can receive the requested claims for the credential.

Response:
```json
{
  "transmission_success": true,
  "verifier_response": {
    "status": "received",
    "message": "Presentation received and is being processed."
  }
}
```

The wallet tells us with `transmission_success`: `true` that the transmission from the wallet to
the Relying Party (Verifier) was successful. In addition, the response replied by the Verifier
can also be seen in `verifier_response`.

#### 1.1.3. View presentation result in Verifier

Now that the wallet has presented the Photo ID to the Verifier, we can inquire the Verifier
about the verification status with the `GET /verification-session/{session}/info` endpoint.

```shell
curl -X 'GET' \
  'https://verifier.mdoc-test.waltid.cloud/verification-session/8939646d-6eb4-4fcc-bb19-6ac3346de241/info' \
  -H 'accept: application/json'
```

Response:
```json
{
  "id": "8939646d-6eb4-4fcc-bb19-6ac3346de241",
  "creationDate": "2025-09-19T07:05:58.099914187Z",
  "expirationDate": "2025-09-19T07:10:58.099914187Z",
  "retentionDate": "2035-09-19T07:05:58.099914187Z",
  "status": "SUCCESSFUL",
  "attempted": true,
  "reattemptable": true,
  "bootstrapAuthorizationRequest": {
    "client_id": "verifier2",
    "request_uri": "http://localhost:7003/verification-session//8939646d-6eb4-4fcc-bb19-6ac3346de241/request"
  },
  "bootstrapAuthorizationRequestUrl": "openid4vp://authorize?client_id=verifier2&request_uri=http%3A%2F%2Flocalhost%3A7003%2Fverification-session%2F%2F8939646d-6eb4-4fcc-bb19-6ac3346de241%2Frequest",
  "authorizationRequest": {
    "response_type": "vp_token",
    "client_id": "verifier2",
    "state": "29026a29-431d-4386-a3c3-4ee48edb0301",
    "response_mode": "direct_post",
    "nonce": "6d80e6b5-cc19-4dde-b944-a313a1965fed",
    "response_uri": "http://localhost:7003/verification-session//8939646d-6eb4-4fcc-bb19-6ac3346de241/response",
    "dcql_query": {
      "credentials": [
        {
          "id": "my_photoid",
          "format": "mso_mdoc",
          "multiple": false,
          "meta": {
            "doctype_value": "org.iso.23220.photoid.1"
          },
          "require_cryptographic_holder_binding": true,
          "claims": [
            {
              "path": [
                "org.iso.18013.5.1",
                "family_name_unicode"
              ]
            },
            {
              "path": [
                "org.iso.18013.5.1",
                "given_name_unicode"
              ]
            },
            {
              "path": [
                "org.iso.18013.5.1",
                "issuing_authority_unicode"
              ]
            },
            {
              "path": [
                "org.iso.18013.5.1",
                "resident_postal_code"
              ],
              "values": [
                1180,
                1190,
                1200,
                1210
              ]
            },
            {
              "path": [
                "org.iso.18013.5.1",
                "issuing_country"
              ],
              "values": [
                "AT"
              ]
            },
            {
              "path": [
                "org.iso.23220.photoid.1",
                "person_id"
              ]
            },
            {
              "path": [
                "org.iso.23220.photoid.1",
                "resident_street"
              ]
            },
            {
              "path": [
                "org.iso.23220.photoid.1",
                "administrative_number"
              ]
            },
            {
              "path": [
                "org.iso.23220.photoid.1",
                "travel_document_number"
              ]
            },
            {
              "path": [
                "org.iso.23220.dtc.1",
                "dtc_version"
              ]
            },
            {
              "path": [
                "org.iso.23220.dtc.1",
                "dtc_dg1"
              ]
            }
          ]
        }
      ]
    },
    "client_metadata": {
      "client_name": "Verifier2",
      "logo_uri": "https://images.squarespace-cdn.com/content/v1/609c0ddf94bcc0278a7cbdb4/4d493ccf-c893-4882-925f-fda3256c38f4/Walt.id_Logo_transparent.png"
    }
  },
  "authorizationRequestUrl": "openid4vp://authorize?response_type=vp_token&client_id=verifier2&state=29026a29-431d-4386-a3c3-4ee48edb0301&response_mode=direct_post&nonce=6d80e6b5-cc19-4dde-b944-a313a1965fed&response_uri=http%3A%2F%2Flocalhost%3A7003%2Fverification-session%2F%2F8939646d-6eb4-4fcc-bb19-6ac3346de241%2Fresponse&dcql_query=%7B%22credentials%22%3A%5B%7B%22id%22%3A%22my_photoid%22%2C%22format%22%3A%22mso_mdoc%22%2C%22meta%22%3A%7B%22doctype_value%22%3A%22org.iso.23220.photoid.1%22%7D%2C%22claims%22%3A%5B%7B%22path%22%3A%5B%22org.iso.18013.5.1%22%2C%22family_name_unicode%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.18013.5.1%22%2C%22given_name_unicode%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.18013.5.1%22%2C%22issuing_authority_unicode%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.18013.5.1%22%2C%22resident_postal_code%22%5D%2C%22values%22%3A%5B1180%2C1190%2C1200%2C1210%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.18013.5.1%22%2C%22issuing_country%22%5D%2C%22values%22%3A%5B%22AT%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.23220.photoid.1%22%2C%22person_id%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.23220.photoid.1%22%2C%22resident_street%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.23220.photoid.1%22%2C%22administrative_number%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.23220.photoid.1%22%2C%22travel_document_number%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.23220.dtc.1%22%2C%22dtc_version%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.23220.dtc.1%22%2C%22dtc_dg1%22%5D%7D%5D%7D%5D%7D&client_metadata=%7B%22client_name%22%3A%22Verifier2%22%2C%22logo_uri%22%3A%22https%3A%2F%2Fimages.squarespace-cdn.com%2Fcontent%2Fv1%2F609c0ddf94bcc0278a7cbdb4%2F4d493ccf-c893-4882-925f-fda3256c38f4%2FWalt.id_Logo_transparent.png%22%7D",
  "policies": {
    "vcPolicies": [
      {
        "policy": "signature",
        "id": "signature"
      },
      {
        "policy": "regex",
        "path": "$.['org.iso.23220.dtc.1'].dtc_version",
        "regex": "^(\"[0-9]+\"|-?[0-9]+(\\.[0-9]+)?)$",
        "allowNull": false,
        "id": "regex"
      }
    ],
    "specificVcPolicies": {}
  },
  "policyResults": {
    "vcPolicies": [
      {
        "policy": {
          "policy": "signature",
          "id": "signature"
        },
        "success": true,
        "result": {
          "verification_result": true,
          "signed_credential": "o2d2ZXJzaW9uYzEuMGlkb2N1bWVudHOBo2dkb2NUeXBld29yZy5pc28uMjMyMjAucGhvdG9pZC4xbGlzc3VlclNpZ25lZKJqbmFtZVNwYWNlc6Nxb3JnLmlzby4xODAxMy41LjGF2BhYWqRoZGlnZXN0SUQAZnJhbmRvbVBw8zvZ2SDHWLQKWmewsDAZcWVsZW1lbnRJZGVudGlmaWVyc2ZhbWlseV9uYW1lX3VuaWNvZGVsZWxlbWVudFZhbHVlY0RvZdgYWFqkaGRpZ2VzdElEAWZyYW5kb21Q88V1EGykbJwv-YUaZiGM2HFlbGVtZW50SWRlbnRpZmllcnJnaXZlbl9uYW1lX3VuaWNvZGVsZWxlbWVudFZhbHVlZEpvaG7YGFhppGhkaWdlc3RJRAVmcmFuZG9tUIPdz7j6iiSeP-2ROd1weyRxZWxlbWVudElkZW50aWZpZXJ4GWlzc3VpbmdfYXV0aG9yaXR5X3VuaWNvZGVsZWxlbWVudFZhbHVla0xQRCBXaWVuIDIy2BhYWqRoZGlnZXN0SUQOZnJhbmRvbVCRoStTSp4cLKgWNDMZTX6-cWVsZW1lbnRJZGVudGlmaWVydHJlc2lkZW50X3Bvc3RhbF9jb2RlbGVsZW1lbnRWYWx1ZRkEptgYWFWkaGRpZ2VzdElEBmZyYW5kb21Q2sPFLqEkZ-f6UE0Gf4GaPnFlbGVtZW50SWRlbnRpZmllcm9pc3N1aW5nX2NvdW50cnlsZWxlbWVudFZhbHVlYkFUd29yZy5pc28uMjMyMjAucGhvdG9pZC4xhNgYWFSkaGRpZ2VzdElEAGZyYW5kb21QrhGF7mMF6ImUJ0BXP4bJg3FlbGVtZW50SWRlbnRpZmllcmlwZXJzb25faWRsZWxlbWVudFZhbHVlZ0FUMTIzNDXYGFhepGhkaWdlc3RJRAVmcmFuZG9tUGlUt8hGrPJ4qLn3Se-3j-5xZWxlbWVudElkZW50aWZpZXJvcmVzaWRlbnRfc3RyZWV0bGVsZW1lbnRWYWx1ZWtQw7xjaGxnYXNzZdgYWGKkaGRpZ2VzdElEBGZyYW5kb21QPJNttcEtGwWDB_WuKRxGCnFlbGVtZW50SWRlbnRpZmllcnVhZG1pbmlzdHJhdGl2ZV9udW1iZXJsZWxlbWVudFZhbHVlaUFUQVRBVDEyM9gYWF6kaGRpZ2VzdElEB2ZyYW5kb21QvkFbBzf7oZMXov_x2dFYynFlbGVtZW50SWRlbnRpZmllcnZ0cmF2ZWxfZG9jdW1lbnRfbnVtYmVybGVsZW1lbnRWYWx1ZRoAESAec29yZy5pc28uMjMyMjAuZHRjLjGC2BhYT6RoZGlnZXN0SUQAZnJhbmRvbVAJV3YDsAAtPsJP5sNUMy1UcWVsZW1lbnRJZGVudGlmaWVya2R0Y192ZXJzaW9ubGVsZW1lbnRWYWx1ZQHYGFikpGhkaWdlc3RJRAFmcmFuZG9tUEbdj4Mery-O1iBhi4mLo4BxZWxlbWVudElkZW50aWZpZXJnZHRjX2RnMWxlbGVtZW50VmFsdWV4WFA8QVVURE9FPDxKT0hOPDw8PDw8PDw8PDw8PDw8PDw8PDw8PDw8PDw8PDw8MDAxMTIyMzM0MEFVVDIwMDEyMDdNMzAwOTEyOTw8PDw8PDw8PDw8PDw8MDZqaXNzdWVyQXV0aIRDoQEmoRghWQINMIICCTCCAbCgAwIBAgIUfqyiArJZoX7M61_473UAVi2_UpgwCgYIKoZIzj0EAwIwKDELMAkGA1UEBhMCQVQxGTAXBgNVBAMMEFdhbHRpZCBUZXN0IElBQ0EwHhcNMjUwNjAyMDY0MTEzWhcNMjYwOTAyMDY0MTEzWjAzMQswCQYDVQQGEwJBVDEkMCIGA1UEAwwbV2FsdGlkIFRlc3QgRG9jdW1lbnQgU2lnbmVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5Xp3CFQDMrMpR_SQ0jt_jTOqExk1PRzjQ79aKpIsJM1mqOBrDCBqTAfBgNVHSMEGDAWgBTxCn2nWMrE70qXb614U14BweY2azAdBgNVHQ4EFgQUx5qkOLC4lpl1xpYZGmF9HLxtp0gwDgYDVR0PAQH_BAQDAgeAMBoGA1UdEgQTMBGGD2h0dHBzOi8vd2FsdC5pZDAVBgNVHSUBAf8ECzAJBgcogYxdBQECMCQGA1UdHwQdMBswGaAXoBWGE2h0dHBzOi8vd2FsdC5pZC9jcmwwCgYIKoZIzj0EAwIDRwAwRAIgHTap3c6yCUNhDVfZWBPMKj9dCWZbrME03kh9NJTbw1ECIAvVvuGll9O21eR16SkJHHAA1pPcovhcTvF9fz9cc66MWQXq2BhZBeWmZ3ZlcnNpb25jMS4wb2RpZ2VzdEFsZ29yaXRobWdTSEEtMjU2bHZhbHVlRGlnZXN0c6Nxb3JnLmlzby4xODAxMy41LjG1AFggrqWS3e8q63Q4HKd1ZjZRmLxcpICibLz9QYrn0NJjZ6MBWCDabRghjuzr8IVHzUn4od5IuICz7_1RxSUK9KPavSHUZAJYIEJ_Xy7_HiULILBY4mj0QQBbzgjEBUdeChl0TNsM_hbpA1ggPz83B-s23IBHEiOqcxUG7ikYVKbH1d77GqrItU0hyLgEWCAxUI7IyS7hXhfTTHS72YlApBJwA91V7iL1evGFUUtIFgVYIOeuxQ8KLZPquAJCYgRIVPXWVCqHHWCu0swZHnrVW2HwBlgghhOGkx0qs3HOzKMbCQnpk0zONfju1m0A6FZngYXGvBMHWCDm-ByV8OPRXKasucwdFEttbeMEvAfn1hD9dt5ykbm78ghYICfgZC9bnMS97m7Q9gXENgugGaA9RVJp8Jm2JjyyTfwTCVgg255PKEOZo0xLD4Qs0GbwhP3yVu_LMZSHM-FTk14wrvwKWCAX1lwNuDMaet5fwVEMlCfz1mUsQjSJnU5NaXaPvsHznAtYID9B0FBdSsWybCtPwitJb5cT9dbT_9D4iXgQ11q-ndEjDFggz1LvOXwz0ZfGbdy_Y41wrbQ7Ujd_I-OUy6ZXDkAlHS4NWCBzzW8Df3h7EE2IdgtWajT_wpGiUE3TKCoreS8uE607_w5YIBRmlq8GxemaxvClxhptP57eiqoO82ycLn02bq3xrPJGD1ggJl5YJRKhy7dy80bw1Qr1ifrzD3nXyXaNfJQls6AnIPwQWCBFBJ_SnCEPAJp38djw8ZPN3VZ4v271RJ0YZoQTf4zF1RFYICYR9UKloWsrHCkRBS0WsmJvvy-pjtIA1k7hxFGLWMeVElgg9nwkP3G7mzkjllROqcGfD4AAUYfWCCeX_bP0X6E5LNsTWCCMlbugK7hH5B9cCe3ISHHYTrHsVTjLnS-FxmE-OkP_FxRYIAwz-LpO07HIRUS2cFJ--9i87BPyODWDYUerfiLjeSU2d29yZy5pc28uMjMyMjAucGhvdG9pZC4xqQBYIKyv5MZB3px3zZ7ywtAIwowuPrTYfp2OyVzFlD2cq3PBAVggJzgW7nOhRbV_8_ejW7FnFpNegyp3vINWcgzT3unekDwCWCDh9HUFwiMdmGDlivzvmSJYesh42Fmvug5cZI0ez1R5EwNYIGq6qr2uFMugaRTu9NTzP6srFAbkLeLnkmjfw6EKJsoFBFggRWEVHxjtG4v9XMr92lvteTKWt94xZqWMTVVyJT7IrLkFWCDezRcjlwHSe3iz3UunSH-fPyZwpkKS6euQIjXb09yRBwZYIBeuMG6BPklt95XcqROCo9VeYh2wnkpdzX39i4wEanN3B1ggo1beUr99YjP8jADxJJUKwv4j5c7ockvaaWAczHGdvhMIWCC9_w7Q2gizwv-vbd_IbDZ4XCtiHTPFs5Fnqyvgox41AHNvcmcuaXNvLjIzMjIwLmR0Yy4xogBYIMiDNqVyPp2VWKEMV2Yt63-9fJo_RFYmOew8IKrCp-p2AVggXlUGymiqYUXVMCW6hMn9dMzkvMUi6v1in4xar1iDa6htZGV2aWNlS2V5SW5mb6FpZGV2aWNlS2V5pAECIAEhWCB5NPZZ3OWY5YEi12BKayoHX8E_r3B-7A57Uhi9pjVZ0iJYIMGzrtxj9NiYjlSEUP7n1i0TTpgwerHmAGmwprBgX6_-Z2RvY1R5cGV3b3JnLmlzby4yMzIyMC5waG90b2lkLjFsdmFsaWRpdHlJbmZvo2ZzaWduZWTAeB4yMDI1LTA5LTE5VDA2OjE4OjA4LjM1Njg5MDM1MlppdmFsaWRGcm9twHgeMjAyNS0wOS0xOVQwNjoxODowOC4zNTY4OTExNzNaanZhbGlkVW50aWzAeB4yMDI2LTA5LTE5VDA2OjE4OjA4LjM1Njg5MTI5NFpYQNhDVZD6RRhxDWuf3GcGcwpGA4cK_BdOq7Jzs5OsQ64YelWQgJ6HQU-n1dzkRH2cjGElPiNH1gV8rj2rimGJ86ZsZGV2aWNlU2lnbmVkompuYW1lU3BhY2Vz2BhBoGpkZXZpY2VBdXRooW9kZXZpY2VTaWduYXR1cmWEQ6EBJqD2WEB9sy7r_33-Ln_DGFf8BIrAk4moljOiblpLpOI5VCys75DkKTUnuLqL6h240JSmZEGWVVBaII1PQvzjm2qXYviNZnN0YXR1cwA",
          "credential_signature": null,
          "verified_data": {
            "org.iso.18013.5.1": {
              "family_name_unicode": "Doe",
              "given_name_unicode": "John",
              "issuing_authority_unicode": "LPD Wien 22",
              "resident_postal_code": 1190,
              "issuing_country": "AT"
            },
            "org.iso.23220.photoid.1": {
              "person_id": "AT12345",
              "resident_street": "Püchlgasse",
              "administrative_number": "ATATAT123",
              "travel_document_number": 1122334
            },
            "org.iso.23220.dtc.1": {
              "dtc_version": 1,
              "dtc_dg1": "P<AUTDOE<<JOHN<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<0011223340AUT2001207M3009129<<<<<<<<<<<<<<06"
            }
          },
          "successful_issuer_public_key": {
            "kty": "EC",
            "crv": "P-256",
            "x": "Pzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5U",
            "y": "6dwhUAzKzKUf0kNI7f40zqhMZNT0c40O_WiqSLCTNZo"
          },
          "successful_issuer_public_key_id": "sW5yv0UmZ3S0dQuUrwlR9I3foREBHHFwXhGJGqGEVf0"
        }
      },
      {
        "policy": {
          "policy": "regex",
          "path": "$.['org.iso.23220.dtc.1'].dtc_version",
          "regex": "^(\"[0-9]+\"|-?[0-9]+(\\.[0-9]+)?)$",
          "allowNull": false,
          "id": "regex"
        },
        "success": true,
        "result": {
          "value": "1",
          "groups": [
            "1",
            "1",
            ""
          ]
        }
      }
    ],
    "specificVcPolicies": {},
    "overallSuccess": true
  },
  "presentedRawData": {
    "vpToken": {
      "my_photoid": [
        "o2d2ZXJzaW9uYzEuMGlkb2N1bWVudHOBo2dkb2NUeXBld29yZy5pc28uMjMyMjAucGhvdG9pZC4xbGlzc3VlclNpZ25lZKJqbmFtZVNwYWNlc6Nxb3JnLmlzby4xODAxMy41LjGF2BhYWqRoZGlnZXN0SUQAZnJhbmRvbVBw8zvZ2SDHWLQKWmewsDAZcWVsZW1lbnRJZGVudGlmaWVyc2ZhbWlseV9uYW1lX3VuaWNvZGVsZWxlbWVudFZhbHVlY0RvZdgYWFqkaGRpZ2VzdElEAWZyYW5kb21Q88V1EGykbJwv-YUaZiGM2HFlbGVtZW50SWRlbnRpZmllcnJnaXZlbl9uYW1lX3VuaWNvZGVsZWxlbWVudFZhbHVlZEpvaG7YGFhppGhkaWdlc3RJRAVmcmFuZG9tUIPdz7j6iiSeP-2ROd1weyRxZWxlbWVudElkZW50aWZpZXJ4GWlzc3VpbmdfYXV0aG9yaXR5X3VuaWNvZGVsZWxlbWVudFZhbHVla0xQRCBXaWVuIDIy2BhYWqRoZGlnZXN0SUQOZnJhbmRvbVCRoStTSp4cLKgWNDMZTX6-cWVsZW1lbnRJZGVudGlmaWVydHJlc2lkZW50X3Bvc3RhbF9jb2RlbGVsZW1lbnRWYWx1ZRkEptgYWFWkaGRpZ2VzdElEBmZyYW5kb21Q2sPFLqEkZ-f6UE0Gf4GaPnFlbGVtZW50SWRlbnRpZmllcm9pc3N1aW5nX2NvdW50cnlsZWxlbWVudFZhbHVlYkFUd29yZy5pc28uMjMyMjAucGhvdG9pZC4xhNgYWFSkaGRpZ2VzdElEAGZyYW5kb21QrhGF7mMF6ImUJ0BXP4bJg3FlbGVtZW50SWRlbnRpZmllcmlwZXJzb25faWRsZWxlbWVudFZhbHVlZ0FUMTIzNDXYGFhepGhkaWdlc3RJRAVmcmFuZG9tUGlUt8hGrPJ4qLn3Se-3j-5xZWxlbWVudElkZW50aWZpZXJvcmVzaWRlbnRfc3RyZWV0bGVsZW1lbnRWYWx1ZWtQw7xjaGxnYXNzZdgYWGKkaGRpZ2VzdElEBGZyYW5kb21QPJNttcEtGwWDB_WuKRxGCnFlbGVtZW50SWRlbnRpZmllcnVhZG1pbmlzdHJhdGl2ZV9udW1iZXJsZWxlbWVudFZhbHVlaUFUQVRBVDEyM9gYWF6kaGRpZ2VzdElEB2ZyYW5kb21QvkFbBzf7oZMXov_x2dFYynFlbGVtZW50SWRlbnRpZmllcnZ0cmF2ZWxfZG9jdW1lbnRfbnVtYmVybGVsZW1lbnRWYWx1ZRoAESAec29yZy5pc28uMjMyMjAuZHRjLjGC2BhYT6RoZGlnZXN0SUQAZnJhbmRvbVAJV3YDsAAtPsJP5sNUMy1UcWVsZW1lbnRJZGVudGlmaWVya2R0Y192ZXJzaW9ubGVsZW1lbnRWYWx1ZQHYGFikpGhkaWdlc3RJRAFmcmFuZG9tUEbdj4Mery-O1iBhi4mLo4BxZWxlbWVudElkZW50aWZpZXJnZHRjX2RnMWxlbGVtZW50VmFsdWV4WFA8QVVURE9FPDxKT0hOPDw8PDw8PDw8PDw8PDw8PDw8PDw8PDw8PDw8PDw8MDAxMTIyMzM0MEFVVDIwMDEyMDdNMzAwOTEyOTw8PDw8PDw8PDw8PDw8MDZqaXNzdWVyQXV0aIRDoQEmoRghWQINMIICCTCCAbCgAwIBAgIUfqyiArJZoX7M61_473UAVi2_UpgwCgYIKoZIzj0EAwIwKDELMAkGA1UEBhMCQVQxGTAXBgNVBAMMEFdhbHRpZCBUZXN0IElBQ0EwHhcNMjUwNjAyMDY0MTEzWhcNMjYwOTAyMDY0MTEzWjAzMQswCQYDVQQGEwJBVDEkMCIGA1UEAwwbV2FsdGlkIFRlc3QgRG9jdW1lbnQgU2lnbmVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5Xp3CFQDMrMpR_SQ0jt_jTOqExk1PRzjQ79aKpIsJM1mqOBrDCBqTAfBgNVHSMEGDAWgBTxCn2nWMrE70qXb614U14BweY2azAdBgNVHQ4EFgQUx5qkOLC4lpl1xpYZGmF9HLxtp0gwDgYDVR0PAQH_BAQDAgeAMBoGA1UdEgQTMBGGD2h0dHBzOi8vd2FsdC5pZDAVBgNVHSUBAf8ECzAJBgcogYxdBQECMCQGA1UdHwQdMBswGaAXoBWGE2h0dHBzOi8vd2FsdC5pZC9jcmwwCgYIKoZIzj0EAwIDRwAwRAIgHTap3c6yCUNhDVfZWBPMKj9dCWZbrME03kh9NJTbw1ECIAvVvuGll9O21eR16SkJHHAA1pPcovhcTvF9fz9cc66MWQXq2BhZBeWmZ3ZlcnNpb25jMS4wb2RpZ2VzdEFsZ29yaXRobWdTSEEtMjU2bHZhbHVlRGlnZXN0c6Nxb3JnLmlzby4xODAxMy41LjG1AFggrqWS3e8q63Q4HKd1ZjZRmLxcpICibLz9QYrn0NJjZ6MBWCDabRghjuzr8IVHzUn4od5IuICz7_1RxSUK9KPavSHUZAJYIEJ_Xy7_HiULILBY4mj0QQBbzgjEBUdeChl0TNsM_hbpA1ggPz83B-s23IBHEiOqcxUG7ikYVKbH1d77GqrItU0hyLgEWCAxUI7IyS7hXhfTTHS72YlApBJwA91V7iL1evGFUUtIFgVYIOeuxQ8KLZPquAJCYgRIVPXWVCqHHWCu0swZHnrVW2HwBlgghhOGkx0qs3HOzKMbCQnpk0zONfju1m0A6FZngYXGvBMHWCDm-ByV8OPRXKasucwdFEttbeMEvAfn1hD9dt5ykbm78ghYICfgZC9bnMS97m7Q9gXENgugGaA9RVJp8Jm2JjyyTfwTCVgg255PKEOZo0xLD4Qs0GbwhP3yVu_LMZSHM-FTk14wrvwKWCAX1lwNuDMaet5fwVEMlCfz1mUsQjSJnU5NaXaPvsHznAtYID9B0FBdSsWybCtPwitJb5cT9dbT_9D4iXgQ11q-ndEjDFggz1LvOXwz0ZfGbdy_Y41wrbQ7Ujd_I-OUy6ZXDkAlHS4NWCBzzW8Df3h7EE2IdgtWajT_wpGiUE3TKCoreS8uE607_w5YIBRmlq8GxemaxvClxhptP57eiqoO82ycLn02bq3xrPJGD1ggJl5YJRKhy7dy80bw1Qr1ifrzD3nXyXaNfJQls6AnIPwQWCBFBJ_SnCEPAJp38djw8ZPN3VZ4v271RJ0YZoQTf4zF1RFYICYR9UKloWsrHCkRBS0WsmJvvy-pjtIA1k7hxFGLWMeVElgg9nwkP3G7mzkjllROqcGfD4AAUYfWCCeX_bP0X6E5LNsTWCCMlbugK7hH5B9cCe3ISHHYTrHsVTjLnS-FxmE-OkP_FxRYIAwz-LpO07HIRUS2cFJ--9i87BPyODWDYUerfiLjeSU2d29yZy5pc28uMjMyMjAucGhvdG9pZC4xqQBYIKyv5MZB3px3zZ7ywtAIwowuPrTYfp2OyVzFlD2cq3PBAVggJzgW7nOhRbV_8_ejW7FnFpNegyp3vINWcgzT3unekDwCWCDh9HUFwiMdmGDlivzvmSJYesh42Fmvug5cZI0ez1R5EwNYIGq6qr2uFMugaRTu9NTzP6srFAbkLeLnkmjfw6EKJsoFBFggRWEVHxjtG4v9XMr92lvteTKWt94xZqWMTVVyJT7IrLkFWCDezRcjlwHSe3iz3UunSH-fPyZwpkKS6euQIjXb09yRBwZYIBeuMG6BPklt95XcqROCo9VeYh2wnkpdzX39i4wEanN3B1ggo1beUr99YjP8jADxJJUKwv4j5c7ockvaaWAczHGdvhMIWCC9_w7Q2gizwv-vbd_IbDZ4XCtiHTPFs5Fnqyvgox41AHNvcmcuaXNvLjIzMjIwLmR0Yy4xogBYIMiDNqVyPp2VWKEMV2Yt63-9fJo_RFYmOew8IKrCp-p2AVggXlUGymiqYUXVMCW6hMn9dMzkvMUi6v1in4xar1iDa6htZGV2aWNlS2V5SW5mb6FpZGV2aWNlS2V5pAECIAEhWCB5NPZZ3OWY5YEi12BKayoHX8E_r3B-7A57Uhi9pjVZ0iJYIMGzrtxj9NiYjlSEUP7n1i0TTpgwerHmAGmwprBgX6_-Z2RvY1R5cGV3b3JnLmlzby4yMzIyMC5waG90b2lkLjFsdmFsaWRpdHlJbmZvo2ZzaWduZWTAeB4yMDI1LTA5LTE5VDA2OjE4OjA4LjM1Njg5MDM1MlppdmFsaWRGcm9twHgeMjAyNS0wOS0xOVQwNjoxODowOC4zNTY4OTExNzNaanZhbGlkVW50aWzAeB4yMDI2LTA5LTE5VDA2OjE4OjA4LjM1Njg5MTI5NFpYQNhDVZD6RRhxDWuf3GcGcwpGA4cK_BdOq7Jzs5OsQ64YelWQgJ6HQU-n1dzkRH2cjGElPiNH1gV8rj2rimGJ86ZsZGV2aWNlU2lnbmVkompuYW1lU3BhY2Vz2BhBoGpkZXZpY2VBdXRooW9kZXZpY2VTaWduYXR1cmWEQ6EBJqD2WEB9sy7r_33-Ln_DGFf8BIrAk4moljOiblpLpOI5VCys75DkKTUnuLqL6h240JSmZEGWVVBaII1PQvzjm2qXYviNZnN0YXR1cwA"
      ]
    },
    "state": "29026a29-431d-4386-a3c3-4ee48edb0301"
  },
  "presentedCredentials": {
    "my_photoid": [
      {
        "type": "vc-mdocs",
        "credentialData": {
          "org.iso.18013.5.1": {
            "family_name_unicode": "Doe",
            "given_name_unicode": "John",
            "issuing_authority_unicode": "LPD Wien 22",
            "resident_postal_code": 1190,
            "issuing_country": "AT"
          },
          "org.iso.23220.photoid.1": {
            "person_id": "AT12345",
            "resident_street": "Püchlgasse",
            "administrative_number": "ATATAT123",
            "travel_document_number": 1122334
          },
          "org.iso.23220.dtc.1": {
            "dtc_version": 1,
            "dtc_dg1": "P<AUTDOE<<JOHN<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<0011223340AUT2001207M3009129<<<<<<<<<<<<<<06"
          }
        },
        "signed": "o2d2ZXJzaW9uYzEuMGlkb2N1bWVudHOBo2dkb2NUeXBld29yZy5pc28uMjMyMjAucGhvdG9pZC4xbGlzc3VlclNpZ25lZKJqbmFtZVNwYWNlc6Nxb3JnLmlzby4xODAxMy41LjGF2BhYWqRoZGlnZXN0SUQAZnJhbmRvbVBw8zvZ2SDHWLQKWmewsDAZcWVsZW1lbnRJZGVudGlmaWVyc2ZhbWlseV9uYW1lX3VuaWNvZGVsZWxlbWVudFZhbHVlY0RvZdgYWFqkaGRpZ2VzdElEAWZyYW5kb21Q88V1EGykbJwv-YUaZiGM2HFlbGVtZW50SWRlbnRpZmllcnJnaXZlbl9uYW1lX3VuaWNvZGVsZWxlbWVudFZhbHVlZEpvaG7YGFhppGhkaWdlc3RJRAVmcmFuZG9tUIPdz7j6iiSeP-2ROd1weyRxZWxlbWVudElkZW50aWZpZXJ4GWlzc3VpbmdfYXV0aG9yaXR5X3VuaWNvZGVsZWxlbWVudFZhbHVla0xQRCBXaWVuIDIy2BhYWqRoZGlnZXN0SUQOZnJhbmRvbVCRoStTSp4cLKgWNDMZTX6-cWVsZW1lbnRJZGVudGlmaWVydHJlc2lkZW50X3Bvc3RhbF9jb2RlbGVsZW1lbnRWYWx1ZRkEptgYWFWkaGRpZ2VzdElEBmZyYW5kb21Q2sPFLqEkZ-f6UE0Gf4GaPnFlbGVtZW50SWRlbnRpZmllcm9pc3N1aW5nX2NvdW50cnlsZWxlbWVudFZhbHVlYkFUd29yZy5pc28uMjMyMjAucGhvdG9pZC4xhNgYWFSkaGRpZ2VzdElEAGZyYW5kb21QrhGF7mMF6ImUJ0BXP4bJg3FlbGVtZW50SWRlbnRpZmllcmlwZXJzb25faWRsZWxlbWVudFZhbHVlZ0FUMTIzNDXYGFhepGhkaWdlc3RJRAVmcmFuZG9tUGlUt8hGrPJ4qLn3Se-3j-5xZWxlbWVudElkZW50aWZpZXJvcmVzaWRlbnRfc3RyZWV0bGVsZW1lbnRWYWx1ZWtQw7xjaGxnYXNzZdgYWGKkaGRpZ2VzdElEBGZyYW5kb21QPJNttcEtGwWDB_WuKRxGCnFlbGVtZW50SWRlbnRpZmllcnVhZG1pbmlzdHJhdGl2ZV9udW1iZXJsZWxlbWVudFZhbHVlaUFUQVRBVDEyM9gYWF6kaGRpZ2VzdElEB2ZyYW5kb21QvkFbBzf7oZMXov_x2dFYynFlbGVtZW50SWRlbnRpZmllcnZ0cmF2ZWxfZG9jdW1lbnRfbnVtYmVybGVsZW1lbnRWYWx1ZRoAESAec29yZy5pc28uMjMyMjAuZHRjLjGC2BhYT6RoZGlnZXN0SUQAZnJhbmRvbVAJV3YDsAAtPsJP5sNUMy1UcWVsZW1lbnRJZGVudGlmaWVya2R0Y192ZXJzaW9ubGVsZW1lbnRWYWx1ZQHYGFikpGhkaWdlc3RJRAFmcmFuZG9tUEbdj4Mery-O1iBhi4mLo4BxZWxlbWVudElkZW50aWZpZXJnZHRjX2RnMWxlbGVtZW50VmFsdWV4WFA8QVVURE9FPDxKT0hOPDw8PDw8PDw8PDw8PDw8PDw8PDw8PDw8PDw8PDw8MDAxMTIyMzM0MEFVVDIwMDEyMDdNMzAwOTEyOTw8PDw8PDw8PDw8PDw8MDZqaXNzdWVyQXV0aIRDoQEmoRghWQINMIICCTCCAbCgAwIBAgIUfqyiArJZoX7M61_473UAVi2_UpgwCgYIKoZIzj0EAwIwKDELMAkGA1UEBhMCQVQxGTAXBgNVBAMMEFdhbHRpZCBUZXN0IElBQ0EwHhcNMjUwNjAyMDY0MTEzWhcNMjYwOTAyMDY0MTEzWjAzMQswCQYDVQQGEwJBVDEkMCIGA1UEAwwbV2FsdGlkIFRlc3QgRG9jdW1lbnQgU2lnbmVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5Xp3CFQDMrMpR_SQ0jt_jTOqExk1PRzjQ79aKpIsJM1mqOBrDCBqTAfBgNVHSMEGDAWgBTxCn2nWMrE70qXb614U14BweY2azAdBgNVHQ4EFgQUx5qkOLC4lpl1xpYZGmF9HLxtp0gwDgYDVR0PAQH_BAQDAgeAMBoGA1UdEgQTMBGGD2h0dHBzOi8vd2FsdC5pZDAVBgNVHSUBAf8ECzAJBgcogYxdBQECMCQGA1UdHwQdMBswGaAXoBWGE2h0dHBzOi8vd2FsdC5pZC9jcmwwCgYIKoZIzj0EAwIDRwAwRAIgHTap3c6yCUNhDVfZWBPMKj9dCWZbrME03kh9NJTbw1ECIAvVvuGll9O21eR16SkJHHAA1pPcovhcTvF9fz9cc66MWQXq2BhZBeWmZ3ZlcnNpb25jMS4wb2RpZ2VzdEFsZ29yaXRobWdTSEEtMjU2bHZhbHVlRGlnZXN0c6Nxb3JnLmlzby4xODAxMy41LjG1AFggrqWS3e8q63Q4HKd1ZjZRmLxcpICibLz9QYrn0NJjZ6MBWCDabRghjuzr8IVHzUn4od5IuICz7_1RxSUK9KPavSHUZAJYIEJ_Xy7_HiULILBY4mj0QQBbzgjEBUdeChl0TNsM_hbpA1ggPz83B-s23IBHEiOqcxUG7ikYVKbH1d77GqrItU0hyLgEWCAxUI7IyS7hXhfTTHS72YlApBJwA91V7iL1evGFUUtIFgVYIOeuxQ8KLZPquAJCYgRIVPXWVCqHHWCu0swZHnrVW2HwBlgghhOGkx0qs3HOzKMbCQnpk0zONfju1m0A6FZngYXGvBMHWCDm-ByV8OPRXKasucwdFEttbeMEvAfn1hD9dt5ykbm78ghYICfgZC9bnMS97m7Q9gXENgugGaA9RVJp8Jm2JjyyTfwTCVgg255PKEOZo0xLD4Qs0GbwhP3yVu_LMZSHM-FTk14wrvwKWCAX1lwNuDMaet5fwVEMlCfz1mUsQjSJnU5NaXaPvsHznAtYID9B0FBdSsWybCtPwitJb5cT9dbT_9D4iXgQ11q-ndEjDFggz1LvOXwz0ZfGbdy_Y41wrbQ7Ujd_I-OUy6ZXDkAlHS4NWCBzzW8Df3h7EE2IdgtWajT_wpGiUE3TKCoreS8uE607_w5YIBRmlq8GxemaxvClxhptP57eiqoO82ycLn02bq3xrPJGD1ggJl5YJRKhy7dy80bw1Qr1ifrzD3nXyXaNfJQls6AnIPwQWCBFBJ_SnCEPAJp38djw8ZPN3VZ4v271RJ0YZoQTf4zF1RFYICYR9UKloWsrHCkRBS0WsmJvvy-pjtIA1k7hxFGLWMeVElgg9nwkP3G7mzkjllROqcGfD4AAUYfWCCeX_bP0X6E5LNsTWCCMlbugK7hH5B9cCe3ISHHYTrHsVTjLnS-FxmE-OkP_FxRYIAwz-LpO07HIRUS2cFJ--9i87BPyODWDYUerfiLjeSU2d29yZy5pc28uMjMyMjAucGhvdG9pZC4xqQBYIKyv5MZB3px3zZ7ywtAIwowuPrTYfp2OyVzFlD2cq3PBAVggJzgW7nOhRbV_8_ejW7FnFpNegyp3vINWcgzT3unekDwCWCDh9HUFwiMdmGDlivzvmSJYesh42Fmvug5cZI0ez1R5EwNYIGq6qr2uFMugaRTu9NTzP6srFAbkLeLnkmjfw6EKJsoFBFggRWEVHxjtG4v9XMr92lvteTKWt94xZqWMTVVyJT7IrLkFWCDezRcjlwHSe3iz3UunSH-fPyZwpkKS6euQIjXb09yRBwZYIBeuMG6BPklt95XcqROCo9VeYh2wnkpdzX39i4wEanN3B1ggo1beUr99YjP8jADxJJUKwv4j5c7ockvaaWAczHGdvhMIWCC9_w7Q2gizwv-vbd_IbDZ4XCtiHTPFs5Fnqyvgox41AHNvcmcuaXNvLjIzMjIwLmR0Yy4xogBYIMiDNqVyPp2VWKEMV2Yt63-9fJo_RFYmOew8IKrCp-p2AVggXlUGymiqYUXVMCW6hMn9dMzkvMUi6v1in4xar1iDa6htZGV2aWNlS2V5SW5mb6FpZGV2aWNlS2V5pAECIAEhWCB5NPZZ3OWY5YEi12BKayoHX8E_r3B-7A57Uhi9pjVZ0iJYIMGzrtxj9NiYjlSEUP7n1i0TTpgwerHmAGmwprBgX6_-Z2RvY1R5cGV3b3JnLmlzby4yMzIyMC5waG90b2lkLjFsdmFsaWRpdHlJbmZvo2ZzaWduZWTAeB4yMDI1LTA5LTE5VDA2OjE4OjA4LjM1Njg5MDM1MlppdmFsaWRGcm9twHgeMjAyNS0wOS0xOVQwNjoxODowOC4zNTY4OTExNzNaanZhbGlkVW50aWzAeB4yMDI2LTA5LTE5VDA2OjE4OjA4LjM1Njg5MTI5NFpYQNhDVZD6RRhxDWuf3GcGcwpGA4cK_BdOq7Jzs5OsQ64YelWQgJ6HQU-n1dzkRH2cjGElPiNH1gV8rj2rimGJ86ZsZGV2aWNlU2lnbmVkompuYW1lU3BhY2Vz2BhBoGpkZXZpY2VBdXRooW9kZXZpY2VTaWduYXR1cmWEQ6EBJqD2WEB9sy7r_33-Ln_DGFf8BIrAk4moljOiblpLpOI5VCys75DkKTUnuLqL6h240JSmZEGWVVBaII1PQvzjm2qXYviNZnN0YXR1cwA",
        "docType": "org.iso.23220.photoid.1",
        "issuer": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2IiwieCI6IlB6cDZlVlNBZFhFUnFBcDhxOE91REVobDJJTEdBYW9hUVhUSjJzRDJnNVUiLCJ5IjoiNmR3aFVBekt6S1VmMGtOSTdmNDB6cWhNWk5UMGM0ME9fV2lxU0xDVE5abyJ9",
        "format": "mso_mdoc"
      }
    ]
  }
}
```

In the above verification session information response we can find the full infos about the session status:
- `attempted`: `true` -> the verification session was presented to (by the wallet)
- `status`: `SUCCESSFUL` -> the verification session was presented, the presentation was successfully validated, the selected verification policies were successfuly run
- `authorizationRequest` & `authorizationRequest.dcql_query` -> the information that was provided to the wallet for the presentation
- `policies` -> the policies to be run
- `policyResults` -> the results of the policies after they were executed (if the verification session was presented to)
- `presentedRawData.vpToken` -> the vpToken sent by the wallet
- `presentedCredentials` -> the credentials that were presented by the wallet

In our case `presentedCredentials` contains the Photo ID that the wallet presented, with all the
claims that we requested with the DCQL query we created, even spanning different namespaces.



### 1.2. With Enterprise Verifier

#### 1.2.0. Create & configure

`POST /v1/v2/resource-api/services/create`

```json
{
  "type": "verifier2",
  "baseUrl": "https://waltid.enterprise.mdoc-test.waltid.cloud",
  "clientId": "my-client-id",
  "clientMetadata": {
    "client_name": "Verifier2",
    "logo_uri": "https://images.squarespace-cdn.com/content/v1/609c0ddf94bcc0278a7cbdb4/4d493ccf-c893-4882-925f-fda3256c38f4/Walt.id_Logo_transparent.png"
  }
}
```
(set the `baseUrl` to the publicly accessible URL, i.e. the part before /v1/)

->

```bash
curl -X 'POST' \
  'https://waltid.enterprise.mdoc-test.waltid.cloud/v1/v2/resource-api/services/create' \
  -H 'accept: */*' \
  -H 'Content-Type: application/json' \
  -d '{
  "type": "verifier2",
  "baseUrl": "https://waltid.enterprise.mdoc-test.waltid.cloud",
  "clientId": "my-client-id",
  "clientMetadata": {
    "client_name": "Verifier2",
    "logo_uri": "https://images.squarespace-cdn.com/content/v1/609c0ddf94bcc0278a7cbdb4/4d493ccf-c893-4882-925f-fda3256c38f4/Walt.id_Logo_transparent.png"
  }
}'
```

#### 1.2.1. Create verification request

`POST /v1/{target}/verifier2-service-api/verification-session/create`

```json
{
  "dcql_query": {
    "credentials": [
      {
        "id": "my_photoid",
        "format": "mso_mdoc",
        "meta": {
          "doctype_value": "org.iso.23220.photoid.1"
        },
        "claims": [
          { "path": [ "org.iso.18013.5.1", "family_name_unicode" ] },
          { "path": [ "org.iso.18013.5.1", "given_name_unicode" ] },
          { "path": [ "org.iso.18013.5.1", "issuing_authority_unicode" ] },
          {
            "path": [ "org.iso.18013.5.1", "resident_postal_code" ],
            "values": [ 1180, 1190, 1200, 1210 ]
          },
          {
            "path": [ "org.iso.18013.5.1", "issuing_country" ],
            "values": [ "AT" ]
          },

          { "path": [ "org.iso.23220.photoid.1", "person_id" ] },
          { "path": [ "org.iso.23220.photoid.1", "resident_street" ] },
          { "path": [ "org.iso.23220.photoid.1", "administrative_number" ] },
          { "path": [ "org.iso.23220.photoid.1", "travel_document_number" ] },

          { "path": [ "org.iso.23220.dtc.1", "dtc_version" ] },
          { "path": [ "org.iso.23220.dtc.1", "dtc_dg1" ] }
        ]
      }
    ]
  },
  "policies": {
    "vcPolicies": [
      { "policy": "signature" },
      { "policy": "regex", "path": "$.['org.iso.23220.dtc.1'].dtc_version", "regex": "^(\"[0-9]+\"|-?[0-9]+(\\.[0-9]+)?)$" }
    ]
  }
}
```

->

```bash
curl -X 'POST' \
  'https://waltid.enterprise.mdoc-test.waltid.cloud/v1/waltid.v2/verifier2-service-api/verification-session/create' \
  -H 'accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
  "dcql_query": {
    "credentials": [
      {
        "id": "my_photoid",
        "format": "mso_mdoc",
        "meta": {
          "doctype_value": "org.iso.23220.photoid.1"
        },
        "claims": [
          { "path": [ "org.iso.18013.5.1", "family_name_unicode" ] },
          { "path": [ "org.iso.18013.5.1", "given_name_unicode" ] },
          { "path": [ "org.iso.18013.5.1", "issuing_authority_unicode" ] },
          {
            "path": [ "org.iso.18013.5.1", "resident_postal_code" ],
            "values": [ 1180, 1190, 1200, 1210 ]
          },
          {
            "path": [ "org.iso.18013.5.1", "issuing_country" ],
            "values": [ "AT" ]
          },

          { "path": [ "org.iso.23220.photoid.1", "person_id" ] },
          { "path": [ "org.iso.23220.photoid.1", "resident_street" ] },
          { "path": [ "org.iso.23220.photoid.1", "administrative_number" ] },
          { "path": [ "org.iso.23220.photoid.1", "travel_document_number" ] },

          { "path": [ "org.iso.23220.dtc.1", "dtc_version" ] },
          { "path": [ "org.iso.23220.dtc.1", "dtc_dg1" ] }
        ]
      }
    ]
  },
  "policies": {
    "vcPolicies": [
      { "policy": "signature" },
      { "policy": "regex", "path": "$.['\''org.iso.23220.dtc.1'\''].dtc_version", "regex": "^(\"[0-9]+\"|-?[0-9]+(\\.[0-9]+)?)$" }
    ]
  }
}'
```

Response:

```json
{
  "sessionId": "4ec07883-f1d0-4346-8013-cad30e862b56",
  "bootstrapAuthorizationRequestUrl": "openid4vp://authorize?client_id=my-client-id&request_uri=https%3A%2F%2Fwaltid.enterprise.mdoc-test.waltid.cloud%2Fv1%2Fwaltid.v2%2Fverifier2-service-api%2F4ec07883-f1d0-4346-8013-cad30e862b56%2Frequest",
  "fullAuthorizationRequestUrl": "openid4vp://authorize?response_type=vp_token&client_id=my-client-id&state=5c2c4802-be15-4441-9039-04bed76e3607&response_mode=direct_post&nonce=92698b40-f50d-4be1-97b4-dd1dba9bfe4a&response_uri=https%3A%2F%2Fwaltid.enterprise.mdoc-test.waltid.cloud%2Fv1%2Fwaltid.v2%2Fverifier2-service-api%2F4ec07883-f1d0-4346-8013-cad30e862b56%2Fresponse&dcql_query=%7B%22credentials%22%3A%5B%7B%22id%22%3A%22my_photoid%22%2C%22format%22%3A%22mso_mdoc%22%2C%22meta%22%3A%7B%22doctype_value%22%3A%22org.iso.23220.photoid.1%22%7D%2C%22claims%22%3A%5B%7B%22path%22%3A%5B%22org.iso.18013.5.1%22%2C%22family_name_unicode%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.18013.5.1%22%2C%22given_name_unicode%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.18013.5.1%22%2C%22issuing_authority_unicode%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.18013.5.1%22%2C%22resident_postal_code%22%5D%2C%22values%22%3A%5B1180%2C1190%2C1200%2C1210%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.18013.5.1%22%2C%22issuing_country%22%5D%2C%22values%22%3A%5B%22AT%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.23220.photoid.1%22%2C%22person_id%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.23220.photoid.1%22%2C%22resident_street%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.23220.photoid.1%22%2C%22administrative_number%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.23220.photoid.1%22%2C%22travel_document_number%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.23220.dtc.1%22%2C%22dtc_version%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.23220.dtc.1%22%2C%22dtc_dg1%22%5D%7D%5D%7D%5D%7D&client_metadata=%7B%22client_name%22%3A%22Verifier2%22%2C%22logo_uri%22%3A%22https%3A%2F%2Fimages.squarespace-cdn.com%2Fcontent%2Fv1%2F609c0ddf94bcc0278a7cbdb4%2F4d493ccf-c893-4882-925f-fda3256c38f4%2FWalt.id_Logo_transparent.png%22%7D",
  "creationTarget": "waltid.v2.4ec07883-f1d0-4346-8013-cad30e862b56"
}
```

(note the creationTarget in the Enterprise)

#### 1.2.2. Present to Verifier with wallet

`POST /v1/{target}/wallet-service-api/credentials/present`

```json
{
  "requestUrl": "openid4vp://authorize?client_id=my-client-id&request_uri=https%3A%2F%2Fwaltid.enterprise.mdoc-test.waltid.cloud%2Fv1%2Fwaltid.v2%2Fverifier2-service-api%2F4ec07883-f1d0-4346-8013-cad30e862b56%2Frequest",
  "keyReference": "waltid.test.kms.wallet_key"
}
```

->

```bash
curl -X 'POST' \
  'https://waltid.enterprise.mdoc-test.waltid.cloud/v1/waltid.test.wallet/wallet-service-api/credentials/present' \
  -H 'accept: */*' \
  -H 'Content-Type: application/json' \
  -d '{
  "requestUrl": "openid4vp://authorize?client_id=my-client-id&request_uri=https%3A%2F%2Fwaltid.enterprise.mdoc-test.waltid.cloud%2Fv1%2Fwaltid.v2%2Fverifier2-service-api%2F4ec07883-f1d0-4346-8013-cad30e862b56%2Frequest",
  "keyReference": "waltid.test.kms.wallet_key"
}'
```

Response:

```json
{
  "transmission_success": true,
  "verifier_response": {
    "status": "received",
    "message": "Presentation received and is being processed."
  }
}
```

#### 1.2.3. View presentation result in Verifier

`GET /v1/{target}/verifier2-service-api/verification-session/info`
->

```bash
curl -X 'GET' \
  'https://waltid.enterprise.mdoc-test.waltid.cloud/v1/waltid.v2.4ec07883-f1d0-4346-8013-cad30e862b56/verifier2-service-api/verification-session/info' \
  -H 'accept: application/json'
```

Response:

```json
{
  "_id": "waltid.v2.4ec07883-f1d0-4346-8013-cad30e862b56",
  "session": {
    "id": "4ec07883-f1d0-4346-8013-cad30e862b56",
    "creationDate": "2025-09-19T15:22:42.779658776Z",
    "expirationDate": "2025-09-19T15:27:42.779658776Z",
    "retentionDate": "2035-09-19T15:22:42.779658776Z",
    "status": "SUCCESSFUL",
    "attempted": true,
    "reattemptable": true,
    "bootstrapAuthorizationRequest": {
      "response_type": "vp_token",
      "client_id": "my-client-id",
      "request_uri": "https://waltid.enterprise.mdoc-test.waltid.cloud/v1/waltid.v2/verifier2-service-api/4ec07883-f1d0-4346-8013-cad30e862b56/request"
    },
    "bootstrapAuthorizationRequestUrl": "openid4vp://authorize?client_id=my-client-id&request_uri=https%3A%2F%2Fwaltid.enterprise.mdoc-test.waltid.cloud%2Fv1%2Fwaltid.v2%2Fverifier2-service-api%2F4ec07883-f1d0-4346-8013-cad30e862b56%2Frequest",
    "authorizationRequest": {
      "response_type": "vp_token",
      "client_id": "my-client-id",
      "state": "5c2c4802-be15-4441-9039-04bed76e3607",
      "response_mode": "direct_post",
      "nonce": "92698b40-f50d-4be1-97b4-dd1dba9bfe4a",
      "response_uri": "https://waltid.enterprise.mdoc-test.waltid.cloud/v1/waltid.v2/verifier2-service-api/4ec07883-f1d0-4346-8013-cad30e862b56/response",
      "dcql_query": {
        "credentials": [
          {
            "id": "my_photoid",
            "format": "mso_mdoc",
            "multiple": false,
            "meta": {
              "doctype_value": "org.iso.23220.photoid.1"
            },
            "require_cryptographic_holder_binding": true,
            "claims": [
              {
                "path": [
                  "org.iso.18013.5.1",
                  "family_name_unicode"
                ]
              },
              {
                "path": [
                  "org.iso.18013.5.1",
                  "given_name_unicode"
                ]
              },
              {
                "path": [
                  "org.iso.18013.5.1",
                  "issuing_authority_unicode"
                ]
              },
              {
                "path": [
                  "org.iso.18013.5.1",
                  "resident_postal_code"
                ],
                "values": [
                  1180,
                  1190,
                  1200,
                  1210
                ]
              },
              {
                "path": [
                  "org.iso.18013.5.1",
                  "issuing_country"
                ],
                "values": [
                  "AT"
                ]
              },
              {
                "path": [
                  "org.iso.23220.photoid.1",
                  "person_id"
                ]
              },
              {
                "path": [
                  "org.iso.23220.photoid.1",
                  "resident_street"
                ]
              },
              {
                "path": [
                  "org.iso.23220.photoid.1",
                  "administrative_number"
                ]
              },
              {
                "path": [
                  "org.iso.23220.photoid.1",
                  "travel_document_number"
                ]
              },
              {
                "path": [
                  "org.iso.23220.dtc.1",
                  "dtc_version"
                ]
              },
              {
                "path": [
                  "org.iso.23220.dtc.1",
                  "dtc_dg1"
                ]
              }
            ]
          }
        ]
      },
      "client_metadata": {
        "client_name": "Verifier2",
        "logo_uri": "https://images.squarespace-cdn.com/content/v1/609c0ddf94bcc0278a7cbdb4/4d493ccf-c893-4882-925f-fda3256c38f4/Walt.id_Logo_transparent.png"
      }
    },
    "authorizationRequestUrl": "openid4vp://authorize?response_type=vp_token&client_id=my-client-id&state=5c2c4802-be15-4441-9039-04bed76e3607&response_mode=direct_post&nonce=92698b40-f50d-4be1-97b4-dd1dba9bfe4a&response_uri=https%3A%2F%2Fwaltid.enterprise.mdoc-test.waltid.cloud%2Fv1%2Fwaltid.v2%2Fverifier2-service-api%2F4ec07883-f1d0-4346-8013-cad30e862b56%2Fresponse&dcql_query=%7B%22credentials%22%3A%5B%7B%22id%22%3A%22my_photoid%22%2C%22format%22%3A%22mso_mdoc%22%2C%22meta%22%3A%7B%22doctype_value%22%3A%22org.iso.23220.photoid.1%22%7D%2C%22claims%22%3A%5B%7B%22path%22%3A%5B%22org.iso.18013.5.1%22%2C%22family_name_unicode%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.18013.5.1%22%2C%22given_name_unicode%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.18013.5.1%22%2C%22issuing_authority_unicode%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.18013.5.1%22%2C%22resident_postal_code%22%5D%2C%22values%22%3A%5B1180%2C1190%2C1200%2C1210%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.18013.5.1%22%2C%22issuing_country%22%5D%2C%22values%22%3A%5B%22AT%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.23220.photoid.1%22%2C%22person_id%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.23220.photoid.1%22%2C%22resident_street%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.23220.photoid.1%22%2C%22administrative_number%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.23220.photoid.1%22%2C%22travel_document_number%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.23220.dtc.1%22%2C%22dtc_version%22%5D%7D%2C%7B%22path%22%3A%5B%22org.iso.23220.dtc.1%22%2C%22dtc_dg1%22%5D%7D%5D%7D%5D%7D&client_metadata=%7B%22client_name%22%3A%22Verifier2%22%2C%22logo_uri%22%3A%22https%3A%2F%2Fimages.squarespace-cdn.com%2Fcontent%2Fv1%2F609c0ddf94bcc0278a7cbdb4%2F4d493ccf-c893-4882-925f-fda3256c38f4%2FWalt.id_Logo_transparent.png%22%7D",
    "policies": {
      "vcPolicies": [
        {
          "policy": "signature",
          "id": "signature"
        },
        {
          "policy": "regex",
          "path": "$.['org.iso.23220.dtc.1'].dtc_version",
          "regex": "^(\"[0-9]+\"|-?[0-9]+(\\.[0-9]+)?)$",
          "allowNull": false,
          "id": "regex"
        }
      ],
      "specificVcPolicies": {}
    },
    "policyResults": {
      "vcPolicies": [
        {
          "policy": {
            "policy": "signature",
            "id": "signature"
          },
          "success": true,
          "result": {
            "verification_result": true,
            "signed_credential": "o2d2ZXJzaW9uYzEuMGlkb2N1bWVudHOBo2dkb2NUeXBld29yZy5pc28uMjMyMjAucGhvdG9pZC4xbGlzc3VlclNpZ25lZKJqbmFtZVNwYWNlc6Nxb3JnLmlzby4xODAxMy41LjGF2BhYWqRoZGlnZXN0SUQAZnJhbmRvbVBXjrSPzTHh_3EpjZBMk3VzcWVsZW1lbnRJZGVudGlmaWVyc2ZhbWlseV9uYW1lX3VuaWNvZGVsZWxlbWVudFZhbHVlY0RvZdgYWFqkaGRpZ2VzdElEAWZyYW5kb21QUjRsAOzweOWZe4Pa7qdDeXFlbGVtZW50SWRlbnRpZmllcnJnaXZlbl9uYW1lX3VuaWNvZGVsZWxlbWVudFZhbHVlZEpvaG7YGFhppGhkaWdlc3RJRAVmcmFuZG9tUOeHfqfTlyUI23tPzklRyBhxZWxlbWVudElkZW50aWZpZXJ4GWlzc3VpbmdfYXV0aG9yaXR5X3VuaWNvZGVsZWxlbWVudFZhbHVla0xQRCBXaWVuIDIy2BhYWqRoZGlnZXN0SUQOZnJhbmRvbVAwinKsJb5yZAOwDgUs61drcWVsZW1lbnRJZGVudGlmaWVydHJlc2lkZW50X3Bvc3RhbF9jb2RlbGVsZW1lbnRWYWx1ZRkEptgYWFWkaGRpZ2VzdElEBmZyYW5kb21Q8FQtg_Xvk_6_ZO2on0J3ZnFlbGVtZW50SWRlbnRpZmllcm9pc3N1aW5nX2NvdW50cnlsZWxlbWVudFZhbHVlYkFUd29yZy5pc28uMjMyMjAucGhvdG9pZC4xhNgYWFSkaGRpZ2VzdElEAGZyYW5kb21QbrvMNdB9GrPeYqmr3_S3y3FlbGVtZW50SWRlbnRpZmllcmlwZXJzb25faWRsZWxlbWVudFZhbHVlZ0FUMTIzNDXYGFhepGhkaWdlc3RJRAVmcmFuZG9tUGkakmU3ilFxvy8fPrJm_0dxZWxlbWVudElkZW50aWZpZXJvcmVzaWRlbnRfc3RyZWV0bGVsZW1lbnRWYWx1ZWtQw7xjaGxnYXNzZdgYWGKkaGRpZ2VzdElEBGZyYW5kb21QTnK027UPJOcDdhTEtZF1l3FlbGVtZW50SWRlbnRpZmllcnVhZG1pbmlzdHJhdGl2ZV9udW1iZXJsZWxlbWVudFZhbHVlaUFUQVRBVDEyM9gYWF6kaGRpZ2VzdElEB2ZyYW5kb21Q38ynn5QTSgOntjBpVdGdm3FlbGVtZW50SWRlbnRpZmllcnZ0cmF2ZWxfZG9jdW1lbnRfbnVtYmVybGVsZW1lbnRWYWx1ZRoAESAec29yZy5pc28uMjMyMjAuZHRjLjGC2BhYT6RoZGlnZXN0SUQAZnJhbmRvbVCt1yWTaOG0nFd3oM_z2pfMcWVsZW1lbnRJZGVudGlmaWVya2R0Y192ZXJzaW9ubGVsZW1lbnRWYWx1ZQHYGFikpGhkaWdlc3RJRAFmcmFuZG9tUF9zKHM5dwwm3Ndfe4_fUZFxZWxlbWVudElkZW50aWZpZXJnZHRjX2RnMWxlbGVtZW50VmFsdWV4WFA8QVVURE9FPDxKT0hOPDw8PDw8PDw8PDw8PDw8PDw8PDw8PDw8PDw8PDw8MDAxMTIyMzM0MEFVVDIwMDEyMDdNMzAwOTEyOTw8PDw8PDw8PDw8PDw8MDZqaXNzdWVyQXV0aIRDoQEmoRghWQINMIICCTCCAbCgAwIBAgIUfqyiArJZoX7M61_473UAVi2_UpgwCgYIKoZIzj0EAwIwKDELMAkGA1UEBhMCQVQxGTAXBgNVBAMMEFdhbHRpZCBUZXN0IElBQ0EwHhcNMjUwNjAyMDY0MTEzWhcNMjYwOTAyMDY0MTEzWjAzMQswCQYDVQQGEwJBVDEkMCIGA1UEAwwbV2FsdGlkIFRlc3QgRG9jdW1lbnQgU2lnbmVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5Xp3CFQDMrMpR_SQ0jt_jTOqExk1PRzjQ79aKpIsJM1mqOBrDCBqTAfBgNVHSMEGDAWgBTxCn2nWMrE70qXb614U14BweY2azAdBgNVHQ4EFgQUx5qkOLC4lpl1xpYZGmF9HLxtp0gwDgYDVR0PAQH_BAQDAgeAMBoGA1UdEgQTMBGGD2h0dHBzOi8vd2FsdC5pZDAVBgNVHSUBAf8ECzAJBgcogYxdBQECMCQGA1UdHwQdMBswGaAXoBWGE2h0dHBzOi8vd2FsdC5pZC9jcmwwCgYIKoZIzj0EAwIDRwAwRAIgHTap3c6yCUNhDVfZWBPMKj9dCWZbrME03kh9NJTbw1ECIAvVvuGll9O21eR16SkJHHAA1pPcovhcTvF9fz9cc66MWQXq2BhZBeWmZ3ZlcnNpb25jMS4wb2RpZ2VzdEFsZ29yaXRobWdTSEEtMjU2bHZhbHVlRGlnZXN0c6Nxb3JnLmlzby4xODAxMy41LjG1AFggHs4Zw8M_FMp07jxll3RTDAqE6JB7kBmWMhfwfjzGzA4BWCD7yPCQZjqaFcrt_WgR9sAdW2ziDPlrCVsfd8iVmIMDLwJYIK-PQcwmRZoKgS3c3IdV597v6AwuVAOYyy1EZfpUQ2tMA1ggCpSg-11_W6RBAXi4gDwR4nI_vI9cpONJHOZACftxhz8EWCDdWVmvr6DvTnfdtqyddWLpb0GQXX3Z5uSLetB2PN6IOwVYIENvxy_Rs2-9cEdx0tdKmbttvDX-Qv7cidtK7WK0saTSBlggHKehanNLWPv3eJ1uNQVAFk1uSGde43X1JyiLAHSWBQQHWCD265uI2ZWphi_9k27J4o0lW8OPsqkV45RoYH_ectHJ4AhYICqCl2fLlZI9znzy6rIrjxfNgMc7DarZnJJjwJryy1pICVgg-aOmuuYUuiMyjkd5Yl2vy4AzJwmnWph4D6Y-uyV59F0KWCBuP2YIrjiQzCbtwdMAtZz7pTrYhFPxTp5Pxz8722sXMAtYIDDh0_Xi3IBjAp3bLcgq-vC1L5I7Ejwh3WkPr1vwLAe-DFgg7OY8BWy_rJQUzFTEjb5-j-dbjTjl0EkA8jh14YSnLfYNWCCkCJXa0v5pEivv-blgQSmDRa0DHzV6zI3QStC8OErfmQ5YILLRZQ79eqIz5W3UHghJlvV2IVAP66A-3qPw7-ld_OveD1ggIxyc21FE6g7U5E1eJ9f1UquonrAdgAyxTOtPFcM5zpcQWCDQ0IQ0YH4q4GXD8cm_JRcXW35QcgpEDDoBPJzGBs1J3BFYIHHlgsipbxe9uaOnD_znrWeGRXZP7sxm7b99_9Uz8nLSElgg0sgZa6JS-1cNL89KiUzK2-jo_uyiOB_q0keS7K065qETWCAtVlbSSlnHrAw5a7TPWIOQB6lZi5kswOCEZ20mjSnT2hRYIOmF49LL1MdFIgyA97jAHeyoJwb_K_NbxoLTVvQhxQmad29yZy5pc28uMjMyMjAucGhvdG9pZC4xqQBYIK1J0RHnaeau55mdOkQ4M4af2kRqAaMH2fUbOOOKH9DDAVgg7p6iC2dyZsdPD-8pqMxFwiE8Tt1TAk1xxuiCCD-yUD4CWCBTlFrzS4d6QsRSX8Js-TTPXH1SzSAVRKWVbpDYe3bmrgNYIP8fC75sQEyNKdAePELhjjs7qHmxyxKMvsqmCD6L7nGGBFgguRVw8MChW9Ew9HXF8RmxSYNhhJ3Jq3klB0Q4YkoH4t4FWCAboi49bkOTF_c4mmc2Xh_aM9F9fQCmyhQXWScfuAPFRQZYIFlvy0ZCmJvsFbgvz2GvZ9U9pYaWrQcUvv_-EqQ1oW0OB1ggiFZXrFWAlLCxg-1SeekRM84wNu0OElwiQude8Uig4lcIWCC717fM_6E4FqW7VHYb_yiXk9FAUIfvB683jO8WtRFRdHNvcmcuaXNvLjIzMjIwLmR0Yy4xogBYINyUeDNgw_arfmhacW8ooRNcnAMj9jqesw9SWmUG9hv5AVggbZpw6lp_qcoA-CLSaBXqg0wyjOurK_bgVfU3DCOh0hhtZGV2aWNlS2V5SW5mb6FpZGV2aWNlS2V5pAECIAEhWCDIy6N-quKCA3W-Ih8b71tpKN2T0ynFm4GQrJqmsArkliJYIJn1Gz3hE_ZxjNfQVtC-KI77qfaJ8W70Fu5kyV8lEqJVZ2RvY1R5cGV3b3JnLmlzby4yMzIyMC5waG90b2lkLjFsdmFsaWRpdHlJbmZvo2ZzaWduZWTAeB4yMDI1LTA5LTE5VDEzOjIyOjE2Ljk2ODEzOTExMFppdmFsaWRGcm9twHgeMjAyNS0wOS0xOVQxMzoyMjoxNi45NjgxNDAxMTBaanZhbGlkVW50aWzAeB4yMDI2LTA5LTE5VDEzOjIyOjE2Ljk2ODE0MjMxMFpYQBM4mbEoq9v8gigaoX_gkyBQEaWDrhauPv6ny4AfrVifa0-CtX0EQ7UHYd5bzF8mMB_EvKzNHFZeeTFhRifZ-v9sZGV2aWNlU2lnbmVkompuYW1lU3BhY2Vz2BhBoGpkZXZpY2VBdXRooW9kZXZpY2VTaWduYXR1cmWEQ6EBJqD2WED6bx4FebKf7alDsr5gXyBSFKOgpqliZnv6Rk7NpDoGae1FiSl-mY1vtDTfeO2cIN52zbN8dx-PwYrLrwpm6cAAZnN0YXR1cwA",
            "verified_data": {
              "org.iso.18013.5.1": {
                "family_name_unicode": "Doe",
                "given_name_unicode": "John",
                "issuing_authority_unicode": "LPD Wien 22",
                "resident_postal_code": 1190,
                "issuing_country": "AT"
              },
              "org.iso.23220.photoid.1": {
                "person_id": "AT12345",
                "resident_street": "Püchlgasse",
                "administrative_number": "ATATAT123",
                "travel_document_number": 1122334
              },
              "org.iso.23220.dtc.1": {
                "dtc_version": 1,
                "dtc_dg1": "P<AUTDOE<<JOHN<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<0011223340AUT2001207M3009129<<<<<<<<<<<<<<06"
              }
            },
            "successful_issuer_public_key": {
              "kty": "EC",
              "crv": "P-256",
              "x": "Pzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5U",
              "y": "6dwhUAzKzKUf0kNI7f40zqhMZNT0c40O_WiqSLCTNZo"
            },
            "successful_issuer_public_key_id": "sW5yv0UmZ3S0dQuUrwlR9I3foREBHHFwXhGJGqGEVf0"
          }
        },
        {
          "policy": {
            "policy": "regex",
            "path": "$.['org.iso.23220.dtc.1'].dtc_version",
            "regex": "^(\"[0-9]+\"|-?[0-9]+(\\.[0-9]+)?)$",
            "allowNull": false,
            "id": "regex"
          },
          "success": true,
          "result": {
            "value": "1",
            "groups": [
              "1",
              "1",
              ""
            ]
          }
        }
      ],
      "specificVcPolicies": {},
      "overallSuccess": true
    },
    "presentedRawData": {
      "vpToken": {
        "my_photoid": [
          "o2d2ZXJzaW9uYzEuMGlkb2N1bWVudHOBo2dkb2NUeXBld29yZy5pc28uMjMyMjAucGhvdG9pZC4xbGlzc3VlclNpZ25lZKJqbmFtZVNwYWNlc6Nxb3JnLmlzby4xODAxMy41LjGF2BhYWqRoZGlnZXN0SUQAZnJhbmRvbVBXjrSPzTHh_3EpjZBMk3VzcWVsZW1lbnRJZGVudGlmaWVyc2ZhbWlseV9uYW1lX3VuaWNvZGVsZWxlbWVudFZhbHVlY0RvZdgYWFqkaGRpZ2VzdElEAWZyYW5kb21QUjRsAOzweOWZe4Pa7qdDeXFlbGVtZW50SWRlbnRpZmllcnJnaXZlbl9uYW1lX3VuaWNvZGVsZWxlbWVudFZhbHVlZEpvaG7YGFhppGhkaWdlc3RJRAVmcmFuZG9tUOeHfqfTlyUI23tPzklRyBhxZWxlbWVudElkZW50aWZpZXJ4GWlzc3VpbmdfYXV0aG9yaXR5X3VuaWNvZGVsZWxlbWVudFZhbHVla0xQRCBXaWVuIDIy2BhYWqRoZGlnZXN0SUQOZnJhbmRvbVAwinKsJb5yZAOwDgUs61drcWVsZW1lbnRJZGVudGlmaWVydHJlc2lkZW50X3Bvc3RhbF9jb2RlbGVsZW1lbnRWYWx1ZRkEptgYWFWkaGRpZ2VzdElEBmZyYW5kb21Q8FQtg_Xvk_6_ZO2on0J3ZnFlbGVtZW50SWRlbnRpZmllcm9pc3N1aW5nX2NvdW50cnlsZWxlbWVudFZhbHVlYkFUd29yZy5pc28uMjMyMjAucGhvdG9pZC4xhNgYWFSkaGRpZ2VzdElEAGZyYW5kb21QbrvMNdB9GrPeYqmr3_S3y3FlbGVtZW50SWRlbnRpZmllcmlwZXJzb25faWRsZWxlbWVudFZhbHVlZ0FUMTIzNDXYGFhepGhkaWdlc3RJRAVmcmFuZG9tUGkakmU3ilFxvy8fPrJm_0dxZWxlbWVudElkZW50aWZpZXJvcmVzaWRlbnRfc3RyZWV0bGVsZW1lbnRWYWx1ZWtQw7xjaGxnYXNzZdgYWGKkaGRpZ2VzdElEBGZyYW5kb21QTnK027UPJOcDdhTEtZF1l3FlbGVtZW50SWRlbnRpZmllcnVhZG1pbmlzdHJhdGl2ZV9udW1iZXJsZWxlbWVudFZhbHVlaUFUQVRBVDEyM9gYWF6kaGRpZ2VzdElEB2ZyYW5kb21Q38ynn5QTSgOntjBpVdGdm3FlbGVtZW50SWRlbnRpZmllcnZ0cmF2ZWxfZG9jdW1lbnRfbnVtYmVybGVsZW1lbnRWYWx1ZRoAESAec29yZy5pc28uMjMyMjAuZHRjLjGC2BhYT6RoZGlnZXN0SUQAZnJhbmRvbVCt1yWTaOG0nFd3oM_z2pfMcWVsZW1lbnRJZGVudGlmaWVya2R0Y192ZXJzaW9ubGVsZW1lbnRWYWx1ZQHYGFikpGhkaWdlc3RJRAFmcmFuZG9tUF9zKHM5dwwm3Ndfe4_fUZFxZWxlbWVudElkZW50aWZpZXJnZHRjX2RnMWxlbGVtZW50VmFsdWV4WFA8QVVURE9FPDxKT0hOPDw8PDw8PDw8PDw8PDw8PDw8PDw8PDw8PDw8PDw8MDAxMTIyMzM0MEFVVDIwMDEyMDdNMzAwOTEyOTw8PDw8PDw8PDw8PDw8MDZqaXNzdWVyQXV0aIRDoQEmoRghWQINMIICCTCCAbCgAwIBAgIUfqyiArJZoX7M61_473UAVi2_UpgwCgYIKoZIzj0EAwIwKDELMAkGA1UEBhMCQVQxGTAXBgNVBAMMEFdhbHRpZCBUZXN0IElBQ0EwHhcNMjUwNjAyMDY0MTEzWhcNMjYwOTAyMDY0MTEzWjAzMQswCQYDVQQGEwJBVDEkMCIGA1UEAwwbV2FsdGlkIFRlc3QgRG9jdW1lbnQgU2lnbmVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5Xp3CFQDMrMpR_SQ0jt_jTOqExk1PRzjQ79aKpIsJM1mqOBrDCBqTAfBgNVHSMEGDAWgBTxCn2nWMrE70qXb614U14BweY2azAdBgNVHQ4EFgQUx5qkOLC4lpl1xpYZGmF9HLxtp0gwDgYDVR0PAQH_BAQDAgeAMBoGA1UdEgQTMBGGD2h0dHBzOi8vd2FsdC5pZDAVBgNVHSUBAf8ECzAJBgcogYxdBQECMCQGA1UdHwQdMBswGaAXoBWGE2h0dHBzOi8vd2FsdC5pZC9jcmwwCgYIKoZIzj0EAwIDRwAwRAIgHTap3c6yCUNhDVfZWBPMKj9dCWZbrME03kh9NJTbw1ECIAvVvuGll9O21eR16SkJHHAA1pPcovhcTvF9fz9cc66MWQXq2BhZBeWmZ3ZlcnNpb25jMS4wb2RpZ2VzdEFsZ29yaXRobWdTSEEtMjU2bHZhbHVlRGlnZXN0c6Nxb3JnLmlzby4xODAxMy41LjG1AFggHs4Zw8M_FMp07jxll3RTDAqE6JB7kBmWMhfwfjzGzA4BWCD7yPCQZjqaFcrt_WgR9sAdW2ziDPlrCVsfd8iVmIMDLwJYIK-PQcwmRZoKgS3c3IdV597v6AwuVAOYyy1EZfpUQ2tMA1ggCpSg-11_W6RBAXi4gDwR4nI_vI9cpONJHOZACftxhz8EWCDdWVmvr6DvTnfdtqyddWLpb0GQXX3Z5uSLetB2PN6IOwVYIENvxy_Rs2-9cEdx0tdKmbttvDX-Qv7cidtK7WK0saTSBlggHKehanNLWPv3eJ1uNQVAFk1uSGde43X1JyiLAHSWBQQHWCD265uI2ZWphi_9k27J4o0lW8OPsqkV45RoYH_ectHJ4AhYICqCl2fLlZI9znzy6rIrjxfNgMc7DarZnJJjwJryy1pICVgg-aOmuuYUuiMyjkd5Yl2vy4AzJwmnWph4D6Y-uyV59F0KWCBuP2YIrjiQzCbtwdMAtZz7pTrYhFPxTp5Pxz8722sXMAtYIDDh0_Xi3IBjAp3bLcgq-vC1L5I7Ejwh3WkPr1vwLAe-DFgg7OY8BWy_rJQUzFTEjb5-j-dbjTjl0EkA8jh14YSnLfYNWCCkCJXa0v5pEivv-blgQSmDRa0DHzV6zI3QStC8OErfmQ5YILLRZQ79eqIz5W3UHghJlvV2IVAP66A-3qPw7-ld_OveD1ggIxyc21FE6g7U5E1eJ9f1UquonrAdgAyxTOtPFcM5zpcQWCDQ0IQ0YH4q4GXD8cm_JRcXW35QcgpEDDoBPJzGBs1J3BFYIHHlgsipbxe9uaOnD_znrWeGRXZP7sxm7b99_9Uz8nLSElgg0sgZa6JS-1cNL89KiUzK2-jo_uyiOB_q0keS7K065qETWCAtVlbSSlnHrAw5a7TPWIOQB6lZi5kswOCEZ20mjSnT2hRYIOmF49LL1MdFIgyA97jAHeyoJwb_K_NbxoLTVvQhxQmad29yZy5pc28uMjMyMjAucGhvdG9pZC4xqQBYIK1J0RHnaeau55mdOkQ4M4af2kRqAaMH2fUbOOOKH9DDAVgg7p6iC2dyZsdPD-8pqMxFwiE8Tt1TAk1xxuiCCD-yUD4CWCBTlFrzS4d6QsRSX8Js-TTPXH1SzSAVRKWVbpDYe3bmrgNYIP8fC75sQEyNKdAePELhjjs7qHmxyxKMvsqmCD6L7nGGBFgguRVw8MChW9Ew9HXF8RmxSYNhhJ3Jq3klB0Q4YkoH4t4FWCAboi49bkOTF_c4mmc2Xh_aM9F9fQCmyhQXWScfuAPFRQZYIFlvy0ZCmJvsFbgvz2GvZ9U9pYaWrQcUvv_-EqQ1oW0OB1ggiFZXrFWAlLCxg-1SeekRM84wNu0OElwiQude8Uig4lcIWCC717fM_6E4FqW7VHYb_yiXk9FAUIfvB683jO8WtRFRdHNvcmcuaXNvLjIzMjIwLmR0Yy4xogBYINyUeDNgw_arfmhacW8ooRNcnAMj9jqesw9SWmUG9hv5AVggbZpw6lp_qcoA-CLSaBXqg0wyjOurK_bgVfU3DCOh0hhtZGV2aWNlS2V5SW5mb6FpZGV2aWNlS2V5pAECIAEhWCDIy6N-quKCA3W-Ih8b71tpKN2T0ynFm4GQrJqmsArkliJYIJn1Gz3hE_ZxjNfQVtC-KI77qfaJ8W70Fu5kyV8lEqJVZ2RvY1R5cGV3b3JnLmlzby4yMzIyMC5waG90b2lkLjFsdmFsaWRpdHlJbmZvo2ZzaWduZWTAeB4yMDI1LTA5LTE5VDEzOjIyOjE2Ljk2ODEzOTExMFppdmFsaWRGcm9twHgeMjAyNS0wOS0xOVQxMzoyMjoxNi45NjgxNDAxMTBaanZhbGlkVW50aWzAeB4yMDI2LTA5LTE5VDEzOjIyOjE2Ljk2ODE0MjMxMFpYQBM4mbEoq9v8gigaoX_gkyBQEaWDrhauPv6ny4AfrVifa0-CtX0EQ7UHYd5bzF8mMB_EvKzNHFZeeTFhRifZ-v9sZGV2aWNlU2lnbmVkompuYW1lU3BhY2Vz2BhBoGpkZXZpY2VBdXRooW9kZXZpY2VTaWduYXR1cmWEQ6EBJqD2WED6bx4FebKf7alDsr5gXyBSFKOgpqliZnv6Rk7NpDoGae1FiSl-mY1vtDTfeO2cIN52zbN8dx-PwYrLrwpm6cAAZnN0YXR1cwA"
        ]
      },
      "state": "5c2c4802-be15-4441-9039-04bed76e3607"
    },
    "presentedCredentials": {
      "my_photoid": [
        {
          "type": "vc-mdocs",
          "credentialData": {
            "org.iso.18013.5.1": {
              "family_name_unicode": "Doe",
              "given_name_unicode": "John",
              "issuing_authority_unicode": "LPD Wien 22",
              "resident_postal_code": 1190,
              "issuing_country": "AT"
            },
            "org.iso.23220.photoid.1": {
              "person_id": "AT12345",
              "resident_street": "Püchlgasse",
              "administrative_number": "ATATAT123",
              "travel_document_number": 1122334
            },
            "org.iso.23220.dtc.1": {
              "dtc_version": 1,
              "dtc_dg1": "P<AUTDOE<<JOHN<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<0011223340AUT2001207M3009129<<<<<<<<<<<<<<06"
            }
          },
          "signed": "o2d2ZXJzaW9uYzEuMGlkb2N1bWVudHOBo2dkb2NUeXBld29yZy5pc28uMjMyMjAucGhvdG9pZC4xbGlzc3VlclNpZ25lZKJqbmFtZVNwYWNlc6Nxb3JnLmlzby4xODAxMy41LjGF2BhYWqRoZGlnZXN0SUQAZnJhbmRvbVBXjrSPzTHh_3EpjZBMk3VzcWVsZW1lbnRJZGVudGlmaWVyc2ZhbWlseV9uYW1lX3VuaWNvZGVsZWxlbWVudFZhbHVlY0RvZdgYWFqkaGRpZ2VzdElEAWZyYW5kb21QUjRsAOzweOWZe4Pa7qdDeXFlbGVtZW50SWRlbnRpZmllcnJnaXZlbl9uYW1lX3VuaWNvZGVsZWxlbWVudFZhbHVlZEpvaG7YGFhppGhkaWdlc3RJRAVmcmFuZG9tUOeHfqfTlyUI23tPzklRyBhxZWxlbWVudElkZW50aWZpZXJ4GWlzc3VpbmdfYXV0aG9yaXR5X3VuaWNvZGVsZWxlbWVudFZhbHVla0xQRCBXaWVuIDIy2BhYWqRoZGlnZXN0SUQOZnJhbmRvbVAwinKsJb5yZAOwDgUs61drcWVsZW1lbnRJZGVudGlmaWVydHJlc2lkZW50X3Bvc3RhbF9jb2RlbGVsZW1lbnRWYWx1ZRkEptgYWFWkaGRpZ2VzdElEBmZyYW5kb21Q8FQtg_Xvk_6_ZO2on0J3ZnFlbGVtZW50SWRlbnRpZmllcm9pc3N1aW5nX2NvdW50cnlsZWxlbWVudFZhbHVlYkFUd29yZy5pc28uMjMyMjAucGhvdG9pZC4xhNgYWFSkaGRpZ2VzdElEAGZyYW5kb21QbrvMNdB9GrPeYqmr3_S3y3FlbGVtZW50SWRlbnRpZmllcmlwZXJzb25faWRsZWxlbWVudFZhbHVlZ0FUMTIzNDXYGFhepGhkaWdlc3RJRAVmcmFuZG9tUGkakmU3ilFxvy8fPrJm_0dxZWxlbWVudElkZW50aWZpZXJvcmVzaWRlbnRfc3RyZWV0bGVsZW1lbnRWYWx1ZWtQw7xjaGxnYXNzZdgYWGKkaGRpZ2VzdElEBGZyYW5kb21QTnK027UPJOcDdhTEtZF1l3FlbGVtZW50SWRlbnRpZmllcnVhZG1pbmlzdHJhdGl2ZV9udW1iZXJsZWxlbWVudFZhbHVlaUFUQVRBVDEyM9gYWF6kaGRpZ2VzdElEB2ZyYW5kb21Q38ynn5QTSgOntjBpVdGdm3FlbGVtZW50SWRlbnRpZmllcnZ0cmF2ZWxfZG9jdW1lbnRfbnVtYmVybGVsZW1lbnRWYWx1ZRoAESAec29yZy5pc28uMjMyMjAuZHRjLjGC2BhYT6RoZGlnZXN0SUQAZnJhbmRvbVCt1yWTaOG0nFd3oM_z2pfMcWVsZW1lbnRJZGVudGlmaWVya2R0Y192ZXJzaW9ubGVsZW1lbnRWYWx1ZQHYGFikpGhkaWdlc3RJRAFmcmFuZG9tUF9zKHM5dwwm3Ndfe4_fUZFxZWxlbWVudElkZW50aWZpZXJnZHRjX2RnMWxlbGVtZW50VmFsdWV4WFA8QVVURE9FPDxKT0hOPDw8PDw8PDw8PDw8PDw8PDw8PDw8PDw8PDw8PDw8MDAxMTIyMzM0MEFVVDIwMDEyMDdNMzAwOTEyOTw8PDw8PDw8PDw8PDw8MDZqaXNzdWVyQXV0aIRDoQEmoRghWQINMIICCTCCAbCgAwIBAgIUfqyiArJZoX7M61_473UAVi2_UpgwCgYIKoZIzj0EAwIwKDELMAkGA1UEBhMCQVQxGTAXBgNVBAMMEFdhbHRpZCBUZXN0IElBQ0EwHhcNMjUwNjAyMDY0MTEzWhcNMjYwOTAyMDY0MTEzWjAzMQswCQYDVQQGEwJBVDEkMCIGA1UEAwwbV2FsdGlkIFRlc3QgRG9jdW1lbnQgU2lnbmVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5Xp3CFQDMrMpR_SQ0jt_jTOqExk1PRzjQ79aKpIsJM1mqOBrDCBqTAfBgNVHSMEGDAWgBTxCn2nWMrE70qXb614U14BweY2azAdBgNVHQ4EFgQUx5qkOLC4lpl1xpYZGmF9HLxtp0gwDgYDVR0PAQH_BAQDAgeAMBoGA1UdEgQTMBGGD2h0dHBzOi8vd2FsdC5pZDAVBgNVHSUBAf8ECzAJBgcogYxdBQECMCQGA1UdHwQdMBswGaAXoBWGE2h0dHBzOi8vd2FsdC5pZC9jcmwwCgYIKoZIzj0EAwIDRwAwRAIgHTap3c6yCUNhDVfZWBPMKj9dCWZbrME03kh9NJTbw1ECIAvVvuGll9O21eR16SkJHHAA1pPcovhcTvF9fz9cc66MWQXq2BhZBeWmZ3ZlcnNpb25jMS4wb2RpZ2VzdEFsZ29yaXRobWdTSEEtMjU2bHZhbHVlRGlnZXN0c6Nxb3JnLmlzby4xODAxMy41LjG1AFggHs4Zw8M_FMp07jxll3RTDAqE6JB7kBmWMhfwfjzGzA4BWCD7yPCQZjqaFcrt_WgR9sAdW2ziDPlrCVsfd8iVmIMDLwJYIK-PQcwmRZoKgS3c3IdV597v6AwuVAOYyy1EZfpUQ2tMA1ggCpSg-11_W6RBAXi4gDwR4nI_vI9cpONJHOZACftxhz8EWCDdWVmvr6DvTnfdtqyddWLpb0GQXX3Z5uSLetB2PN6IOwVYIENvxy_Rs2-9cEdx0tdKmbttvDX-Qv7cidtK7WK0saTSBlggHKehanNLWPv3eJ1uNQVAFk1uSGde43X1JyiLAHSWBQQHWCD265uI2ZWphi_9k27J4o0lW8OPsqkV45RoYH_ectHJ4AhYICqCl2fLlZI9znzy6rIrjxfNgMc7DarZnJJjwJryy1pICVgg-aOmuuYUuiMyjkd5Yl2vy4AzJwmnWph4D6Y-uyV59F0KWCBuP2YIrjiQzCbtwdMAtZz7pTrYhFPxTp5Pxz8722sXMAtYIDDh0_Xi3IBjAp3bLcgq-vC1L5I7Ejwh3WkPr1vwLAe-DFgg7OY8BWy_rJQUzFTEjb5-j-dbjTjl0EkA8jh14YSnLfYNWCCkCJXa0v5pEivv-blgQSmDRa0DHzV6zI3QStC8OErfmQ5YILLRZQ79eqIz5W3UHghJlvV2IVAP66A-3qPw7-ld_OveD1ggIxyc21FE6g7U5E1eJ9f1UquonrAdgAyxTOtPFcM5zpcQWCDQ0IQ0YH4q4GXD8cm_JRcXW35QcgpEDDoBPJzGBs1J3BFYIHHlgsipbxe9uaOnD_znrWeGRXZP7sxm7b99_9Uz8nLSElgg0sgZa6JS-1cNL89KiUzK2-jo_uyiOB_q0keS7K065qETWCAtVlbSSlnHrAw5a7TPWIOQB6lZi5kswOCEZ20mjSnT2hRYIOmF49LL1MdFIgyA97jAHeyoJwb_K_NbxoLTVvQhxQmad29yZy5pc28uMjMyMjAucGhvdG9pZC4xqQBYIK1J0RHnaeau55mdOkQ4M4af2kRqAaMH2fUbOOOKH9DDAVgg7p6iC2dyZsdPD-8pqMxFwiE8Tt1TAk1xxuiCCD-yUD4CWCBTlFrzS4d6QsRSX8Js-TTPXH1SzSAVRKWVbpDYe3bmrgNYIP8fC75sQEyNKdAePELhjjs7qHmxyxKMvsqmCD6L7nGGBFgguRVw8MChW9Ew9HXF8RmxSYNhhJ3Jq3klB0Q4YkoH4t4FWCAboi49bkOTF_c4mmc2Xh_aM9F9fQCmyhQXWScfuAPFRQZYIFlvy0ZCmJvsFbgvz2GvZ9U9pYaWrQcUvv_-EqQ1oW0OB1ggiFZXrFWAlLCxg-1SeekRM84wNu0OElwiQude8Uig4lcIWCC717fM_6E4FqW7VHYb_yiXk9FAUIfvB683jO8WtRFRdHNvcmcuaXNvLjIzMjIwLmR0Yy4xogBYINyUeDNgw_arfmhacW8ooRNcnAMj9jqesw9SWmUG9hv5AVggbZpw6lp_qcoA-CLSaBXqg0wyjOurK_bgVfU3DCOh0hhtZGV2aWNlS2V5SW5mb6FpZGV2aWNlS2V5pAECIAEhWCDIy6N-quKCA3W-Ih8b71tpKN2T0ynFm4GQrJqmsArkliJYIJn1Gz3hE_ZxjNfQVtC-KI77qfaJ8W70Fu5kyV8lEqJVZ2RvY1R5cGV3b3JnLmlzby4yMzIyMC5waG90b2lkLjFsdmFsaWRpdHlJbmZvo2ZzaWduZWTAeB4yMDI1LTA5LTE5VDEzOjIyOjE2Ljk2ODEzOTExMFppdmFsaWRGcm9twHgeMjAyNS0wOS0xOVQxMzoyMjoxNi45NjgxNDAxMTBaanZhbGlkVW50aWzAeB4yMDI2LTA5LTE5VDEzOjIyOjE2Ljk2ODE0MjMxMFpYQBM4mbEoq9v8gigaoX_gkyBQEaWDrhauPv6ny4AfrVifa0-CtX0EQ7UHYd5bzF8mMB_EvKzNHFZeeTFhRifZ-v9sZGV2aWNlU2lnbmVkompuYW1lU3BhY2Vz2BhBoGpkZXZpY2VBdXRooW9kZXZpY2VTaWduYXR1cmWEQ6EBJqD2WED6bx4FebKf7alDsr5gXyBSFKOgpqliZnv6Rk7NpDoGae1FiSl-mY1vtDTfeO2cIN52zbN8dx-PwYrLrwpm6cAAZnN0YXR1cwA",
          "docType": "org.iso.23220.photoid.1",
          "issuer": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2IiwieCI6IlB6cDZlVlNBZFhFUnFBcDhxOE91REVobDJJTEdBYW9hUVhUSjJzRDJnNVUiLCJ5IjoiNmR3aFVBekt6S1VmMGtOSTdmNDB6cWhNWk5UMGM0ME9fV2lxU0xDVE5abyJ9",
          "format": "mso_mdoc"
        }
      ]
    }
  },
  "parent": "waltid.v2"
}
```
