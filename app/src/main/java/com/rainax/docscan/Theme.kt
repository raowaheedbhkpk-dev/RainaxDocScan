package com.rainax.docscan

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val Teal = Color(0xFF14B88E)

/** Dark theme: charcoal background, lighter sheet for lists, teal accent. */
val AppDark = darkColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1F4D43),
    onPrimaryContainer = Color.White,
    secondaryContainer = Color(0xFF1F4D43),
    background = Color(0xFF232427),
    onBackground = Color.White,
    surface = Color(0xFF232427),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2D2E32),
    onSurfaceVariant = Color(0xFFB0B3B8),
)
