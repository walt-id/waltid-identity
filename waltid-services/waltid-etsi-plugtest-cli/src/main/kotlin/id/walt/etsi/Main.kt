package id.walt.etsi

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.etsi.config.ConfigManager
import id.walt.etsi.generator.MdocGenerator
import id.walt.etsi.generator.SdJwtVcGenerator
import id.walt.etsi.validator.ContentValidator
import id.walt.etsi.validator.CredentialValidator
import id.walt.etsi.validator.ValidationResult
import id.walt.etsi.validator.VendorFile
import id.walt.etsi.validator.XmlReportGenerator
import id.walt.etsi.validator.ZipParser
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File

private val log = KotlinLogging.logger {}

class EtsiCli : CliktCommand(name = "etsi-cli") {
    override fun help(context: Context) = "ETSI Plugtest EAA Generator CLI"
    override fun run() = Unit
}

class GenerateCommand : CliktCommand(name = "generate") {
    override fun help(context: Context) = "Generate EAA files from test cases"

    private val configFile by option("--config", help = "Path to HOCON configuration file")
        .file(mustExist = true)

    private val testCasesFile by option("--test-cases", "-t", help = "Path to test-cases.json")
        .file(mustExist = true)
        .required()

    private val outputDir by option("--output", "-o", help = "Output directory for generated files (overrides config)")
        .file()

    private val issuerKeyFile by option("--issuer-key", "-k", help = "Path to issuer JWK key file (overrides config)")
        .file(mustExist = true)

    private val issuerCertFile by option("--issuer-cert", "-c", help = "Path to issuer certificate PEM file (overrides config)")
        .file(mustExist = true)

    private val holderKeyFile by option("--holder-key", help = "Path to holder JWK key file (overrides config)")
        .file(mustExist = true)

    private val profile by option("--profile", "-p", help = "Filter by profile name (e.g., 'SD-JWT-VC EAA')")

    private val testCaseId by option("--test-case", help = "Generate only specific test case by ID (e.g., 'SJV-EAA-1')")

    private val format by option("--format", "-f", help = "Filter by format: 'sd-jwt-vc' or 'mdoc'")

    private val issuerUrl by option("--issuer-url", help = "Issuer URL for SD-JWT-VC (overrides config)")

    private val vct by option("--vct", help = "Verifiable Credential Type for SD-JWT-VC (overrides config)")

    override fun run() = runBlocking {
        val config = ConfigManager.loadConfig(configFile)
        echo("Configuration loaded")

        echo("Loading test cases from: ${testCasesFile.absolutePath}")

        val scrapedData = Json.decodeFromString<ScrapedData>(testCasesFile.readText())
        echo("Loaded ${scrapedData.formats.size} formats")

        val effectiveOutputDir = outputDir ?: File(config.output.directory)
        effectiveOutputDir.mkdirs()

        val issuerKey = loadKey(
            cliKeyFile = issuerKeyFile,
            configKeyFile = config.issuer.keyFile,
            configKeyJwk = config.issuer.keyJwk,
            name = "issuer"
        )
        
        val holderKey = loadKey(
            cliKeyFile = holderKeyFile,
            configKeyFile = config.holder.keyFile,
            configKeyJwk = config.holder.keyJwk,
            name = "holder"
        )
        
        val issuerCert = loadCertificate(
            cliCertFile = issuerCertFile,
            configCertFile = config.issuer.certificateFile,
            configCertPem = config.issuer.certificatePem
        )

        val effectiveIssuerUrl = issuerUrl ?: config.issuer.url
        val effectiveVct = vct ?: config.sdjwt.vct

        var generatedCount = 0

        for (formatData in scrapedData.formats) {
            if (format != null && formatData.id != format) continue

            echo("\nProcessing format: ${formatData.name}")

            for (profileData in formatData.profiles) {
                if (profile != null && !profileData.id.contains(profile!!, ignoreCase = true)) continue

                echo("  Profile: ${profileData.id}")

                for (testCase in profileData.testCases) {
                    if (testCaseId != null && testCase.id != testCaseId) continue

                    try {
                        when (formatData.id) {
                            "sd-jwt-vc" -> {
                                val result = SdJwtVcGenerator.generate(
                                    testCase = testCase,
                                    issuerKey = issuerKey,
                                    issuerCertificatePem = issuerCert,
                                    holderKey = if (testCase.hasKeyBinding) holderKey else null,
                                    issuerUrl = effectiveIssuerUrl,
                                    vct = effectiveVct
                                )

                                val fileName = config.output.fileNamePattern
                                    .replace("{testCaseId}", testCase.id)
                                val outputFile = File(effectiveOutputDir, "$fileName.json")
                                outputFile.writeText(result.sdJwtVc)
                                echo("    Generated: ${outputFile.name}")
                                generatedCount++
                            }

                            "mdoc" -> {
                                val result = MdocGenerator.generate(
                                    testCase = testCase,
                                    issuerKey = issuerKey,
                                    issuerCertificatePem = issuerCert,
                                    holderKey = holderKey
                                )

                                val fileName = config.output.fileNamePattern
                                    .replace("{testCaseId}", testCase.id)
                                val outputFile = File(effectiveOutputDir, "$fileName.cbor")
                                outputFile.writeBytes(result.cborBytes)
                                echo("    Generated: ${outputFile.name}")
                                generatedCount++
                            }

                            else -> {
                                echo("    Skipping unknown format: ${formatData.id}")
                            }
                        }
                    } catch (e: Exception) {
                        echo("    ERROR generating ${testCase.id}: ${e.message}")
                        log.error(e) { "Failed to generate ${testCase.id}" }
                    }
                }
            }
        }

        echo("\nGeneration complete! Generated $generatedCount files in: ${effectiveOutputDir.absolutePath}")
    }

    private suspend fun loadKey(
        cliKeyFile: File?,
        configKeyFile: String?,
        configKeyJwk: String?,
        name: String
    ): JWKKey {
        return when {
            cliKeyFile != null -> {
                echo("Loading $name key from CLI option: ${cliKeyFile.absolutePath}")
                KeyManager.resolveSerializedKey(cliKeyFile.readText()) as JWKKey
            }
            configKeyFile != null -> {
                val file = ConfigManager.resolvePath(configKeyFile)
                echo("Loading $name key from config file: ${file.absolutePath}")
                KeyManager.resolveSerializedKey(file.readText()) as JWKKey
            }
            configKeyJwk != null -> {
                echo("Loading $name key from inline JWK in config")
                KeyManager.resolveSerializedKey(configKeyJwk) as JWKKey
            }
            else -> {
                echo("Generating new $name key (P-256)")
                JWKKey.generate(KeyType.secp256r1)
            }
        }
    }

    private fun loadCertificate(
        cliCertFile: File?,
        configCertFile: String?,
        configCertPem: String?
    ): String {
        return when {
            cliCertFile != null -> {
                echo("Loading certificate from CLI option: ${cliCertFile.absolutePath}")
                cliCertFile.readText()
            }
            configCertFile != null -> {
                val file = ConfigManager.resolvePath(configCertFile)
                echo("Loading certificate from config file: ${file.absolutePath}")
                file.readText()
            }
            configCertPem != null -> {
                echo("Using inline certificate from config")
                configCertPem
            }
            else -> {
                log.warn { "No certificate provided, using placeholder. For production, provide a proper certificate chain." }
                DEFAULT_CERTIFICATE
            }
        }
    }

    companion object {
        private val DEFAULT_CERTIFICATE = """
-----BEGIN CERTIFICATE-----
MIICCTCCAbCgAwIBAgIUfqyiArJZoX7M61/473UAVi2/UpgwCgYIKoZIzj0EAwIw
KDELMAkGA1UEBhMCQVQxGTAXBgNVBAMMEFdhbHRpZCBUZXN0IElBQ0EwHhcNMjUw
NjAyMDY0MTEzWhcNMjYwOTAyMDY0MTEzWjAzMQswCQYDVQQGEwJBVDEkMCIGA1UE
AwwbV2FsdGlkIFRlc3QgRG9jdW1lbnQgU2lnbmVyMFkwEwYHKoZIzj0CAQYIKoZI
zj0DAQcDQgAEPzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5Xp3CFQDMrM
pR/SQ0jt/jTOqExk1PRzjQ79aKpIsJM1mqOBrDCBqTAfBgNVHSMEGDAWgBTxCn2n
WMrE70qXb614U14BweY2azAdBgNVHQ4EFgQUx5qkOLC4lpl1xpYZGmF9HLxtp0gw
DgYDVR0PAQH/BAQDAgeAMBoGA1UdEgQTMBGGD2h0dHBzOi8vd2FsdC5pZDAVBgNV
HSUBAf8ECzAJBgcogYxdBQECMCQGA1UdHwQdMBswGaAXoBWGE2h0dHBzOi8vd2Fs
dC5pZC9jcmwwCgYIKoZIzj0EAwIDRwAwRAIgHTap3c6yCUNhDVfZWBPMKj9dCWZb
rME03kh9NJTbw1ECIAvVvuGll9O21eR16SkJHHAA1pPcovhcTvF9fz9cc66M
-----END CERTIFICATE-----
        """.trimIndent()
    }
}

class ListCommand : CliktCommand(name = "list") {
    override fun help(context: Context) = "List available test cases"

    private val testCasesFile by option("--test-cases", "-t", help = "Path to test-cases.json")
        .file(mustExist = true)
        .required()

    private val format by option("--format", "-f", help = "Filter by format: 'sd-jwt-vc' or 'mdoc'")

    private val profile by option("--profile", "-p", help = "Filter by profile name")

    override fun run() {
        val scrapedData = Json.decodeFromString<ScrapedData>(testCasesFile.readText())

        for (formatData in scrapedData.formats) {
            if (format != null && formatData.id != format) continue

            echo("\n${formatData.name} (${formatData.id})")
            echo("=" .repeat(50))

            for (profileData in formatData.profiles) {
                if (profile != null && !profileData.id.contains(profile!!, ignoreCase = true)) continue

                echo("\n  Profile: ${profileData.id}")
                if (profileData.notes.isNotBlank()) {
                    echo("  Notes: ${profileData.notes.take(100)}...")
                }

                for (testCase in profileData.testCases) {
                    val flags = buildList {
                        if (testCase.hasKeyBinding) add("KB")
                        if (testCase.hasSelectiveDisclosure) add("SD")
                        if (testCase.isPseudonym) add("PSEUDO")
                        if (testCase.isOneTime) add("1TIME")
                        if (testCase.isShortLived) add("SHORT")
                    }
                    val flagStr = if (flags.isNotEmpty()) " [${flags.joinToString(",")}]" else ""
                    echo("    - ${testCase.id}$flagStr")
                    echo("      ${testCase.description.take(80).replace("\n", " ")}...")
                }
            }
        }
    }
}

class ValidateCommand : CliktCommand(name = "validate") {
    override fun help(context: Context) = "Validate EAA files from a vendor zip and generate XML reports"

    private val zipFile by option("--zip", "-z", help = "Path to the vendor zip file downloaded from ETSI portal")
        .file(mustExist = true)
        .required()

    private val testCasesFile by option("--test-cases", "-t", help = "Path to test-cases.json for content validation")
        .file(mustExist = true)

    private val outputDir by option("--output", "-o", help = "Output directory for XML verification reports")
        .file()
        .default(File("verification-reports"))

    private val vendorFilter by option("--vendor", "-v", help = "Filter by vendor name (optional)")

    private val testCaseFilter by option("--test-case", help = "Filter by test case ID (optional)")

    private val formatFilter by option("--format", "-f", help = "Filter by format: 'sd-jwt-vc' or 'mdoc'")

    private val summary by option("--summary", "-s", help = "Print summary only, don't generate reports")
        .flag(default = false)

    override fun run() = runBlocking {
        echo("Parsing zip file: ${zipFile.absolutePath}")
        
        // Load test cases if provided
        val scrapedData = testCasesFile?.let { file ->
            echo("Loading test cases from: ${file.absolutePath}")
            Json.decodeFromString<ScrapedData>(file.readText())
        }
        
        if (scrapedData != null) {
            echo("Content validation enabled - will check credentials against test case requirements")
        }
        
        val vendorFiles = ZipParser.parseZipFile(zipFile)
        echo("Found ${vendorFiles.size} files from ${vendorFiles.map { it.vendorId }.distinct().size} vendors")

        val filteredFiles = vendorFiles.filter { file ->
            (vendorFilter == null || file.vendorId.contains(vendorFilter!!, ignoreCase = true)) &&
            (testCaseFilter == null || file.testCaseId.contains(testCaseFilter!!, ignoreCase = true)) &&
            (formatFilter == null || when (formatFilter) {
                "sd-jwt-vc" -> file.format == id.walt.etsi.validator.VendorFile.FileFormat.SD_JWT_VC
                "mdoc" -> file.format == id.walt.etsi.validator.VendorFile.FileFormat.MDOC
                else -> true
            })
        }

        if (filteredFiles.isEmpty()) {
            echo("No files match the specified filters")
            return@runBlocking
        }

        echo("Validating ${filteredFiles.size} files...")
        echo("")

        if (!summary) {
            outputDir.mkdirs()
        }

        val results = mutableListOf<ValidationResult>()
        var validCount = 0
        var invalidCount = 0
        var indeterminateCount = 0
        var contentValidCount = 0
        var contentInvalidCount = 0

        for (file in filteredFiles) {
            echo("  Validating: ${file.vendorId}/${file.fileName}")
            
            // Signature validation
            var result = CredentialValidator.validate(file)
            
            // Content validation if test cases are provided
            if (scrapedData != null) {
                val testCase = ContentValidator.findTestCase(scrapedData, file.testCaseId)
                if (testCase != null) {
                    val contentValidation = when (file.format) {
                        VendorFile.FileFormat.SD_JWT_VC -> {
                            ContentValidator.validateSdJwtVcContent(
                                file.content.decodeToString().trim(),
                                testCase
                            )
                        }
                        VendorFile.FileFormat.MDOC -> {
                            ContentValidator.validateMdocContent(
                                file.content,
                                testCase
                            )
                        }
                    }
                    
                    // Update result with content validation
                    result = result.copy(contentValidation = contentValidation)
                    
                    if (contentValidation.overallValid) {
                        contentValidCount++
                    } else {
                        contentInvalidCount++
                    }
                } else {
                    echo("    Warning: Test case ${file.testCaseId} not found in test-cases.json")
                }
            }
            
            results.add(result)

            val statusIcon = when (result.status) {
                ValidationResult.ValidationStatus.VALID -> {
                    validCount++
                    "✓"
                }
                ValidationResult.ValidationStatus.INVALID -> {
                    invalidCount++
                    "✗"
                }
                ValidationResult.ValidationStatus.INDETERMINATE -> {
                    indeterminateCount++
                    "?"
                }
            }

            echo("    $statusIcon ${result.status}")
            if (result.errorMessage != null) {
                echo("      Error: ${result.errorMessage}")
            }
            
            // Show content validation result
            result.contentValidation?.let { cv ->
                val cvIcon = if (cv.overallValid) "✓" else "✗"
                echo("    $cvIcon Content: ${cv.summary}")
            }

            if (!summary) {
                val reportXml = XmlReportGenerator.generateReport(result)
                val reportFileName = XmlReportGenerator.generateReportFileName(result)
                val reportFile = File(outputDir, reportFileName)
                reportFile.writeText(reportXml)
                echo("    Report: ${reportFile.name}")
            }
        }

        echo("")
        echo("=" .repeat(50))
        echo("Validation Summary:")
        echo("  Total files:       ${results.size}")
        echo("  Signature Valid:   $validCount")
        echo("  Signature Invalid: $invalidCount")
        echo("  Indeterminate:     $indeterminateCount")
        
        if (scrapedData != null) {
            echo("")
            echo("Content Validation:")
            echo("  Content Valid:     $contentValidCount")
            echo("  Content Invalid:   $contentInvalidCount")
        }
        
        if (!summary) {
            echo("")
            echo("Reports written to: ${outputDir.absolutePath}")
        }

        val byVendor = results.groupBy { it.vendorId }
        echo("")
        echo("Results by vendor:")
        for ((vendor, vendorResults) in byVendor) {
            val v = vendorResults.count { it.status == ValidationResult.ValidationStatus.VALID }
            val i = vendorResults.count { it.status == ValidationResult.ValidationStatus.INVALID }
            val u = vendorResults.count { it.status == ValidationResult.ValidationStatus.INDETERMINATE }
            val cv = vendorResults.count { it.contentValidation?.overallValid == true }
            val ci = vendorResults.count { it.contentValidation?.overallValid == false }
            
            val contentStr = if (scrapedData != null) ", content: $cv valid/$ci invalid" else ""
            echo("  $vendor: $v valid, $i invalid, $u indeterminate$contentStr")
        }
    }
}

fun main(args: Array<String>) = EtsiCli()
    .subcommands(GenerateCommand(), ListCommand(), ValidateCommand())
    .main(args)
