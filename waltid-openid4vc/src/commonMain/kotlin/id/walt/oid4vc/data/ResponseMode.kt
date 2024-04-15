package id.walt.oid4vc.data

import kotlinx.serialization.SerialName

enum class ResponseMode {
    query,
    fragment,
    form_post,
    direct_post,
    post;
}
