# Adding New mdoc Types to waltid-mdoc-credentials2

This guide explains how to add support for new mdoc document types. The library currently supports:

- **mDL** (Mobile Driving Licence) - `org.iso.18013.5.1`
- **Photo ID** - `org.iso.23220.photoid.1` and `org.iso.23220.1`
- **EU PID** (Person Identification Data) - `eu.europa.ec.eudi.pid.1`

## Overview

Adding a new mdoc type requires:

1. Creating a data class implementing `MdocData`
2. Implementing `MdocCompanion` to register serializers
3. Adding the type to `CredentialManager.credentials`
4. Ensuring `CredentialManager.init()` is called at startup

## Step-by-Step Guide

### Step 1: Create the Data Class

Create a new file: `src/commonMain/kotlin/id/walt/mdoc/credsdata/[yourtype]/YourType.kt`

```kotlin
@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.credsdata.yourtype

import id.walt.mdoc.credsdata.MdocCompanion
import id.walt.mdoc.credsdata.MdocData
import id.walt.mdoc.encoding.ByteArrayBase64UrlSerializer
import id.walt.mdoc.objects.MdocsCborSerializer
import kotlinx.datetime.LocalDate
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.cbor.ByteString

@Serializable
data class YourType(
    @SerialName("field_name") val fieldName: String,
    @SerialName("birth_date") val birthDate: LocalDate,
    @ByteString
    @Serializable(with = ByteArrayBase64UrlSerializer::class)
    @SerialName("portrait") val portrait: ByteArray,
    @SerialName("age") val age: UInt? = null,
) : MdocData {
    
    companion object : MdocCompanion {
        override fun registerSerializationTypes() {
            MdocsCborSerializer.register(
                mapOf(
                    "birth_date" to LocalDate.serializer(),
                    "portrait" to ByteArraySerializer(),
                    "age" to UInt.serializer(),
                ),
                "your.namespace.here"
            )
        }
    }
}
```

**Key Points:**

- Implement `MdocData` interface
- Use `@Serializable` annotation
- Use `@SerialName` to map properties to mdoc element identifiers
- For `ByteArray` fields: add `@ByteString` and `@Serializable(with = ByteArrayBase64UrlSerializer::class)`
- Register all non-String serializers in `registerSerializationTypes()`

### Step 2: Register Serializers

In `registerSerializationTypes()`, map each element identifier to its serializer:

**Common Serializers:**

| Kotlin Type | Serializer |
|------------|------------|
| `String` | `String.serializer()` (optional, handled automatically) |
| `LocalDate` | `LocalDate.serializer()` |
| `UInt` | `UInt.serializer()` |
| `Boolean` | `Boolean.serializer()` |
| `ByteArray` | `ByteArraySerializer()` |
| `List<T>` | `ListSerializer(T.serializer())` |
| Enum | `EnumType.serializer()` |

### Step 3: Register in CredentialManager

Add your type to `CredentialManager.credentials`:

```kotlin
// src/commonMain/kotlin/id/walt/mdoc/credsdata/CredentialManager.kt
val credentials: List<MdocCompanion> = listOf(
    Mdl,
    PhotoId,
    Pid,
    YourType // Add here
)
```

### Step 4: Initialize

Call `CredentialManager.init()` at application startup:

```kotlin
import id.walt.mdoc.credsdata.CredentialManager

CredentialManager.init()
```

## Advanced Topics

### Multiple Namespaces

Register serializers for each namespace:

```kotlin
override fun registerSerializationTypes() {
    MdocsCborSerializer.register(mapOf(/* ... */), "namespace.1")
    MdocsCborSerializer.register(mapOf(/* ... */), "namespace.2")
}
```

### Fallback Decoders

Handle malformed or older-format documents:

```kotlin
MdocsCborSerializer.registerFallbackDecoder(
    mapOf("portrait" to ListSerializer(Byte.serializer())),
    "your.namespace"
)
```

### Nested Structures

Create separate data classes for nested types:

```kotlin
@Serializable
data class YourType(
    @SerialName("nested_field") val nested: List<NestedType>? = null,
) : MdocData

@Serializable
data class NestedType(
    @SerialName("code") val code: String,
    @SerialName("value") val value: UInt,
)

// Register in companion:
MdocsCborSerializer.register(
    mapOf("nested_field" to ListSerializer(NestedType.serializer())),
    "your.namespace"
)
```

## Reference Implementations

- **mDL**: `src/commonMain/kotlin/id/walt/mdoc/credsdata/mdl/Mdl.kt`
- **Photo ID**: `src/commonMain/kotlin/id/walt/mdoc/credsdata/photoid/PhotoId.kt`
- **EU PID**: `src/commonMain/kotlin/id/walt/mdoc/credsdata/pid/Pid.kt`

## Checklist

- [ ] Create data class implementing `MdocData`
- [ ] Add `@Serializable` and `@SerialName` annotations
- [ ] Implement `MdocCompanion` with `registerSerializationTypes()`
- [ ] Register all serializers for non-String types
- [ ] Add type to `CredentialManager.credentials`
- [ ] Verify `CredentialManager.init()` is called
- [ ] Test with real mdoc data

## Related Documentation

- [mdoc Credentials Documentation](https://docs.walt.id/concepts/digital-credentials/mdoc-mdl-iso)
- [Verification Guide](./verification_guide.md)
- [Library README](../README.md)
