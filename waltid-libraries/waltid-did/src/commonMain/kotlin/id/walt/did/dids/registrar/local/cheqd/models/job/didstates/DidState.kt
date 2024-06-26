package id.walt.did.dids.registrar.local.cheqd.models.job.didstates

import id.walt.did.dids.registrar.local.cheqd.models.job.didstates.action.ActionDidState
import id.walt.did.dids.registrar.local.cheqd.models.job.didstates.failed.FailedDidState
import id.walt.did.dids.registrar.local.cheqd.models.job.didstates.finished.FinishedDidState
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class, ExperimentalSerializationApi::class)
@JsExport
@Polymorphic
@Serializable
@JsonClassDiscriminator("state")
abstract class DidState {
    abstract val state: String
}

@OptIn(ExperimentalJsExport::class)
@JsExport
val didStateSerializationModule = SerializersModule {
    polymorphic(DidState::class) {
        subclass(ActionDidState::class)
        subclass(FailedDidState::class)
        subclass(FinishedDidState::class)
    }
}
