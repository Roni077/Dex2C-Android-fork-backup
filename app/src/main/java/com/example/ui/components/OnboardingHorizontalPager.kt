package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.example.R
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun OnboardingHorizontalPager(
  pagerState: PagerState,
  pageCount: Int,
  isReplayMode: Boolean,
  onDismissReplay: () -> Unit,
  onFinish: () -> Unit,
  modifier: Modifier = Modifier,
  pageContent: @Composable (page: Int) -> Unit,
) {
  val scope = rememberCoroutineScope()

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if (isReplayMode) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .widthIn(max = 680.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onDismissReplay,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("onboarding_close_btn"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
          )
        }
      }
    } else {
      Spacer(Modifier.height(8.dp))
    }

    HorizontalPager(
      state = pagerState,
      modifier = Modifier.weight(1f).fillMaxWidth().testTag("onboarding_pager"),
    ) { page ->
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
      ) {
        Box(
          modifier =
            Modifier
              .fillMaxSize()
              .widthIn(max = 680.dp)
              .padding(horizontal = 20.dp),
        ) {
          pageContent(page)
        }
      }
    }

    // Balanced footer with Skip on left, swipe-interpolated aligned dots in center, and CTA on right
    Surface(
      modifier = Modifier.fillMaxWidth(),
      color = MaterialTheme.colorScheme.background,
      tonalElevation = 0.dp,
    ) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
      ) {
        val isLast = pagerState.currentPage == pageCount - 1
        val nextInteraction = remember { MutableInteractionSource() }

        OnboardingBalancedFooterLayout(
          modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp),
          leftSlot = {
            if (!isLast) {
              TextButton(
                onClick = { if (isReplayMode) onDismissReplay() else onFinish() },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("onboarding_skip_btn"),
              ) {
                Text(
                  text =
                    if (isReplayMode) stringResource(R.string.onboarding_done)
                    else stringResource(R.string.onboarding_skip),
                  maxLines = 1,
                )
              }
            } else {
              Spacer(Modifier.size(width = 1.dp, height = 48.dp))
            }
          },
          centerSlot = {
            OnboardingPagerDots(
              pagerState = pagerState,
              pageCount = pageCount,
              onSelectPage = { index ->
                scope.launch { pagerState.animateScrollToPage(index) }
              },
            )
          },
          rightSlot = {
            Button(
              onClick = {
                if (!isLast) {
                  scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                } else {
                  if (isReplayMode) onDismissReplay() else onFinish()
                }
              },
              interactionSource = nextInteraction,
              shape = RoundedCornerShape(20.dp),
              contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
              modifier =
                Modifier
                  .expressivePressScale(nextInteraction, 0.96f)
                  .minimumInteractiveComponentSize()
                  .testTag(if (isLast) "onboarding_finish_btn" else "onboarding_next_btn"),
            ) {
              Text(
                text =
                  if (!isLast) stringResource(R.string.onboarding_next)
                  else if (isReplayMode) stringResource(R.string.onboarding_done)
                  else stringResource(R.string.onboarding_get_started),
                maxLines = 1,
              )
              if (!isLast) {
                Spacer(Modifier.width(6.dp))
                Icon(
                  imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              }
            }
          },
        )
      }
    }
  }
}

@Composable
fun OnboardingPagerDots(
  pagerState: PagerState,
  pageCount: Int,
  onSelectPage: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val currentPosition =
    (pagerState.currentPage + pagerState.currentPageOffsetFraction)
      .coerceIn(0f, (pageCount - 1).coerceAtLeast(0).toFloat())
  val inactiveColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
  val activeColor = MaterialTheme.colorScheme.primary

  Row(
    modifier = modifier.testTag("onboarding_page_dots"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
  ) {
    repeat(pageCount) { index ->
      val distance = abs(currentPosition - index).coerceIn(0f, 1f)
      val progress = 1f - distance
      val dotWidth = lerp(7.dp, 22.dp, progress)
      val dotColor = lerp(inactiveColor, activeColor, progress)
      val isCurrent = pagerState.currentPage == index

      Box(
        modifier =
          Modifier
            .height(48.dp)
            .clip(CircleShape)
            .clickable(
              role = Role.Tab,
              onClick = { onSelectPage(index) },
            )
            .semantics {
              contentDescription = "Screen ${index + 1} of $pageCount"
              selected = isCurrent
            }
            .padding(horizontal = 4.dp)
            .testTag("onboarding_page_dot_$index"),
        contentAlignment = Alignment.Center,
      ) {
        Box(
          modifier =
            Modifier
              .height(7.dp)
              .width(dotWidth)
              .clip(CircleShape)
              .background(dotColor),
        )
      }
    }
  }
}

@Composable
private fun OnboardingBalancedFooterLayout(
  leftSlot: @Composable () -> Unit,
  centerSlot: @Composable () -> Unit,
  rightSlot: @Composable () -> Unit,
  modifier: Modifier = Modifier,
) {
  Layout(
    modifier = modifier,
    contents = listOf(leftSlot, centerSlot, rightSlot),
  ) { (leftMeasurables, centerMeasurables, rightMeasurables), constraints ->
    val loose = constraints.copy(minWidth = 0, minHeight = 0)
    val left = leftMeasurables.first().measure(loose)
    val right = rightMeasurables.first().measure(loose)
    val center = centerMeasurables.first().measure(loose)

    val width = constraints.maxWidth
    val height = maxOf(left.height, center.height, right.height)
    val gapPx = 6.dp.roundToPx()

    val rightX = (width - right.width).coerceAtLeast(0)
    val idealCenterX = (width - center.width) / 2
    val minCenterX = left.width + gapPx
    val maxCenterX = (rightX - center.width - gapPx).coerceAtLeast(minCenterX)
    val centerX = idealCenterX.coerceIn(minCenterX, maxCenterX)

    layout(width, height) {
      left.placeRelative(0, (height - left.height) / 2)
      center.placeRelative(centerX, (height - center.height) / 2)
      right.placeRelative(rightX, (height - right.height) / 2)
    }
  }
}
