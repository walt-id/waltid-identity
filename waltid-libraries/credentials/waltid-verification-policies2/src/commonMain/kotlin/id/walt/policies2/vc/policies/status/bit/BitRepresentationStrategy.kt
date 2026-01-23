package id.walt.policies2.vc.policies.status.bit

interface BitRepresentationStrategy {
    operator fun invoke(): IntProgression
}

class BigEndianRepresentation : id.walt.policies2.vc.policies.status.bit.BitRepresentationStrategy {
    override fun invoke(): IntProgression = 7 downTo 0
}

class LittleEndianRepresentation : id.walt.policies2.vc.policies.status.bit.BitRepresentationStrategy {
    override fun invoke(): IntProgression = 0..7
}
