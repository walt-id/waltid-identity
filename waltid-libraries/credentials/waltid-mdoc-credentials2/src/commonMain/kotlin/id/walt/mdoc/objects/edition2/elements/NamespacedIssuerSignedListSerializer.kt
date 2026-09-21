package id.walt.mdoc.objects.edition2.elements

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** Namespace values already contain typed CBOR elements and need no key-dependent serializer. */
object NamespacedIssuerSignedListSerializer : KSerializer<Map<String, IssuerSignedList>> by
    MapSerializer(String.serializer(), IssuerSignedListSerializer)
