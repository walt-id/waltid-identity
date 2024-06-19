package cbor.internal

internal const val TDATE = 0L
internal const val TIME = 1L
internal const val ENCODED_CBOR = 24L
internal const val FULL_DATE_STR = 1004L
internal const val FULL_DATE_INT = 100L
internal const val FALSE = 0xf4
internal const val TRUE = 0xf5
internal const val NULL = 0xf6
internal const val COSE_SIGN1 = 18L

internal const val NEXT_HALF = 0xf9
internal const val NEXT_FLOAT = 0xfa
internal const val NEXT_DOUBLE = 0xfb

internal const val BEGIN_ARRAY = 0x9f
internal const val BEGIN_MAP = 0xbf
internal const val BREAK = 0xff

internal const val ADDITIONAL_INFORMATION_INDEFINITE_LENGTH = 0x1f

internal const val HEADER_BYTE_STRING: Byte = 0b010_00000
internal const val HEADER_STRING: Byte = 0b011_00000
internal const val HEADER_NEGATIVE: Byte = 0b001_00000
internal const val HEADER_ARRAY: Int = 0b100_00000
internal const val HEADER_MAP: Int = 0b101_00000
internal const val HEADER_TAG: Int = 0b110_00000

/** Value to represent an indefinite length CBOR item within a "length stack". */
internal const val LENGTH_STACK_INDEFINITE = -1

internal const val HALF_PRECISION_EXPONENT_BIAS = 15
internal const val HALF_PRECISION_MAX_EXPONENT = 0x1f
internal const val HALF_PRECISION_MAX_MANTISSA = 0x3ff

internal const val SINGLE_PRECISION_EXPONENT_BIAS = 127
internal const val SINGLE_PRECISION_MAX_EXPONENT = 0xFF

internal const val SINGLE_PRECISION_NORMALIZE_BASE = 0.5f

