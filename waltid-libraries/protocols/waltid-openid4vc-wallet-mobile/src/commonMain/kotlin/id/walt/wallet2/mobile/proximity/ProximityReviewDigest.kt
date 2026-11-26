@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile.proximity

import kotlinx.serialization.ExperimentalSerializationApi
import id.walt.credentials.formats.MdocsCredential
import id.walt.mdoc.objects.edition2.document.Document
import id.walt.mdoc.proximity.MdocApplicationAuthorization
import id.walt.mdoc.proximity.MdocHolderRequestContext
import id.walt.mdoc.proximity.SelectedDocument
import id.walt.wallet2.data.ResolvedHolderKey
import id.walt.wallet2.data.StoredCredential
import kotlin.io.encoding.Base64
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.cbor.CborElement
import org.kotlincrypto.hash.sha2.SHA256

internal data class InventoryDocument(
    val stored: StoredCredential,
    val credential: MdocsCredential,
    val document: Document,
    val holderKey: ResolvedHolderKey,
    val deviceAuthentication: ProximityDeviceAuthenticationMethod,
    val issuerAuthorityKeyIdentifiers: List<ByteString>,
)

internal data class ApplicationProfileSnapshot(
    val public: ProximityApplicationAuthorization,
    val lower: MdocApplicationAuthorization,
    val decodedDeviceElements: List<Pair<ProximityDeviceSignedElement, CborElement>>,
)

internal fun choiceDigest(
    reviewId: ProximityReviewId,
    submission: ProximitySubmission,
    profiles: List<ApplicationProfileSnapshot>,
): ByteString {
    val values = buildList {
        add("walt.id/mobile-wallet-proximity-choice/v1".encodeToByteArray())
        add(reviewId.value.encodeToByteArray())
        add(byteArrayOf(if (submission.continueAfterResponse) 1 else 0))
        add(intBytes(submission.documents.size))
        submission.documents.forEach { document ->
            add(intBytes(document.requestIndex))
            add(document.credentialId.encodeToByteArray())
            add(intBytes(document.disclosedElements.size))
            document.disclosedElements.sortedWith(compareBy({ it.namespace }, { it.elementIdentifier })).forEach {
                add(it.namespace.encodeToByteArray())
                add(it.elementIdentifier.encodeToByteArray())
            }
        }
        add(intBytes(profiles.size))
        profiles.forEach { profile ->
            val authorization = profile.public
            add(authorization.profileId.encodeToByteArray())
            add(authorization.displayTitle.encodeToByteArray())
            add(authorization.resultBindingDigestBase64Url.fromBase64Url())
            add(intBytes(authorization.details.size))
            authorization.details.forEach {
                add(it.id.encodeToByteArray())
                add(it.label.encodeToByteArray())
                add(it.value.encodeToByteArray())
            }
            add(intBytes(authorization.compatibleCredentialIds.size))
            authorization.compatibleCredentialIds.sorted().forEach { add(it.encodeToByteArray()) }
            add(intBytes(authorization.deviceSignedElements.size))
            authorization.deviceSignedElements.forEach {
                add(it.credentialId.encodeToByteArray())
                add(it.namespace.encodeToByteArray())
                add(it.elementIdentifier.encodeToByteArray())
                add(it.valueCborBase64Url.fromBase64Url())
            }
        }
    }
    return ByteString(SHA256().digest(values.fold(intBytes(values.size)) { bytes, value -> bytes + lengthPrefixed(value) }))
}

internal fun snapshotDigest(
    context: MdocHolderRequestContext,
    inventory: List<InventoryDocument>,
    eligible: List<SelectedDocument>,
    review: ProximityReview,
    profiles: List<ApplicationProfileSnapshot>,
): ByteString {
    val values = buildList {
        add("walt.id/mobile-wallet-proximity-snapshot/v1".encodeToByteArray())
        add(context.request.encodedCopy())
        add(context.transcript.encodedCopy())
        add(context.exchange.toString().encodeToByteArray())
        inventory.sortedBy { it.stored.id }.forEach { item ->
            add(item.stored.id.encodeToByteArray())
            add(item.credential.docType.encodeToByteArray())
            add(requireNotNull(item.credential.signed).encodeToByteArray())
            val binding = item.holderKey.binding
            add(binding.schemaVersion.toString().encodeToByteArray())
            add(binding.keyReference.encodeToByteArray())
            add(binding.publicKeyThumbprint.algorithm.encodeToByteArray())
            add(binding.publicKeyThumbprint.value.encodeToByteArray())
            add(item.deviceAuthentication.name.encodeToByteArray())
        }
        eligible.sortedWith(compareBy(SelectedDocument::requestIndex, SelectedDocument::credentialId)).forEach { option ->
            add(option.requestIndex.toString().encodeToByteArray())
            add(option.credentialId.encodeToByteArray())
            option.elements.sortedWith(compareBy({ it.reference.namespace }, { it.reference.elementIdentifier }))
                .forEach { element ->
                    add(element.reference.namespace.encodeToByteArray())
                    add(element.reference.elementIdentifier.encodeToByteArray())
                    add(byteArrayOf(if (element.intentToRetain) 1 else 0))
                }
        }
        review.readerAuthentication.forEach { entry ->
            add((if (entry.scope is ProximityReaderAuthenticationScope.Document) "Document" else "WholeRequest").encodeToByteArray())
            add((entry.scope.documentRequestIndex?.toString() ?: "-").encodeToByteArray())
            add(entry.authenticationIndex.toString().encodeToByteArray())
            add(entry.validity.name.encodeToByteArray())
            add(entry.trust.name.encodeToByteArray())
            add(entry.certificatePath.name.encodeToByteArray())
            add(entry.revocation.name.encodeToByteArray())
            add(entry.rical.name.encodeToByteArray())
            add(entry.displayName.orEmpty().encodeToByteArray())
            add(entry.reason.orEmpty().encodeToByteArray())
        }
        profiles.forEach { profile ->
            add(profile.public.profileId.encodeToByteArray())
            add(profile.public.displayTitle.encodeToByteArray())
            profile.public.details.forEach { detail ->
                add(detail.id.encodeToByteArray())
                add(detail.label.encodeToByteArray())
                add(detail.value.encodeToByteArray())
            }
            add(profile.public.resultBindingDigestBase64Url.fromBase64Url())
            profile.public.compatibleCredentialIds.sorted().forEach { add(it.encodeToByteArray()) }
            profile.public.deviceSignedElements.forEach { element ->
                add(element.credentialId.encodeToByteArray())
                add(element.namespace.encodeToByteArray())
                add(element.elementIdentifier.encodeToByteArray())
                add(element.valueCborBase64Url.fromBase64Url())
            }
        }
    }
    val material = values.fold(intBytes(values.size)) { bytes, value -> bytes + lengthPrefixed(value) }
    return ByteString(SHA256().digest(material))
}

internal fun String.fromBase64Url(): ByteArray =
    Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(this)

private fun intBytes(value: Int): ByteArray = byteArrayOf(
    (value ushr 24).toByte(),
    (value ushr 16).toByte(),
    (value ushr 8).toByte(),
    value.toByte(),
)

internal fun lengthPrefixed(value: ByteArray): ByteArray = intBytes(value.size) + value
