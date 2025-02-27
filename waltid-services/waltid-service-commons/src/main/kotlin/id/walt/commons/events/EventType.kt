package id.walt.commons.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class EventType {
  @SerialName("IssuanceEvent") IssuanceEvent,
  @SerialName("VerificationEvent") VerificationEvent,
  @SerialName("KeyEvent") KeyEvent,
  @SerialName("DIDEvent") DIDEvent,
  @SerialName("CredentialWalletEvent") CredentialWalletEvent,
  @SerialName("IssuanceWalletEvent") IssuanceWalletEvent,
  @SerialName("PresentationWalletEvent") PresentationWalletEvent;

  override fun toString(): String {
    return when (this) {
      IssuanceEvent -> "IssuanceEvent"
      VerificationEvent -> "VerificationEvent"
      DIDEvent -> "DIDEvent"
      KeyEvent -> "KeyEvent"
      CredentialWalletEvent -> "CredentialWalletEvent"
      IssuanceWalletEvent -> "IssuanceWalletEvent"
      PresentationWalletEvent -> "PresentationWalletEvent"
    }
  }
}
