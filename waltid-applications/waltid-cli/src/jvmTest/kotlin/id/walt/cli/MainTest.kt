package id.walt.cli

import id.walt.cli.util.getResourcePath
import id.walt.crypto.keys.KeyType
//import org.junit.jupiter.api.Disabled
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.*

private fun tapSystemOut(block: () -> Unit): String {
    val stdoutCaptureStream = ByteArrayOutputStream()

    val originalOut = System.out
    System.setOut(PrintStream(stdoutCaptureStream))

    try {
        block.invoke()

        return stdoutCaptureStream.toString()
    } finally {
        System.setOut(originalOut)
    }
}

class MainTest {

    val ed25519JWKKeyPattern: List<String> = listOf(
        """"kty": "OKP"""",
        """"d": ".*?"""",
        """"crv": "Ed25519"""",
        """"kid": ".*?"""",
        """"x": ".*?""""
    )

    val secp256k1JWKKeyPattern: List<String> = listOf(
        """"kty": "EC"""",
        """"d": ".*?"""",
        """"crv": "secp256k1"""",
        """"kid": ".*?"""",
        """"x": ".*?""""
    )

    val secp256r1JWKKeyPattern: List<String> = listOf(
        """"kty": "EC"""",
        """"d": ".*?"""",
        """"crv": "P-256"""",
        """"kid": ".*?"""",
        """"x": ".*?"""",
        """"y": ".*?""""
    )

    val rsaJWKKeyPattern: List<String> = listOf(
        """"p": ".*?"""",
        """"kty": "RSA"""",
        """"q": ".*?"""",
        """"d": ".*?"""",
        """"e": "AQAB"""",
        """"kid": ".*?"""",
        """"qi": ".*?"""",
        """"dp": ".*?"""",
        """"dq": ".*"""",
        """"n": ".*""""
    )

    val jwkKeyPatterns = mapOf(
        KeyType.Ed25519 to ed25519JWKKeyPattern,
        KeyType.secp256k1 to secp256k1JWKKeyPattern,
        KeyType.secp256r1 to secp256r1JWKKeyPattern,
        KeyType.RSA to rsaJWKKeyPattern
    )

    val rsaPEMKeyPattern = listOf(
        "-----BEGIN RSA PRIVATE KEY-----",
        "-----END RSA PRIVATE KEY-----",
        "-----BEGIN RSA PUBLIC KEY-----",
        "-----END RSA PUBLIC KEY-----"
    )

    val pemKeyPatterns = mapOf(
        KeyType.RSA to rsaPEMKeyPattern
    )

    val resourcesPath = "src/jvmTest/resources"

    val keyFilePath = "${resourcesPath}/key/ed25519_by_waltid_pvt_key.jwk"
    val did1 = "did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV"
    val did2 = "did:key:z6Mkjm2gaGsodGchfG4k8P6KwCHZsVEPZho5VuEbY94qiBB9"
    val vcFilePath = "${resourcesPath}/vc/openbadgecredential_sample.json"

    val signedVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.signed.json"
    val badSignedVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.signed.badsignature.json"

    val signedValidSchemaVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.signed.json"
    val signedInvalidSchemaVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.invalidschema.signed.json"

    val schemaFilePath = "${resourcesPath}/schema/OpenBadgeV3_schema.json"

    val signedExpiredVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.expired.signed.json"
    val signedNotExpiredVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.signed.json"

    val signedValidFromVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.signed.json"
    val signedInvalidFromVCFilePath = "${resourcesPath}/vc/openbadgecredential_sample.invalidnotbefore.signed.json"


    @Test
    fun `should show usage message when called with no arguments`() {
        val output = tapSystemOut { main(arrayOf("")) }
        assertContains(output, "Usage: waltid [<options>] <command> [<args>]...")
    }

    @Test
    fun `should show usage message when called with -h or --help`() {
        val output = tapSystemOut { main(arrayOf("-h")) }
        assertContains(output, "Usage: waltid [<options>] <command> [<args>]...")
    }

    @Test
    fun `should show key command usage message when called with -h or --help`() {
        val output = tapSystemOut { main(arrayOf("key", "-h")) }
        assertContains(output, "Usage: waltid key [<options>] <command> [<args>]...")
    }

    @Test
    fun `should show key generate command usage when called with -h or --help`() {
        val output = tapSystemOut { main(arrayOf("key", "generate", "-h")) }
        assertContains(output, "Usage: waltid key generate [<options>]")
    }

    @Test
    fun `should show key convert usage when called with -h or --help`() {
        val output = tapSystemOut { main(arrayOf("key", "convert", "-h")) }
        assertContains(output, "Usage: waltid key convert [<options>]")
    }

    @Test
    fun `should generate a new Secp256r1 key when 'key generate' is called with no arguments`() {
        val output = tapSystemOut { main(arrayOf("key", "generate")) }

        val expected = "Done. Key saved at file \"(.*?)\"".toRegex()

        assertExpectations(output, expected, secp256r1JWKKeyPattern)
        deleteGeneratedFile(output, expected)
    }

    @Test
    fun `should generate a new Secp256k1 key when 'key generate -tsecp256k1' is executed`() {
        val output = tapSystemOut { main(arrayOf("key", "generate", "-tsecp256k1")) }

        val expected = "Done. Key saved at file \"(.*?)\"".toRegex()

        assertExpectations(output, expected, secp256k1JWKKeyPattern)
        deleteGeneratedFile(output, expected)
    }

    @Test
    //@Disabled("RSA key parameters are too long and are being cropped in IntelliJ")
    @Ignore
    fun `should generate a new RSA key when 'key generate --keyType=RSA' is executed`() {
        val output = tapSystemOut { main(arrayOf("key", "generate", "--keyType=RSA")) }

        val expected = "Done. Key saved at file \"(.*?)\"".toRegex()

        assertExpectations(output, expected, rsaJWKKeyPattern)
        deleteGeneratedFile(output, expected)
    }

    @Test
//    @Disabled
    @Ignore
    fun `should generate a RSA key in the specified file when 'key generate --keyType=RSA -o myRSAKey' is executed`() {

        val outputFileName = "myRSAKey.json"
        val output = tapSystemOut { main(arrayOf("key", "generate", "--keyType=RSA", "-o${outputFileName}")) }

        val expected = "Done. Key saved at file \"(.*?)\"".toRegex()

        assertExpectations(output, expected, rsaJWKKeyPattern, outputFileName)

        deleteGeneratedFile(output, expected)
    }

    @Test
    fun `should generate an Ed25519 key in the specified file when 'key generate --keyType=Ed25519 -o myEd25519Key' is executed`() {
        val keyType = KeyType.Ed25519
        val outputFileName = "myEd25519Key.json"
        testKeyGenerate(keyType, outputFileName, jwkKeyPatterns[keyType])
    }

    @Test
    fun `should generate a secp256k1 key in the specified file when 'key generate --keyType=secp256k1 -o mySecp256k1Key' is executed`() {
        val keyType = KeyType.secp256k1
        val outputFileName = "mySecp256k1Key.json"
        testKeyGenerate(keyType, outputFileName, jwkKeyPatterns[keyType])
    }

    @Test
    fun `should generate a secp256r1 key in the specified file when 'key generate --keyType=secp256r1 -o mySecp256r1Key' is executed`() {
        val keyType = KeyType.secp256r1
        val outputFileName = "mySecp256r1Key.json"
        testKeyGenerate(keyType, outputFileName, jwkKeyPatterns[keyType])
    }

    @Test
    fun `should convert the RSA key in the specified file from the JWK to the PEM format when 'key convert -i myRSAKey' is executed`() {
        val inputFileName = getResourcePath(this, "key/rsa_by_waltid_pub_pvt_key.jwk")
        testSuccessfulKeyConvertion(inputFileName, pemKeyPatterns[KeyType.RSA])
    }

    @Test
    fun `should fail when trying to convert the Ed25519 key in the specified file from the JWK to the PEM format when 'key convert -i myEd25519Key' is executed`() {
        val inputFileName = getResourcePath(this, "key/ed25519_by_waltid_pub_pvt_key.jwk")
        testFailedKeyConvertion(inputFileName, pemKeyPatterns[KeyType.RSA])
    }

    @Test
    @Ignore
    fun `should convert the secp256k1 key in the specified file from the JWK to the PEM format when 'key convert -i mySecp256k1Key' is executed`() {
        main(arrayOf("key convert -i mySecp256k1Key.json"))
        fail("Not yet implemented")
        // assertContains(output.all  , "Usage: waltid [<options>] <command> [<args>]...")
    }

    @Test
    @Ignore
    fun `should convert the secp256r1 key in the specified file from the JWK to the PEM format when 'key convert -i mySecp256r1Key' is executed`() {
        main(arrayOf("key convert -i mySecp256r1Key.json"))
        fail("Not yet implemented")
        // assertContains(output.all  , "Usage: waltid [<options>] <command> [<args>]...")
    }

    @Test
    @Ignore
    fun `should convert the RSA key in the specified file from the PEM to the JWK format when 'key convert --input=myRSAKey' is executed`() {
        main(arrayOf("key convert --input=./myRSAKey.pem"))
        fail("Not yet implemented")
        // assertContains(output.all  , "Usage: waltid [<options>] <command> [<args>]...")
    }

    @Test
    @Ignore
    fun `should convert the Ed25519 key in the specified file from the PEM to the JWK format when 'key convert --input=myEd25519Key' is executed`() {
        main(arrayOf("key convert --input=./myEd25519Key.pem"))
        fail("Not yet implemented")
        // assertContains(output.all  , "Usage: waltid [<options>] <command> [<args>]...")
    }

    @Test
    @Ignore
    fun `should convert the secp256k1 key in the specified file from the PEM to the JWK format when 'key convert --input=mySecp256k1Key' is executed`() {
        main(arrayOf("key convert --input=./mySecp256k1Key.pem"))
        fail("Not yet implemented")
        // assertContains(output.all  , "Usage: waltid [<options>] <command> [<args>]...")
    }

    @Test
    @Ignore
    fun `should convert the secp256r1 key in the specified PEM input file and save it in the specified JWK output file when 'key convert --input=mySecp256r1Key --output=convertedSecp256r1' is executed`() {
        main(arrayOf("key convert --input=./mySecp256r1Key.pem --output=./convertedSecp256r1.jwk`"))
        fail("Not yet implemented")
        // assertContains(output.all  , "Usage: waltid [<options>] <command> [<args>]...")
    }

    // fun `should convert the secp256r1 key in the specified PEM input file and save it in the specified JWK output file when 'openssl ecparam -genkey -name secp256k1 -out secp256k1_by_openssl_pub_pvt_key' is executed`(
    //     output: CapturedOutput
    // ) {
    //     main(arrayOf("openssl ecparam -genkey -name secp256k1 -out secp256k1_by_openssl_pub_pvt_key.pem"))
    //     fail("Not yet implemented")
    //     //  assertContains(output.all  , "Usage: waltid [<options>] <command> [<args>]...")
    // }

    @Test
    @Ignore
    fun `should convert the Secp256k1 key in the given PEM file to the JWK format when 'key convert --verbose -i secp256k1_by_openssl_pub_pvt_key' is executed`() {
        main(arrayOf("key convert --verbose -i secp256k1_by_openssl_pub_pvt_key.pem"))
        fail("Not yet implemented")
        // assertContains(output.all  , "Usage: waltid [<options>] <command> [<args>]...")
    }

    @Test
    @Ignore
    fun `should print 'did' command usage message when 'did -h' is executed`() {
        main(arrayOf("did -h"))
        fail("Not yet implemented")
        // assertContains(output.all  , "Usage: waltid [<options>] <command> [<args>]...")
    }

    @Test
    @Ignore
    fun `should print 'did create' command usage message when 'did create -h' is executed`() {
        main(arrayOf("did create -h"))
        fail("Not yet implemented")
        // assertContains(output.all  , "Usage: waltid [<options>] <command> [<args>]...")
    }

    @Test
    @Ignore
    fun `should create a new did-key when 'did create' is executed`() {
        main(arrayOf("did create"))
        fail("Not yet implemented")
        // assertContains(output.all  , "Usage: waltid [<options>] <command> [<args>]...")
    }

    @Test
    @Ignore
    fun `should create a new did-key with the key provided in the specified file when 'did create --key=myRSAKey' is executed`() {
        main(arrayOf("did create --key=myRSAKey.json"))
        fail("Not yet implemented")
        // assertContains(output.all  , "Usage: waltid [<options>] <command> [<args>]...")
    }

    @Test
    fun `should print usage instructions when 'vc' command is called with no argument`() {
        val output = tapSystemOut { main(arrayOf("vc")) }
        assertContains(output, "Usage: waltid vc")
    }

    @Test
    @Ignore("Failing with NoSuchSubcommand :-/ I'll check it later.")
    fun `should print usage instructions when 'vc sign' command is called with no argument`() {
        val output = tapSystemOut { main(arrayOf("vc sign")) }
        assertContains(output, "Usage: waltid vc sign")
    }

    @Test
    @Ignore
    fun `should sign a given VC when no DID is provided for the Issuer`() {

        val output = tapSystemOut { main(arrayOf("""vc sign -k "$keyFilePath" -s $did2 """)) }

        assertFalse(output.contains("ERROR"))
        assertContains(output, "Generated DID:")
        assertContains(output, "Signed VC saved at")
    }

    @Test
    @Ignore
    fun `should sign a given VC when all parameters are provided correctly`() {

        val output = tapSystemOut { main(arrayOf("""vc sign -k "$keyFilePath" -i $did1 -s $did2 """)) }

        assertFalse(output.contains("ERROR"))
        assertContains(output, "Signed VC saved at")
    }

    @Test
    @Ignore("Failing with NoSuchSubcommand :-/ I'll check it later.")
    fun `should print usage instructions when 'vc verify' command is called with no argument`() {
        val output = tapSystemOut { main(arrayOf("vc verify"))}
        assertContains(output, "Usage: waltid vc verify")
    }

    @Test
    fun `should verify the signature of a VC if the VC file is provided with no other parameter`() {
        val output = tapSystemOut { main(arrayOf("vc", "verify", "--policy=signature", signedVCFilePath)) }

        assertFalse(output.contains("ERROR"))
        assertContains(output, "signature: Success! ")
    }

    @Test
    fun `should succeed the signature verification when a valid VC is provided`() {
        val output = tapSystemOut { main(arrayOf("vc", "verify", "--policy=signature", signedVCFilePath)) }

        assertFalse(output.contains("ERROR"))
        assertContains(output, "signature: Success! ")
    }

    @Test
    fun `should fail the signature verification when an invalid VC is provided`() {
        val output = tapSystemOut { main(arrayOf("vc", "verify", "--policy=signature", badSignedVCFilePath)) }

        assertContains(output, "signature: Fail!")
    }

    @Test
    fun `should succeed if the credentials expiration date (exp for JWTs) has not been exceeded when --policy=expired`() {
        val output = tapSystemOut { main(arrayOf("vc", "verify", "--policy=expired", signedNotExpiredVCFilePath)) }
        assertContains(output, "expired: Success")
    }

    @Test
    fun `should fail if the credentials expiration date (exp for JWTs) has been exceeded when --policy=expired`() {
        val output = tapSystemOut { main(arrayOf("vc", "verify", "--policy=expired", signedExpiredVCFilePath)) }
        assertContains(output, "expired: Fail! VC expired since")
    }

    @Test
    fun `should succeed if credential is valid when --policy=not-before`() {
        val output = tapSystemOut { main(arrayOf("vc", "verify", "--policy=not-before", signedValidFromVCFilePath)) }
        assertContains(output, "not-before: Success")
    }

    @Test
    fun `should fail if credential is not valid yet when --policy=not-before`() {
        val output = tapSystemOut { main(arrayOf("vc", "verify", "--policy=not-before", signedInvalidFromVCFilePath)) }
        assertContains(output, "not-before: Fail! VC not valid until")
    }

    @Test
    fun `should succeed the schema verification when a valid VC is provided`() {
        val output = tapSystemOut {
            main(
                arrayOf(
                    "vc",
                    "verify",
                    "--policy=schema",
                    "-a",
                    "schema=${schemaFilePath}",
                    signedValidSchemaVCFilePath
                )
            )
        }

        assertFalse(output.contains("ERROR"))
        assertContains(output, "schema: Success! ")
    }

    @Test
    fun `should fail the schema verification when an invalid VC is provided`() {
        val output = tapSystemOut {
            main(
                arrayOf(
                    "vc",
                    "verify",
                    "--policy=schema",
                    "-a",
                    "schema=${schemaFilePath}",
                    signedInvalidSchemaVCFilePath
                )
            )
        }

        assertContains(output, "schema: Fail!")
        assertContains(output, "missing required properties: [name]")
    }

    @Test
    fun `should apply all policies specified`() {
        val output = tapSystemOut {
            main(
                arrayOf(
                    "vc",
                    "verify",
                    "--policy=signature",
                    "--policy=schema",
                    "-a",
                    "schema=${schemaFilePath}",
                    signedInvalidSchemaVCFilePath
                )
            )
        }
        assertContains(output, "signature: Success!")
        assertContains(output, "schema: Fail!")
        assertContains(output, "schema: Fail!.*missing required properties".toRegex())
    }

    private fun testSuccessfulKeyCmd(
        cmd: String,
        cmdArgs: Array<String>,
        outputFileName: String,
        expectedMessage: Regex,
        bodyPattern: List<String>?,
    ): String {
        // deleteFile(outputFileName)
        val output = tapSystemOut { main(arrayOf("key", cmd) + cmdArgs) }
        assertExpectations(output, expectedMessage, bodyPattern, outputFileName)
        // deleteGeneratedFile(output.all, expectedMessage)
        // deleteFile(outputFileName)
        return output
    }

    private fun testFailedKeyCmd(
        cmd: String,
        cmdArgs: Array<String>,
        expectedMessage: Regex,
    ) {
        val output = tapSystemOut { main(arrayOf("key", cmd) + cmdArgs) }
        assertExpectations(output, expectedMessage, null, null)
    }

    private fun testKeyGenerate(
        keyType: KeyType,
        outputFileName: String,
        bodyPattern: List<String>?,
    ) {
        val cmd = "generate"
        val cmdArgs = arrayOf("--keyType=${keyType.name}", "-o${outputFileName}")
        val expected = "Done. Key saved at file \"(.*?)\"".toRegex()

        deleteFile(outputFileName) // To avoid overwrite prompt
        testSuccessfulKeyCmd(cmd, cmdArgs, outputFileName, expected, bodyPattern)
        // deleteGeneratedFile(output.all, expected)
        deleteFile(outputFileName)
    }

    private fun testSuccessfulKeyConvertion(inputFileName: String, bodyPattern: List<String>?) {
        val cmd = "convert"
        val cmdArgs = arrayOf("-i", inputFileName)
        val expected = "Done. Converted \".*?\" to \"(.*?)\"".toRegex()

        val output = testSuccessfulKeyCmd(cmd, cmdArgs, inputFileName, expected, bodyPattern)
//        deleteGeneratedFile(output.all, expected)
        deleteGeneratedFile(output, expected)
    }

    private fun testFailedKeyConvertion(inputFileName: String, bodyPattern: List<String>?) {
        val cmd = "convert"
        val cmdArgs = arrayOf("-i", inputFileName)
        val expected = "Oops. Something went wrong when converting the key".toRegex()

        testFailedKeyCmd(cmd, cmdArgs, expected)
    }

    private fun assertExpectations(
        output: String,
        expectedMessage: Regex,
        bodyPattern: List<String>?,
        outputFile: String? = null
    ) {
        assertContains(output, expectedMessage)

        bodyPattern?.forEach {
            assertContains(output, it.toRegex())
        }

        if (outputFile != null) {
            assertTrue(File(outputFile).exists())
        }
    }

    private fun deleteGeneratedFile(output: String, expectedOutput: Regex) {
        val match = expectedOutput.find(output)
        var filePath: String? = null
        if (match != null) {
            filePath = match.groups[1]?.value
        } else {
            throw Exception("Filename not found in the output with pattern '${expectedOutput}'")
        }

        File(filePath).delete()
    }

    private fun deleteFile(filename: String) {
        File(filename).delete()
    }
}
