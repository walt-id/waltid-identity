import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class Credential {
  companion object {
    val testCredential: JsonObject = Json.decodeFromString<JsonObject>(
      "{\n" +
          "\"issuanceKey\": {\n" +
          "\"type\": \"local\",\n" +
          "\"jwk\": \"{\\\"kty\\\":\\\"OKP\\\",\\\"d\\\":\\\"mDhpwaH6JYSrD2Bq7Cs-pzmsjlLj4EOhxyI-9DM1mFI\\\",\\\"crv\\\":\\\"Ed25519\\\",\\\"kid\\\":\\\"Vzx7l5fh56F3Pf9aR3DECU5BwfrY6ZJe05aiWYWzan8\\\",\\\"x\\\":\\\"T3T4-u1Xz3vAV2JwPNxWfs4pik_JLiArz_WTCvrCFUM\\\"}\"\n" +
          "},\n" +
          "\"issuerDid\": \"did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp\",\n" +
          "\"vc\": {\n" +
          "\"@context\": [\n" +
          "\"https://www.w3.org/2018/credentials/v1\",\n" +
          "\"https://purl.imsglobal.org/spec/ob/v3p0/context.json\"\n" +
          "],\n" +
          "\"id\": \"urn:uuid:THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION (see below)\",\n" +
          "\"type\": [\n" +
          "\"VerifiableCredential\",\n" +
          "\"OpenBadgeCredential\"\n" +
          "],\n" +
          "\"name\": \"JFF x vc-edu PlugFest 3 Interoperability\",\n" +
          "\"issuer\": {\n" +
          "\"type\": [\n" +
          "\"Profile\"\n" +
          "],\n" +
          "\"id\": \"did:key:THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION FROM CONTEXT (see below)\",\n" +
          "\"name\": \"Jobs for the Future (JFF)\",\n" +
          "\"url\": \"https://www.jff.org/\",\n" +
          "\"image\": \"https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png\"\n" +
          "},\n" +
          "\"issuanceDate\": \"2023-07-20T07:05:44Z (THIS WILL BE REPLACED BY DYNAMIC DATA FUNCTION (see below))\",\n" +
          "\"expirationDate\": \"WILL BE MAPPED BY DYNAMIC DATA FUNCTION (see below)\",\n" +
          "\"credentialSubject\": {\n" +
          "\"id\": \"did:key:123 (THIS WILL BE REPLACED BY DYNAMIC DATA FUNCTION (see below))\",\n" +
          "\"type\": [\n" +
          "\"AchievementSubject\"\n" +
          "],\n" +
          "\"achievement\": {\n" +
          "\"id\": \"urn:uuid:ac254bd5-8fad-4bb1-9d29-efd938536926\",\n" +
          "\"type\": [\n" +
          "\"Achievement\"\n" +
          "],\n" +
          "\"name\": \"JFF x vc-edu PlugFest 3 Interoperability\",\n" +
          "\"description\": \"This wallet supports the use of W3C Verifiable Credentials and has demonstrated interoperability during the presentation request workflow during JFF x VC-EDU PlugFest 3.\",\n" +
          "\"criteria\": {\n" +
          "\"type\": \"Criteria\",\n" +
          "\"narrative\": \"Wallet solutions providers earned this badge by demonstrating interoperability during the presentation request workflow. This includes successfully receiving a presentation request, allowing the holder to select at least two types of verifiable credentials to create a verifiable presentation, returning the presentation to the requestor, and passing verification of the presentation and the included credentials.\"\n" +
          "},\n" +
          "\"image\": {\n" +
          "\"id\": \"https://w3c-ccg.github.io/vc-ed/plugfest-3-2023/images/JFF-VC-EDU-PLUGFEST3-badge-image.png\",\n" +
          "\"type\": \"Image\"\n" +
          "}\n" +
          "}\n" +
          "}\n" +
          "},\n" +
          "\"mapping\": {\n" +
          "\"id\": \"\",\n" +
          "\"issuer\": {\n" +
          "\"id\": \"\"\n" +
          "},\n" +
          "\"credentialSubject\": {\n" +
          "\"id\": \"\"\n" +
          "},\n" +
          "\"issuanceDate\": \"\",\n" +
          "\"expirationDate\": \"\"\n" +
          "}\n" +
          "}".trim()
    )
  }
}