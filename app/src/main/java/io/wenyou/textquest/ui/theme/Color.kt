package io.wenyou.textquest.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Material You 动态配色关闭时的回退主题种子与基础色。
 * 主色取自中性紫（类似默认 M3 基线），打开「动态配色」后由系统壁纸取色覆盖。
 */
val BrandPrimary = Color(0xFF6750A4)
val BrandOnPrimary = Color(0xFFFFFFFF)
val BrandPrimaryContainer = Color(0xFFEADDFF)
val BrandOnPrimaryContainer = Color(0xFF21005D)

val BrandSecondary = Color(0xFF625B71)
val BrandOnSecondary = Color(0xFFFFFFFF)
val BrandSecondaryContainer = Color(0xFFE8DEF8)
val BrandOnSecondaryContainer = Color(0xFF1D192B)

val BrandTertiary = Color(0xFF7D5260)
val BrandTertiaryContainer = Color(0xFFFFD8E4)

val BrandBackgroundLight = Color(0xFFFDF8FD)
val BrandSurfaceLight = Color(0xFFFFFBFF)
val BrandBackgroundDark = Color(0xFF141218)
val BrandSurfaceDark = Color(0xFF141218)

/**
 * 头像 / 封面 / 节点标签的固定取色板（独立于主题，保证不同存档间辨识度一致）。
 * 12 色，圆角形态下做 tonal 头像与封面渐变。
 */
val AvatarPalette: List<Color> = listOf(
    Color(0xFF6750A4), // 紫
    Color(0xFF006A6A), // 青
    Color(0xFF7D5260), // 玫红
    Color(0xFF386A20), // 绿
    Color(0xFF825500), // 琥珀
    Color(0xFF0842A0), // 蓝
    Color(0xFFB3261E), // 红
    Color(0xFF006C4C), // 翠
    Color(0xFF65558F), // 蓝紫
    Color(0xFFB05712), // 橙
    Color(0xFF455A64), // 蓝灰
    Color(0xFF6A3C00)  // 棕
)

fun avatarColor(index: Int): Color =
    AvatarPalette[((index % AvatarPalette.size) + AvatarPalette.size) % AvatarPalette.size]

/** 深色背景下的角色色（提高对比度）。 */
fun avatarColorOnDark(index: Int): Color = avatarColor(index).copy(alpha = 1f)
