package id.walt.policies.policies.status.bit

class BitValueReaderFactory {
    fun new(strategy: BitRepresentationStrategy): BitValueReader = BitValueReader(strategy)
}