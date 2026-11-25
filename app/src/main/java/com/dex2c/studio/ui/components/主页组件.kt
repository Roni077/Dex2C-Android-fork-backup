package com.dex2c.studio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily

@Composable
fun MetricTile(
  label: String,
  value: String,
  icon: ImageVector,
  accent: Color,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(if (compact) 20.dp else 24.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
  ) {
    Column(modifier = Modifier.padding(if (compact) 12.dp else 16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = accent,
          modifier = Modifier.size(if (compact) 16.dp else 18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
          text = label,
          style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
      Text(
        text = value,
        style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
        fontFamily = JetBrainsMonoFontFamily,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun TagPill(
  text: String,
  containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
  contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.semantics(mergeDescendants = true) {},
    shape = RoundedCornerShape(50),
    color = containerColor,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = contentColor,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
fun HeaderKeyValueRow(
  key: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = key,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.width(12.dp))
    Text(
      text = value,
      style = MaterialTheme.typography.labelMedium,
      fontFamily = JetBrainsMonoFontFamily,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
