package id.walt.policies2.policies.status.bit

class BitValueReaderFactory {
    fun new(strategy: BitRepresentationStrategy): BitValueReader = BitValueReader(strategy)
}