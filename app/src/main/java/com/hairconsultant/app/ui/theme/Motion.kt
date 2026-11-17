package com.hairconsultant.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing

/** For anything entering, or answering a tap: fast off the mark, so the UI feels like it heard you. */
val EaseOutStrong = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** For something already on screen moving to a new place (a sliding indicator). */
val EaseInOutStrong = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
