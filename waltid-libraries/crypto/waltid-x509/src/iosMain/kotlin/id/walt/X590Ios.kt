package id.walt.x509

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFArrayAppendValue
import platform.CoreFoundation.CFArrayCreateMutable
import platform.CoreFoundation.CFArrayRef
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFAllocatorDefault
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSError
import platform.Security.SecCertificateCreateWithData
import platform.Security.SecCertificateRef
import platform.Security.SecPolicyCreateBasicX509
import platform.Security.SecPolicyCreateRevocation
import platform.Security.SecPolicyRef
import platform.Security.SecTrustCreateWithCertificates
import platform.Security.SecTrustEvaluateWithError
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
        val certificateDers = (listOf(leaf) + chain)
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
            certificateReferences += anchorDers.mapIndexed { index, certificate ->
                certificate.toSecCertificate("trust anchor at position $index")
            }
            val anchorsArray = createCFArray(
                certificateReferences.takeLast(anchorDers.size)
            ).also(arrayReferences::add)

            checkStatus(
                operation = "set certificate trust anchors",
                status = SecTrustSetAnchorCertificates(trustReference, anchorsArray),
            )
            checkStatus(
                operation = "configure certificate trust anchors",
                status = SecTrustSetAnchorCertificatesOnly(trustReference, !enableSystemTrustAnchors),
            )
        }

        val error = alloc<CFErrorRefVar>().apply { value = null }
        if (!SecTrustEvaluateWithError(trustReference, error.ptr)) {
            val description = error.value?.let {
                (CFBridgingRelease(it) as NSError).localizedDescription
            } ?: "unknown Security framework error"
            throw X509ValidationException(
                "Certificate path invalid: $description"
            )
        }
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
    val data = byteArray.usePinned { pinned ->
        CFDataCreate(
            allocator = kCFAllocatorDefault,
            bytes = pinned.addressOf(0).reinterpret(),
            length = byteArray.size.toLong(),
        )
    } ?: throw X509ValidationException("Certificate validation failed: could not create certificate data.")
    return try {
        SecCertificateCreateWithData(kCFAllocatorDefault, data)
            ?: throw X509ValidationException(
                "Certificate chain validation failed: invalid X.509 DER in $description."
            )
    } finally {
        CFRelease(data)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun MemScope.createCFArray(values: List<COpaquePointer>): CFArrayRef {
    val array = CFArrayCreateMutable(
        allocator = kCFAllocatorDefault,
        capacity = values.size.toLong(),
        callBacks = null,
    ) ?: throw X509ValidationException("Certificate validation failed: could not create a Core Foundation array.")
    values.forEach { value -> CFArrayAppendValue(array, value) }
    return array
}

private fun checkStatus(operation: String, status: Int) {
    if (status != errSecSuccess) {
        throw X509ValidationException("Certificate validation failed: could not $operation (OSStatus $status).")
    }
}
