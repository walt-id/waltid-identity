package id.walt.commons.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class EventType {
  @SerialName("IssuanceEvent") IssuanceEvent,
  @SerialName("VerificationEvent") VerificationEvent,
  @SerialName("KeyEvent") KeyEvent,
  @SerialName("DIDEvent") DIDEvent;

  override fun toString(): String {
    return when (this) {
      IssuanceEvent -> "IssuanceEvent"
      VerificationEvent -> "VerificationEvent"
      DIDEvent -> "DIDEvent"
      KeyEvent -> "KeyEvent"
    }
  }
}
