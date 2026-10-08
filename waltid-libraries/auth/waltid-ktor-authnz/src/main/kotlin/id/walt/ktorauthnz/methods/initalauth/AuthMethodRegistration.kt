package id.walt.ktorauthnz.methods.initalauth

@Deprecated("Moved to id.walt.ktorauthnz.methods.initialauth", ReplaceWith("id.walt.ktorauthnz.methods.initialauth.AuthMethodRegistration"))
typealias AuthMethodRegistration = id.walt.ktorauthnz.methods.initialauth.AuthMethodRegistration

@Deprecated("Moved to id.walt.ktorauthnz.methods.initialauth", ReplaceWith("id.walt.ktorauthnz.methods.initialauth.AuthMethodRegistrationWrapper"))
typealias AuthMethodRegistrationWrapper = id.walt.ktorauthnz.methods.initialauth.AuthMethodRegistrationWrapper

@Deprecated("Moved to id.walt.ktorauthnz.methods.initialauth", ReplaceWith("id.walt.ktorauthnz.methods.initialauth.setInitialAuthJsonObjectType(jsonObject, type)"))
fun setInitialAuthJsonObjectType(jsonObject: kotlinx.serialization.json.JsonObject, type: String) =
    id.walt.ktorauthnz.methods.initialauth.setInitialAuthJsonObjectType(jsonObject, type)
