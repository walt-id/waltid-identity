package id.walt.androidSample.app.features.walkthrough

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

interface WalkthroughViewModel {
    val events: Flow<WalkthroughEvent>
    val keyAlgorithmOptions: List<KeyAlgorithmOption>
    val selectedKeyAlgorithm: StateFlow<KeyAlgorithmOption>

    fun onKeyAlgorithmSelected(keyAlgorithmOption: KeyAlgorithmOption)
    fun onGenerateKeyClick()
    fun onProgressToStepTwoClick()


    class Fake : WalkthroughViewModel {
        override val events = emptyFlow<WalkthroughEvent>()
        override val keyAlgorithmOptions = KeyAlgorithmOption.all()
        override val selectedKeyAlgorithm = MutableStateFlow(keyAlgorithmOptions.first())

        override fun onKeyAlgorithmSelected(keyAlgorithmOption: KeyAlgorithmOption) = Unit
        override fun onGenerateKeyClick() = Unit
        override fun onProgressToStepTwoClick() = Unit
    }

    class Default : ViewModel(), WalkthroughViewModel {

        private val _events = Channel<WalkthroughEvent>()
        override val events = _events.receiveAsFlow()

        override val keyAlgorithmOptions = KeyAlgorithmOption.all()
        override val selectedKeyAlgorithm = MutableStateFlow(keyAlgorithmOptions.first())

        override fun onKeyAlgorithmSelected(keyAlgorithmOption: KeyAlgorithmOption) {
            println("lekker $keyAlgorithmOption")
            selectedKeyAlgorithm.update { keyAlgorithmOption }
        }

        override fun onGenerateKeyClick() {
            TODO("Not yet implemented")
        }

        override fun onProgressToStepTwoClick() {
            viewModelScope.launch { _events.send(WalkthroughEvent.NavigateEvent.NavigateToStepTwo) }
        }

    }
}


sealed interface WalkthroughEvent {
    sealed interface NavigateEvent : WalkthroughEvent {
        data object NavigateToStepTwo : NavigateEvent
    }
}

sealed interface KeyAlgorithmOption {
    data object RSA : KeyAlgorithmOption
    data object Secp256r1 : KeyAlgorithmOption

    companion object {
        fun all(): List<KeyAlgorithmOption> = listOf(RSA, Secp256r1)
    }
}