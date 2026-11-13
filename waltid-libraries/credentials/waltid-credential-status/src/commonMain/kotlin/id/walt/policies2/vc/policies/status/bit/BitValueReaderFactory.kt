package id.walt.policies2.vc.policies.status.bit

class BitValueReaderFactory {
    fun new(strategy: BitRepresentationStrategy): BitValueReader =
        BitValueReader(strategy)
}
