package id.walt.logging

import io.klogging.rendering.*

enum class RenderStrings(val renderString: RenderString) {

    SIMPLE(RENDER_SIMPLE),
    ISO8601(RENDER_ISO8601),
    ANSI(RENDER_ANSI),
    CLEF(RENDER_CLEF),
    GELD(RENDER_GELF),
    ECS(RENDER_ECS),


}

object LogStringManager {
    var selectedRenderString = RenderStrings.ANSI
}
