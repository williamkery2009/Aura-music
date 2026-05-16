package com.aura.musicplayer

import android.graphics.Bitmap
import android.graphics.Color
import androidx.palette.graphics.Palette

/**
 * Extracts dominant/vibrant colors from album artwork bitmap.
 * Used to tint the notification and status bar dynamically.
 */
object DynamicColorExtractor {

    data class AuraColors(
        val primary:   Int,   // vibrant or dominant
        val secondary: Int,   // muted
        val onPrimary: Int,   // text on primary (white or black)
        val dark:      Int,   // darkened primary for status bar
    )

    private val DEFAULT = AuraColors(
        primary   = Color.parseColor("#fc3d6a"),
        secondary = Color.parseColor("#9b3cf7"),
        onPrimary = Color.WHITE,
        dark      = Color.parseColor("#3d000f"),
    )

    fun extract(bitmap: Bitmap, onResult: (AuraColors) -> Unit) {
        Palette.from(bitmap)
            .maximumColorCount(16)
            .generate { palette ->
                if (palette == null) { onResult(DEFAULT); return@generate }

                val vibrant     = palette.getVibrantColor(0)
                val dominant    = palette.getDominantColor(0)
                val muted       = palette.getMutedColor(palette.getDarkMutedColor(0))
                val lightVib    = palette.getLightVibrantColor(vibrant)

                val primary = when {
                    vibrant   != 0 -> vibrant
                    dominant  != 0 -> dominant
                    else           -> DEFAULT.primary
                }
                val secondary = if (muted != 0) muted else DEFAULT.secondary

                // Darken primary for status bar: multiply luminance by 0.3
                val hsv = FloatArray(3)
                Color.colorToHSV(primary, hsv)
                hsv[2] *= 0.25f
                val dark = Color.HSVToColor(hsv)

                // Choose text color for contrast
                val luminance = Color.luminance(primary)
                val onPrimary = if (luminance > 0.4f) Color.BLACK else Color.WHITE

                onResult(AuraColors(primary, secondary, onPrimary, dark))
            }
    }

    /** Lighten a color by factor (0..1 = darken, 1..2 = lighten) */
    fun lighten(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * factor).coerceIn(0f, 1f)
        return Color.HSVToColor(hsv)
    }
}
