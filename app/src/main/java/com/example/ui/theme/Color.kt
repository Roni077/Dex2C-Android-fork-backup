package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Cyber-Silicon Compiler Lab Palette (Dark-first)
val ObsidianVoid = Color(0xFF070C14)
val ObsidianSurface = Color(0xFF0C1420)
val ObsidianContainerLow = Color(0xFF111B2B)
val ObsidianContainer = Color(0xFF162235)
val ObsidianContainerHigh = Color(0xFF1D2C44)
val ObsidianOutline = Color(0xFF2D415E)

val PhosphorMint = Color(0xFF2EE59D)
val PhosphorMintDark = Color(0xFF033824)
val PhosphorMintContainer = Color(0xFF0B422D)
val PhosphorMintOnContainer = Color(0xFFA9F7D4)

val AmberQuartz = Color(0xFFFFB454)
val AmberQuartzDark = Color(0xFF422700)
val AmberQuartzContainer = Color(0xFF3E290B)
val AmberQuartzOnContainer = Color(0xFFFFDDA9)

val ElectricCyan = Color(0xFF4FD1FF)
val ElectricCyanDark = Color(0xFF003547)
val ElectricCyanContainer = Color(0xFF0A3244)
val ElectricCyanOnContainer = Color(0xFFBBEBFF)

val CrimsonSignal = Color(0xFFFF6B7A)
val CrimsonContainer = Color(0xFF4D141D)

val SlateTextPrimary = Color(0xFFEAF1FA)
val SlateTextSecondary = Color(0xFF99ABC4)

// Light Mode Companion Palette
val LightLabBackground = Color(0xFFF3F6FA)
val LightLabSurface = Color(0xFFFFFFFF)
val LightLabContainer = Color(0xFFE8EEF6)
val LightLabContainerHigh = Color(0xFFDCE5F0)
val LightLabOutline = Color(0xFFB8C7DA)

val LightMintPrimary = Color(0xFF007A4E)
val LightMintContainer = Color(0xFFC2F5DC)
val LightMintOnContainer = Color(0xFF002113)

val LightAmberSecondary = Color(0xFF9E5A00)
val LightAmberContainer = Color(0xFFFFE0B5)
val LightAmberOnContainer = Color(0xFF331B00)

val LightCyanTertiary = Color(0xFF006783)
val LightCyanContainer = Color(0xFFBEE9FF)
val LightCyanOnContainer = Color(0xFF001F2A)

val LightTextPrimary = Color(0xFF0F1722)
val LightTextSecondary = Color(0xFF4A5B73)

@Immutable
data class SyntaxPalette(
  val codeBackground: Color,
  val lineNumber: Color,
  val defaultText: Color,
  val keyword: Color,
  val typeName: Color,
  val stringLiteral: Color,
  val comment: Color,
  val numberLiteral: Color,
  val opcode: Color,
  val register: Color,
  val label: Color,
)

val DarkSyntaxPalette =
  SyntaxPalette(
    codeBackground = Color(0xFF080E18),
    lineNumber = Color(0xFF546883),
    defaultText = Color(0xFFDCE6F4),
    keyword = Color(0xFFFF7B93),
    typeName = Color(0xFF4FD1FF),
    stringLiteral = Color(0xFF2EE59D),
    comment = Color(0xFF6A809E),
    numberLiteral = Color(0xFFFFB454),
    opcode = Color(0xFF7BE8BC),
    register = Color(0xFFFFC978),
    label = Color(0xFFD1A4FF),
  )

val LightSyntaxPalette =
  SyntaxPalette(
    codeBackground = Color(0xFFF6F8FC),
    lineNumber = Color(0xFF7F8FA6),
    defaultText = Color(0xFF1A2433),
    keyword = Color(0xFFB31D46),
    typeName = Color(0xFF006783),
    stringLiteral = Color(0xFF007A4E),
    comment = Color(0xFF64748B),
    numberLiteral = Color(0xFFA85600),
    opcode = Color(0xFF02724A),
    register = Color(0xFF9E5000),
    label = Color(0xFF6B35B5),
  )
