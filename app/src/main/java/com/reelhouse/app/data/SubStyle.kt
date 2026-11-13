package com.reelhouse.app.data

import android.graphics.Typeface

/** Maps the saved subtitle preferences to concrete values (used by the player and the Settings preview). */
object SubStyle {
    val fonts = listOf("sans" to "Sans", "serif" to "Serif", "mono" to "Mono", "casual" to "Casual")
    val sizes = listOf("small" to "Small", "medium" to "Medium", "large" to "Large", "xlarge" to "Extra large")
    val colors = listOf("white" to "White", "yellow" to "Yellow", "cyan" to "Cyan", "green" to "Green")
    val backgrounds = listOf("none" to "None", "dim" to "Dim", "black" to "Black")

    fun typeface(key: String): Typeface = when (key) {
        "serif" -> Typeface.SERIF
        "mono" -> Typeface.MONOSPACE
        "casual" -> Typeface.create("casual", Typeface.NORMAL)
        else -> Typeface.SANS_SERIF
    }

    fun sizeSp(key: String): Float = when (key) {
        "small" -> 16f
        "large" -> 26f
        "xlarge" -> 32f
        else -> 20f
    }

    fun fg(key: String): Int = when (key) {
        "yellow" -> 0xFFFFEB3B.toInt()
        "cyan" -> 0xFF4DD0E1.toInt()
        "green" -> 0xFF69F0AE.toInt()
        else -> 0xFFFFFFFF.toInt()
    }

    fun bg(key: String): Int = when (key) {
        "none" -> 0x00000000
        "black" -> 0xFF000000.toInt()
        else -> 0x99000000.toInt()
    }
}
