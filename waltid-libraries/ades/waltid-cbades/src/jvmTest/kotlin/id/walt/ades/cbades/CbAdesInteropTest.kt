package id.walt.ades.cbades

import id.walt.ades.transport.KtorAdesTransport
import id.walt.ades.validation.AdesValidationPolicy
import id.walt.ades.validation.CheckStatus
import id.walt.ades.validation.ValidationData
import id.walt.ades.x509.Pem
import id.walt.ades.x509.X509Crl
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.X509CertificateChainValidator
import id.walt.certificate.x509.validation.validator.X509CertificateValidityValidator
import kotlinx.io.bytestring.ByteString
import kotlin.time.Clock
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import java.io.File
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

/**
 * Opt-in: validates a downloaded plugtest package (`-PcbadesInteropDir=<dir>` with participant folders and Data/)
 * and prints our verdict next to the verdicts other participants reported.
 */
class CbAdesInteropTest {
    @Test
    fun validatePlugtestPackage() = runBlocking {
        val dir = System.getProperty("cbades.interop.dir")?.let(::File) ?: return@runBlocking
        val anchors = buildList {
            File(dir, "Data").listFiles { f -> f.name.endsWith(".cer") || f.name.endsWith(".crt") || f.name.endsWith(".pem") }?.forEach { addAll(Pem.certificates(it.readBytes())) }
            File(dir, "trust").listFiles { f -> !f.name.endsWith(".crl") }?.forEach { addAll(Pem.certificates(it.readBytes())) }
        }.filter { it.isSelfIssued }
        val extra = ValidationData(
            crls = File(dir, "trust").listFiles { f -> f.name.endsWith(".crl") }?.map { X509Crl(it.readBytes()) } ?: emptyList(),
        )
        println("Trust anchors: ${anchors.map { it.subject.commonName }}")
        HttpClient(Java) { followRedirects = false }.use { http ->
            // Opt-in local interoperability harness; all HTTP(S) plugtest endpoints are permitted.
            val transport = KtorAdesTransport(http) { require(it.startsWith("http://") || it.startsWith("https://")) }
            val validator = CbAdesValidator(AdesValidationPolicy(trustAnchors = anchors, transport = transport, additionalValidationData = extra), DocumentResolver.http(transport))
            val reports = dir.walkTopDown().filter { it.name.startsWith("Verification_of_") }.toList()
            dir.listFiles()!!.filter { it.isDirectory && it.name != "Data" && it.name != "trust" }.sortedBy { it.name }.forEach { participant ->
                participant.listFiles { f -> f.name.endsWith(".cbor") }!!.sortedBy { it.name }.forEach { f ->
                    val r = validator.validate(f.readBytes())
                    // Differential migration probe: preserve AdES's historical control time and explicit anchors.
                    val util = X509CertificateUtil.Default
                    for (signature in r.signatures.filter { it.certificateChain.isNotEmpty() }) {
                        val sharedChain = signature.certificateChain.map { util.parseCertificateDerEncoded(ByteString(it.encoded)) }
                        val sharedAnchors = anchors.map { util.parseCertificateDerEncoded(ByteString(it.encoded)) }
                        val validators = util.services.certificateChainValidator.validators
                            .filterNot { it is X509CertificateValidityValidator } +
                            X509CertificateValidityValidator(clock = object : Clock {
                                override fun now() = signature.bestSignatureTime
                            })
                        val shared = X509CertificateChainValidator(validators, InMemoryTrustStore(sharedAnchors))
                            .validate(util.services.cryptoRuntime, sharedChain)
                        if (!shared.valid) println("SHARED-X509 ${participant.name}/${f.name}/${signature.signatureId}: ${shared.errorLog}")
                    }
                    val code = f.nameWithoutExtension
                    val others = reports.filter { it.name == "Verification_of_${participant.name}_$code.xml" }
                        .joinToString(" ") { "${it.parentFile.name}=" + Regex("<(VALID|INVALID|INDETERMINATE)\\b").find(it.readText())?.groupValues?.get(1) }
                    val sigs = r.signatures.joinToString(" | ") { s -> "${s.signatureId}:${s.indication}/${s.subIndication ?: "-"}/${s.level?.code}" }
                    println("${participant.name}/$code => ${r.indication}/${r.subIndication ?: "-"} [$sigs] others: $others")
                    (r.documentChecks + r.signatures.flatMap { it.checks }).filter { it.status == CheckStatus.FAILED || it.status == CheckStatus.WARNING }
                        .distinctBy { it.id + it.message }.forEach { println("    ${it.status} ${it.id}: ${it.message}") }
                    r.signatures.flatMap { it.timestamps }.filter { !it.passed }.forEach { println("    TS ${it.type} ${it.indication}/${it.subIndication}: ${it.problems}") }
                }
            }
        }
    }
}
