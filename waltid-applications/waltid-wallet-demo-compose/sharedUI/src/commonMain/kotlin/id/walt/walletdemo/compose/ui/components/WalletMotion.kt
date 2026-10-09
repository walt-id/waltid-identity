package id.walt.walletdemo.compose.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MotionScheme
import kotlin.math.PI

/** Mirrors SwiftUI WalletMotion's spring; native navigation and sheet gestures remain OS-owned. */
internal object WalletMotion {
    private const val NavigationResponse = .38f
    private const val DampingRatio = .92f
    private val easeInOut = CubicBezierEasing(.42f, 0f, .58f, 1f)
    private val easeOut = CubicBezierEasing(0f, 0f, .58f, 1f)

    val standard: MotionScheme = WalletMotionScheme(reduceMotion = false)
    val reduced: MotionScheme = WalletMotionScheme(reduceMotion = true)

    // Material sheets use fastEffects for positional dismissal. Keep entrance and exit spatial.
    val sheet: MotionScheme = object : MotionScheme by standard {
        override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = navigation()
    }

    fun <T> navigation(reduceMotion: Boolean = false): FiniteAnimationSpec<T> =
        spatial(NavigationResponse, reduceMotion)

    fun <T> disclosure(reduceMotion: Boolean = false): FiniteAnimationSpec<T> =
        if (reduceMotion) snap() else tween(180, easing = easeInOut)

    fun <T> feedback(reduceMotion: Boolean = false): FiniteAnimationSpec<T> =
        if (reduceMotion) snap() else tween(160, easing = easeOut)

    private fun <T> spatial(response: Float, reduceMotion: Boolean): FiniteAnimationSpec<T> {
        if (reduceMotion) return snap()
        // With unit mass, response is the natural period: stiffness = (2π / response)².
        val frequency = (2 * PI / response).toFloat()
        return spring(dampingRatio = DampingRatio, stiffness = frequency * frequency)
    }

    private class WalletMotionScheme(private val reduceMotion: Boolean) : MotionScheme {
        override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = navigation(reduceMotion)
        override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = spatial(.26f, reduceMotion)
        override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = spatial(.46f, reduceMotion)
        override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = disclosure(reduceMotion)
        override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = feedback(reduceMotion)
        override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> =
            if (reduceMotion) snap() else tween(240, easing = easeInOut)
    }
}
