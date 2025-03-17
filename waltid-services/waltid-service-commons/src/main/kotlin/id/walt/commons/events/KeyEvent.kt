package id.walt.commons.events

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
class KeyEvent(
  override val originator: String?,
  override val organization: String,
  override val target: String,
  override val timestamp: Long,
  override val action: Action,
  override val status: Status,
  override val callId: String?,
  override val error: String?,

  val keyEventType: KeyEventType,
  val keyAlgorithm: String
) : Event(EventType.KeyEvent) {
}
