package com.dex2c.studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily

/**
 * Dalvik Smali touch accessory bar providing one-tap symbol/opcode insertion
 * for fast bytecode editing.
 */
@Composable
fun SymbolQuickBar(
  onInsertSymbol: (String) -> Unit,
  modifier: Modifier = Modifier,
  symbols: List<String> = DEFAULT_SMALI_SYMBOLS,
) {
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("symbol_quick_bar"),
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
    tonalElevation = 2.dp,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 6.dp, vertical = 3.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      symbols.forEach { sym ->
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
          TextButton(
            onClick = { onInsertSymbol(sym) },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier
              .height(30.dp)
              .testTag("symbol_btn_$sym"),
          ) {
            Text(
              text = sym,
              style = MaterialTheme.typography.labelSmall,
              fontFamily = JetBrainsMonoFontFamily,
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }
    }
  }
}

private val DEFAULT_SMALI_SYMBOLS = listOf(
  "->", "{}", "()", ",", ".", ";", "\"", ":",
  "v0", "v1", "p0", "p1",
  "[", "]", "<", ">",
  "const", "return-void", "goto", "if-eqz",
  ".method", ".end method", ".line",
)
