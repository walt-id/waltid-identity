package id.walt.webwallet.service.events

sealed interface EventType {
    data object Account : EventType {
        data object Create : Action(this.javaClass.simpleName)
        data object Update : Action(this.javaClass.simpleName)
        data object Delete : Action(this.javaClass.simpleName)
        data object Login : Action(this.javaClass.simpleName)
    }

    data object Key : EventType {
        data object Create : Action(this.javaClass.simpleName)
        data object Rotate : Action(this.javaClass.simpleName)
        data object Delete : Action(this.javaClass.simpleName)
        data object Import : Action(this.javaClass.simpleName)
        data object Export : Action(this.javaClass.simpleName)
        data object Sign : Action(this.javaClass.simpleName)
    }

    data object Did : EventType {
        data object Create : Action(this.javaClass.simpleName)
        data object Update : Action(this.javaClass.simpleName)
        data object Delete : Action(this.javaClass.simpleName)
        data object Register : Action(this.javaClass.simpleName)
        data object Resolve : Action(this.javaClass.simpleName)
    }

    data object Credential : EventType {
        data object Receive : Action(this.javaClass.simpleName)
        data object Accept : Action(this.javaClass.simpleName)
        data object Reject : Action(this.javaClass.simpleName)
        data object Delete : Action(this.javaClass.simpleName)
        data object Present : Action(this.javaClass.simpleName)
    }

    sealed class Action(val type: String)
}