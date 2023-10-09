package id.walt.didlib.did.registrar.local.cheqd.models.job.didstates

//import com.beust.klaxon.TypeAdapter
//import com.beust.klaxon.TypeFor
import kotlinx.serialization.Serializable

@Serializable
//@TypeFor(field = "state", adapter = DidStateAdapter::class)
open class DidState(
    val state: String,
) {
    enum class States {
        Action,
        Finished,
        Failed;

        override fun toString(): String = name.lowercase()

    }
}

/*
class DidStateAdapter : TypeAdapter<DidState> {
    override fun classFor(type: Any): KClass<out DidState> = when (type as String) {
        DidState.States.Action.toString() -> ActionDidState::class
        DidState.States.Finished.toString() -> FinishedDidState::class
        DidState.States.Failed.toString() -> FailedDidState::class
        else -> throw IllegalArgumentException("Unknown did state: $type")
    }

}
*/
