/*
 * Copyright 2017-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalSerializationApi::class)

package cbor.internal

import cbor.ByteString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor


@OptIn(ExperimentalSerializationApi::class)
internal fun SerialDescriptor.isByteString(index: Int): Boolean {
    return getElementAnnotations(index).find { it is ByteString } != null
}


