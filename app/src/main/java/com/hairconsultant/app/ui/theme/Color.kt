package com.hairconsultant.app.ui.theme

import androidx.compose.ui.graphics.Color

// Monochrome barbershop palette: a black shell (grey in dark mode) for headers and bars, neutral
// grounds, oxblood for the one primary action per screen, and amber only for camera warnings.
val ShellBlack = Color(0xFF111111)
val ShellGrey = Color(0xFF2C2C2E)
val OnShell = Color(0xFFF2F2F2)
val OnShellMuted = Color(0xFFA3A3A3)
val OnShellMutedDark = Color(0xFFB8B8BA)
/** Tile outlines drawn on the shell, e.g. the face-shape legend in the Home header. */
val ShellLine = Color(0xFF3A3A3A)
val ShellLineDark = Color(0xFF505052)
/** Active nav pill and avatar tile: a step lighter than the shell it sits on. */
val ShellIndicator = Color(0xFF3A3A3A)
val ShellIndicatorDark = Color(0xFF4A4A4C)

val Oxblood = Color(0xFF7A1E2C)
val OxbloodDark = Color(0xFFA13448)

/** Filled face-shape fit tiles and reached stage stamps: the strongest ink on the surface. */
val MarkInk = Color(0xFF161616)
val MarkInkDark = Color(0xFFEDEDED)

val WarnAmber = Color(0xFFB26A00)
val WarnAmberDark = Color(0xFFF0B54D)

val GroundLight = Color(0xFFF2F2F2)
val GroundDark = Color(0xFF121212)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceDark = Color(0xFF1C1C1E)
val SurfaceVariantLight = Color(0xFFE4E4E4)
val SurfaceVariantDark = Color(0xFF262628)
val TileLine = Color(0xFFD4D4D4)
val TileLineDark = Color(0xFF38383A)

val InkLight = Color(0xFF161616)
val InkDark = Color(0xFFEDEDED)
val InkMutedLight = Color(0xFF5A5A5A)
val InkMutedDark = Color(0xFFA6A6A6)

/** Background behind haircut cutouts: a dark "mirror" well in both themes. */
val ImageWell = Color(0xFF1C1C1C)
val ImageWellDark = Color(0xFF0B0B0B)

/** Camera screens stay dark in both themes; overlays sit on this translucent black. */
val CameraScrim = Color(0xE0111111)

val ErrorRed = Color(0xFFB3261E)
val ErrorRedDark = Color(0xFFF2B8B5)
