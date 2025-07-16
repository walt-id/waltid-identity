package id.walt.commons.logging

import io.klogging.rendering.*

enum class RenderStrings(val renderString: RenderString) {

    SIMPLE(RENDER_SIMPLE),
    ISO8601(RENDER_ISO8601),
    ANSI(RENDER_ANSI),
    ANSI_LONG(renderAnsi(15, 35)),
    CLEF(RENDER_CLEF),
    GELF(RENDER_GELF),
    ECS(RENDER_ECS),
    ECS_DOTNET(RENDER_ECS_DOTNET),
    STANDARD(RENDER_STANDARD)

}

object LogStringManager {
    val DEFAULT_RENDER_STRING = RenderStrings.ANSI

    var selectedRenderString = DEFAULT_RENDER_STRING
}
