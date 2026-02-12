package id.walt.policies2.vc.policies.status.bit

interface BitRepresentationStrategy {
    operator fun invoke(): IntProgression
}

class BigEndianRepresentation : BitRepresentationStrategy {
    override fun invoke(): IntProgression = 7 downTo 0
}

class LittleEndianRepresentation : BitRepresentationStrategy {
    override fun invoke(): IntProgression = 0..7
}
