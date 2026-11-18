package com.dex2c.studio.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Window width size classes based on Material 3 Adaptive design specifications:
 * - COMPACT: < 600 dp (typical phones in portrait)
 * - MEDIUM: 600 dp .. 839 dp (small tablets, foldables unfolded, landscape phones)
 * - EXPANDED: >= 840 dp (large tablets, foldables unfolded, desktops, DeX)
 */
enum class WindowWidthSizeClass {
  COMPACT,
  MEDIUM,
  EXPANDED,
  ;

  companion object {
    fun fromWidth(width: Dp): WindowWidthSizeClass =
      when {
        width < 600.dp -> COMPACT
        width < 840.dp -> MEDIUM
        else -> EXPANDED
      }
  }
}

/**
 * Window height size classes based on Material 3 Adaptive design specifications:
 * - COMPACT: < 480 dp (typical phones in landscape)
 * - MEDIUM: 480 dp .. 899 dp (typical phones in portrait, tablets in landscape)
 * - EXPANDED: >= 900 dp (tablets in portrait, desktop windows)
 */
enum class WindowHeightSizeClass {
  COMPACT,
  MEDIUM,
  EXPANDED,
  ;

  companion object {
    fun fromHeight(height: Dp): WindowHeightSizeClass =
      when {
        height < 480.dp -> COMPACT
        height < 900.dp -> MEDIUM
        else -> EXPANDED
      }
  }
}

/**
 * Encapsulates responsive window metrics, orientation, and layout recommendations.
 */
@Immutable
data class WindowAdaptiveInfo(
  val widthSizeClass: WindowWidthSizeClass,
  val heightSizeClass: WindowHeightSizeClass,
  val maxWidth: Dp,
  val maxHeight: Dp,
) {
  val isLandscape: Boolean
    get() = maxWidth > maxHeight

  /**
   * Use NavigationRail instead of BottomBar when width is Medium or Expanded,
   * OR when in landscape orientation with compact vertical height (saving screen height on phones).
   */
  val useNavRail: Boolean
    get() = widthSizeClass != WindowWidthSizeClass.COMPACT || (isLandscape && heightSizeClass == WindowHeightSizeClass.COMPACT)

  /**
   * Two-pane master-detail layout recommended for Expanded widths (>= 840dp).
   */
  val isTwoPane: Boolean
    get() = widthSizeClass == WindowWidthSizeClass.EXPANDED

  /**
   * Maximum recommended readable width for single-column cards and forms.
   */
  val contentMaxWidth: Dp
    get() = when (widthSizeClass) {
      WindowWidthSizeClass.COMPACT -> Dp.Unspecified
      WindowWidthSizeClass.MEDIUM -> 760.dp
      WindowWidthSizeClass.EXPANDED -> 1200.dp
    }

  /**
   * Dynamically calculates number of grid columns based on available container width and target minimum cell width.
   */
  fun computeGridColumns(minCellWidth: Dp = 300.dp, maxColumns: Int = 4): Int {
    val cols = (maxWidth / minCellWidth).toInt().coerceAtLeast(1)
    return cols.coerceAtMost(maxColumns)
  }
}

/**
 * Resolves [WindowAdaptiveInfo] from explicit [maxWidth] and [maxHeight] (e.g. inside BoxWithConstraints).
 */
@Composable
fun rememberWindowAdaptiveInfo(maxWidth: Dp, maxHeight: Dp): WindowAdaptiveInfo {
  return remember(maxWidth, maxHeight) {
    WindowAdaptiveInfo(
      widthSizeClass = WindowWidthSizeClass.fromWidth(maxWidth),
      heightSizeClass = WindowHeightSizeClass.fromHeight(maxHeight),
      maxWidth = maxWidth,
      maxHeight = maxHeight,
    )
  }
}

/**
 * Resolves [WindowAdaptiveInfo] from current configuration.
 */
@Composable
fun rememberWindowAdaptiveInfo(): WindowAdaptiveInfo {
  val configuration = LocalConfiguration.current
  val screenWidth = configuration.screenWidthDp.dp
  val screenHeight = configuration.screenHeightDp.dp
  return rememberWindowAdaptiveInfo(maxWidth = screenWidth, maxHeight = screenHeight)
}
