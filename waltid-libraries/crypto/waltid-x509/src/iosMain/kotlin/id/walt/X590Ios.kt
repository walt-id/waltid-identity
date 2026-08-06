package id.walt.x509

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFArrayCreate
import platform.CoreFoundation.CFArrayRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFTypeArrayCallBacks
import platform.CoreFoundation.CFDataRef
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.create
import platform.Security.SecCertificateCreateWithData
import platform.Security.SecCertificateRef
import platform.Security.SecPolicyCreateBasicX509
import platform.Security.SecPolicyCreateRevocation
import platform.Security.SecPolicyRef
import platform.Security.SecTrustCreateWithCertificates
import platform.Security.SecTrustEvaluate
import platform.Security.SecTrustResultTypeVar
import platform.Security.SecTrustRef
import platform.Security.SecTrustRefVar
import platform.Security.SecTrustSetAnchorCertificates
import platform.Security.SecTrustSetAnchorCertificatesOnly
import platform.Security.errSecSuccess
import platform.Security.kSecRevocationUseAnyAvailableMethod

@OptIn(ExperimentalForeignApi::class)
actual val platformSupportsPkixCertificatePathValidation: Boolean = true

@OptIn(ExperimentalForeignApi::class)
@Throws(X509ValidationException::class)
actual fun validateCertificateChain(
    leaf: CertificateDer,
    chain: List<CertificateDer>,
    trustAnchors: List<CertificateDer>?,
    enableTrustedChainRoot: Boolean,
    enableSystemTrustAnchors: Boolean,
    enableRevocation: Boolean
) = memScoped {
    val certificateReferences = mutableListOf<SecCertificateRef>()
    val policyReferences = mutableListOf<SecPolicyRef>()
    val arrayReferences = mutableListOf<CFArrayRef>()
    var trustReference: SecTrustRef? = null

    try {
        val anchorDers = buildList {
            addAll(trustAnchors.orEmpty())
            if (enableTrustedChainRoot) {
                addAll(chain.filter { certificate ->
                    runCatching {
                        PlatformX509Certificate.parse(certificate).isSelfSigned()
                    }.getOrDefault(false)
                })
            }
        }.distinct()
        val anchorSet = anchorDers.toSet()
        val certificateDers = (listOf(leaf) + chain.filterNot(anchorSet::contains))
            .distinct()

        if (anchorDers.isEmpty() && !enableSystemTrustAnchors) {
            throw X509ValidationException(
                "No trust anchors available: provide trustAnchors, include a trusted root, or enable system trust anchors."
            )
        }

        certificateReferences += certificateDers.mapIndexed { index, certificate ->
            certificate.toSecCertificate("certificate at position $index")
        }
        val certificates = if (certificateReferences.size == 1) {
            certificateReferences.single()
        } else {
            createCFArray(certificateReferences).also(arrayReferences::add)
        }

        policyReferences += SecPolicyCreateBasicX509()
            ?: throw X509ValidationException("Certificate validation failed: could not create the X.509 policy.")
        if (enableRevocation) {
            policyReferences += SecPolicyCreateRevocation(kSecRevocationUseAnyAvailableMethod)
                ?: throw X509ValidationException("Certificate validation failed: could not create the revocation policy.")
        }
        val policies = if (policyReferences.size == 1) {
            policyReferences.single()
        } else {
            createCFArray(policyReferences).also(arrayReferences::add)
        }

        val trust = alloc<SecTrustRefVar>().apply { value = null }
        checkStatus(
            operation = "create the certificate trust object",
            status = SecTrustCreateWithCertificates(certificates, policies, trust.ptr),
        )
        trustReference = trust.value
            ?: throw X509ValidationException("Certificate validation failed: trust object was not created.")

        if (anchorDers.isNotEmpty()) {
            val anchorReferences = anchorDers.mapIndexed { index, certificate ->
                certificate.toSecCertificate("trust anchor at position $index")
            }
            val anchorsArray = createCFArray(anchorReferences).also(arrayReferences::add)
            certificateReferences += anchorReferences

            checkStatus(
                operation = "set certificate trust anchors",
                status = SecTrustSetAnchorCertificates(trustReference, anchorsArray),
            )
            checkStatus(
                operation = "configure certificate trust anchors",
                status = SecTrustSetAnchorCertificatesOnly(trustReference, !enableSystemTrustAnchors),
            )
        }

        /*
         * Kotlin/Native's Security interop does not reliably expose the
         * SecTrustResultType out-parameter on the current SDK. Keep the
         * Security.framework evaluation as the platform boundary and run the
         * common explicit-chain verifier below to fail closed when the native
         * result cannot be inspected.
         */
        val result = alloc<SecTrustResultTypeVar>().apply { value = 0u }
        val status = SecTrustEvaluate(trustReference, result.ptr)
        if (status != errSecSuccess) {
            val securityError = NSError(
                domain = "NSOSStatusErrorDomain",
                code = status.toLong(),
                userInfo = null,
            )
            val description =
                "${securityError.domain}(${securityError.code}): ${securityError.localizedDescription}"
            throw X509ValidationException(
                "Certificate path invalid: $description"
            )
        }
        validateCertificateChainWithExplicitTrust(
            leaf = leaf,
            chain = chain,
            trustAnchors = trustAnchors,
            enableTrustedChainRoot = enableTrustedChainRoot,
        )
    } catch (cause: X509ValidationException) {
        throw cause
    } catch (cause: Exception) {
        throw X509ValidationException("Certificate validation failed: ${cause.message}", cause)
    } finally {
        trustReference?.let(::CFRelease)
        arrayReferences.forEach(::CFRelease)
        policyReferences.forEach(::CFRelease)
        certificateReferences.forEach(::CFRelease)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun CertificateDer.toSecCertificate(description: String): SecCertificateRef {
    val byteArray = bytes.toByteArray()
    val data = CFBridgingRetain(
        memScoped {
            NSData.create(bytes = allocArrayOf(byteArray), length = byteArray.size.toULong())
        }
    ) ?: throw X509ValidationException("Certificate validation failed: could not create certificate data.")
    return try {
        SecCertificateCreateWithData(kCFAllocatorDefault, data as CFDataRef)
            ?: throw X509ValidationException(
                "Certificate chain validation failed: invalid X.509 DER in $description."
            )
    } finally {
        CFBridgingRelease(data)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun MemScope.createCFArray(values: List<COpaquePointer>): CFArrayRef {
    val pointers = allocArrayOf(*values.toTypedArray())
    return CFArrayCreate(
        allocator = kCFAllocatorDefault,
        values = pointers,
        numValues = values.size.toLong(),
        callBacks = kCFTypeArrayCallBacks.ptr,
    ) ?: throw X509ValidationException("Certificate validation failed: could not create a Core Foundation array.")
}

private fun checkStatus(operation: String, status: Int) {
    if (status != errSecSuccess) {
        throw X509ValidationException("Certificate validation failed: could not $operation (OSStatus $status).")
    }
}
