package id.walt.etsi.cbades

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import id.walt.ades.cbades.CbAdesSigner
import id.walt.ades.cbades.CbAdesSigningContext
import id.walt.ades.cbades.CbAdesValidator
import id.walt.ades.cbades.CoseMessage
import id.walt.ades.cbades.DocumentResolver
import id.walt.ades.transport.AdesTransport
import id.walt.ades.transport.KtorAdesTransport
import id.walt.ades.tsp.Rfc3161TimestampClient
import id.walt.ades.tsp.TimestampSource
import id.walt.ades.validation.AdesLevel
import id.walt.ades.validation.AdesValidationPolicy
import id.walt.ades.validation.ValidationData
import id.walt.ades.x509.Pem
import id.walt.ades.x509.X509Certificate
import id.walt.ades.x509.X509Crl
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

private const val VALIDATOR_NAME = "walt.id waltid-cbades (ETSI TS 119 152-1 V1.1.1, EN 319 102-1 V1.4.1 processes)"

class CbAdesCommand : CliktCommand(name = "cbades") {
    override fun help(context: Context) = "ETSI CB-AdES Plugtests 2026: generate, verify and augment CB-AdES signatures"
    override fun run() = Unit

    companion object {
        fun create() = CbAdesCommand().subcommands(CbAdesGenerateCommand(), CbAdesVerifyCommand(), CbAdesAugmentCommand())
    }
}

/** Options shared by commands that create or augment signatures. */
private class SigningOptions : OptionGroup("Signing options") {
    val participant by option("--participant", help = "Participant acronym on the portal").default("WALT")
    val p12 by option("--p12", help = "PKCS#12 signing credential issued by the plugtest PKI").file(mustExist = true)
    val p12Password by option("--p12-password", envvar = "CBADES_P12_PASSWORD", help = "Password of the PKCS#12 file (or env CBADES_P12_PASSWORD)")
    val chain by option("--chain", help = "Additional CA certificate(s) to append to x5chain (PEM/DER)").file(mustExist = true).multiple()
    val pkiDir by option("--pki-dir", help = "Directory of the persistent local test PKI (second signers, counter-signers, AA)").file().default(File("cbades-pki"))
    val tsaUrl by option("--tsa-url", help = "RFC 3161 TSA URL (omitted: in-process test TSA, for dry runs only)")
    val tsa2Url by option("--tsa2-url", help = "Second RFC 3161 TSA URL for renewed archive time-stamps")
    val tsaHeader by option("--tsa-header", help = "Extra HTTP header for the TSA, 'Name: value'").multiple()
    val trust by option("--trust", help = "Trust anchor certificates / CRLs (files or directories)").file(mustExist = true).multiple()

    fun timestampSource(url: String?, transport: AdesTransport, pki: LocalPlugtestPki): TimestampSource =
        url?.let { Rfc3161TimestampClient(it, transport, headers = tsaHeader.associate { h -> h.substringBefore(':').trim() to h.substringAfter(':').trim() }) }
            ?: pki.timestampSource
}

internal fun loadTrust(files: List<File>): Pair<List<X509Certificate>, ValidationData> {
    val certs = mutableListOf<X509Certificate>()
    val extra = ValidationData()
    files.flatMap { if (it.isDirectory) it.walkTopDown().filter(File::isFile).toList() else listOf(it) }.forEach { f ->
        when (f.extension.lowercase()) {
            "crl" -> runCatching { extra.add(X509Crl(f.readBytes())) }
            "cer", "crt", "pem", "der" -> runCatching { Pem.certificates(f.readBytes()) }.getOrNull()?.forEach { c ->
                if (c.isSelfIssued) certs += c else extra.add(c)
            }
        }
    }
    return certs.distinct() to extra
}

class CbAdesGenerateCommand : CliktCommand(name = "generate") {
    override fun help(context: Context) = "Generate CB-AdES signatures for the plugtest test-case definitions (CB-B-*.xml)"

    private val signing by SigningOptions()
    private val testCases by option("--test-cases", "-t", help = "Directory with CB-B-*.xml definitions").file(mustExist = true).default(File("testcases/cbades"))
    private val dataDir by option("--data", help = "Portal Data directory (toBeSigned.txt, signature_creation_policy.der)").file(mustExist = true).default(File("testcases/cbades/Data"))
    private val output by option("--output", "-o", help = "Output directory").file().default(File("output/cbades"))
    private val only by option("--only", help = "Only these test case codes (repeatable)").multiple()
    private val zip by option("--zip", help = "Also write a flat upload zip").flag()

    override fun run() = runBlocking {
        HttpClient(Java).use { http ->
            val transport = KtorAdesTransport(http)
            val pki = LocalPlugtestPki(signing.pkiDir, signing.participant)
            val extraChain = signing.chain.flatMap { Pem.certificates(it.readBytes()) }
            val main = signing.p12?.let { CbAdesCredentials.fromPkcs12(it, (signing.p12Password ?: error("--p12-password required")).toCharArray(), extraChain) }
                ?: pki.credentials(pki.signer, "local-signer").also { echo("WARNING: no --p12 given, signing with the local test PKI (dry run)") }
            if (signing.tsaUrl == null) echo("WARNING: no --tsa-url given, using the in-process test TSA (dry run)")
            val (anchors, extra) = loadTrust(signing.trust + File(signing.pkiDir, "trust"))
            val ctx = CbAdesSigningContext(
                timestampSource = signing.timestampSource(signing.tsaUrl, transport, pki),
                archiveTimestampSource = signing.tsa2Url?.let { signing.timestampSource(it, transport, pki) },
                transport = transport,
                trustAnchors = anchors + main.certificateChain.filter { it.isSelfIssued },
                documentResolver = DocumentResolver.http(transport),
                additionalValidationData = extra,
            )
            val generator = CbAdesPlugtestGenerator(CbAdesSigner(ctx), main, pki, CbAdesPlugtestGenerator.loadData(dataDir))
            val validator = CbAdesValidator(AdesValidationPolicy(trustAnchors = ctx.trustAnchors, transport = transport, additionalValidationData = extra))
            val outDir = File(output, signing.participant).apply { mkdirs() }
            val cases = CbAdesTestCases.loadAll(testCases).filter { only.isEmpty() || it.code in only }
            echo("Generating ${cases.size} test case(s) as ${main.signingCertificate.subject}")
            val written = mutableListOf<File>()
            for (tc in cases) {
                try {
                    val sig = generator.generate(tc)
                    val file = File(outDir, "${tc.code}.cbor").apply { writeBytes(sig.bytes) }
                    written += file
                    val check = validator.validate(sig.bytes)
                    echo("  ${tc.code}: ${sig.bytes.size} bytes, self-check ${check.indication}${check.subIndication?.let { "/$it" } ?: ""} " +
                        check.signatures.joinToString(" ") { "[${it.signatureId} ${it.level?.code}]" })
                } catch (e: Exception) {
                    echo("  ${tc.code}: FAILED ${e::class.simpleName}: ${e.message}", err = true)
                    if (System.getenv("CBADES_DEBUG") != null) e.printStackTrace()
                }
            }
            if (zip) writeZip(File(output, "${signing.participant}-signatures.zip"), written)
        }
    }
}

class CbAdesVerifyCommand : CliktCommand(name = "verify") {
    override fun help(context: Context) = "Verify a downloaded plugtest package and write Verification_of_<PARTICIPANT>_<CODE>.xml reports"

    private val packageFile by option("--package", "-p", help = "Download-all zip or extracted directory").file(mustExist = true).required()
    private val participant by option("--participant", help = "Our participant acronym (reports are written to <output>/<participant>)").default("WALT")
    private val trust by option("--trust", help = "Additional trust anchors / CRLs (files or directories)").file(mustExist = true).multiple()
    private val output by option("--output", "-o").file().default(File("output/cbades-reports"))
    private val includeOwn by option("--include-own", help = "Also verify our own signatures").flag()
    private val only by option("--from", help = "Only verify signatures of these participants").multiple()
    private val zip by option("--zip", help = "Also write a flat upload zip").flag()
    private val offline by option("--offline", help = "Do not fetch revocation data / documents").flag()

    override fun run() = runBlocking {
        val dir = extractIfZip(packageFile)
        val (anchorsData, extraData) = loadTrust(listOfNotNull(File(dir, "Data").takeIf { it.isDirectory }, File(dir, "trust").takeIf { it.isDirectory }) + trust)
        echo("Trust anchors: ${anchorsData.joinToString { it.subject.commonName ?: it.subject.toString() }}")
        HttpClient(Java).use { http ->
            val transport = if (offline) null else KtorAdesTransport(http)
            val validator = CbAdesValidator(AdesValidationPolicy(trustAnchors = anchorsData, transport = transport, additionalValidationData = extraData))
            val outDir = File(output, participant).apply { mkdirs() }
            val written = mutableListOf<File>()
            val folders = dir.listFiles()!!.filter { it.isDirectory && it.name !in setOf("Data", "trust") }
                .filter { (includeOwn || it.name != participant) && (only.isEmpty() || it.name in only) }.sortedBy { it.name }
            for (folder in folders) {
                for (f in folder.walkTopDown().filter { it.isFile && it.extension == "cbor" }.sortedBy { it.name }) {
                    val code = f.nameWithoutExtension
                    val result = runCatching { validator.validate(f.readBytes()) }.getOrElse {
                        echo("  ${folder.name}/$code: ERROR ${it.message}", err = true); continue
                    }
                    val report = File(outDir, "Verification_of_${folder.name}_$code.xml")
                    report.writeText(CbAdesReportWriter.write(code, result, participant, VALIDATOR_NAME))
                    written += report
                    echo("  ${folder.name}/$code: ${CbAdesReportWriter.rootElement(result.indication)}${result.subIndication?.let { " ($it)" } ?: ""}")
                }
            }
            if (zip) writeZip(File(output, "$participant-reports.zip"), written)
        }
    }
}

class CbAdesAugmentCommand : CliktCommand(name = "augment") {
    override fun help(context: Context) = "Augment other participants' signatures per the augmentation test cases (Aug-B-*.xml)"

    private val signing by SigningOptions()
    private val packageFile by option("--package", "-p", help = "Download-all zip or extracted directory").file(mustExist = true).required()
    private val testCases by option("--test-cases", "-t").file(mustExist = true).default(File("testcases/cbades/augmentation"))
    private val output by option("--output", "-o").file().default(File("output/cbades-augmented"))
    private val zip by option("--zip").flag()

    override fun run() = runBlocking {
        val dir = extractIfZip(packageFile)
        HttpClient(Java).use { http ->
            val transport = KtorAdesTransport(http)
            val pki = LocalPlugtestPki(signing.pkiDir, signing.participant)
            val (anchors, extra) = loadTrust(listOfNotNull(File(dir, "Data").takeIf { it.isDirectory }, File(dir, "trust").takeIf { it.isDirectory }) + signing.trust)
            val signer = CbAdesSigner(
                CbAdesSigningContext(
                    timestampSource = signing.timestampSource(signing.tsaUrl, transport, pki),
                    archiveTimestampSource = signing.tsa2Url?.let { signing.timestampSource(it, transport, pki) },
                    transport = transport, trustAnchors = anchors, documentResolver = DocumentResolver.http(transport), additionalValidationData = extra,
                ),
            )
            val validator = CbAdesValidator(AdesValidationPolicy(trustAnchors = anchors, transport = transport, additionalValidationData = extra))
            val outDir = File(output, signing.participant).apply { mkdirs() }
            val written = mutableListOf<File>()
            for (aug in CbAdesTestCases.loadAugmentations(testCases)) {
                val target = when {
                    aug.augmented.startsWith("CB-B-LTA-") -> AdesLevel.B_LTA
                    aug.augmented.startsWith("CB-B-LT-") -> AdesLevel.B_LT
                    else -> AdesLevel.B_T
                }
                val sources = dir.listFiles()!!.filter { it.isDirectory && it.name !in setOf("Data", "trust", signing.participant) }
                    .mapNotNull { p -> File(p, "${aug.initial}.cbor").takeIf { it.isFile }?.let { p.name to it } }
                for ((origin, file) in sources) {
                    try {
                        val augmented = signer.augment(CoseMessage.decode(file.readBytes()), target)
                        val out = File(outDir, "${aug.portalCode}_${origin}_${aug.initial}.cbor").apply { writeBytes(augmented.encode()) }
                        written += out
                        val check = validator.validate(out.readBytes())
                        echo("  ${out.name}: ${check.indication}${check.subIndication?.let { "/$it" } ?: ""} ${check.signatures.map { it.level?.code }}")
                    } catch (e: Exception) {
                        echo("  ${aug.portalCode} $origin/${aug.initial}: FAILED ${e.message}", err = true)
                    }
                }
            }
            if (zip) writeZip(File(output, "${signing.participant}-augmented.zip"), written)
        }
    }
}

internal fun extractIfZip(file: File): File {
    if (file.isDirectory) return file
    val target = File(file.parentFile, file.nameWithoutExtension).apply { mkdirs() }
    ZipFile(file).use { zip ->
        zip.entries().asSequence().filter { !it.isDirectory }.forEach { e ->
            val out = File(target, e.name).canonicalFile
            require(out.path.startsWith(target.canonicalPath + File.separator)) { "Zip entry outside target: ${e.name}" }
            out.parentFile.mkdirs()
            zip.getInputStream(e).use { input -> out.outputStream().use { input.copyTo(it) } }
        }
    }
    return target
}

internal fun writeZip(target: File, files: List<File>) {
    ZipOutputStream(target.outputStream()).use { zos ->
        files.forEach { f -> zos.putNextEntry(ZipEntry(f.name)); f.inputStream().use { it.copyTo(zos) }; zos.closeEntry() }
    }
    println("Wrote ${target.path} (${files.size} files, flat)")
}
