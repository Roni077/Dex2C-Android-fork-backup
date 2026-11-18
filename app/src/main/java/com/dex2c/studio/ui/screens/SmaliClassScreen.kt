package com.dex2c.studio.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dex2c.studio.R
import com.dex2c.studio.data.SmaliSyntaxTheme
import com.dex2c.studio.dex.formatAccessFlags
import com.dex2c.studio.ui.SmaliClassUiState
import com.dex2c.studio.ui.components.SmaliSoraEditor
import com.dex2c.studio.ui.components.TagPill
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily
import com.dex2c.studio.ui.theme.LocalEditorSettings

/**
 * Clean, minimal Smali Class Code Viewer.
 * Keeps only essential class viewing functionality with zero clutter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmaliClassScreen(
  state: SmaliClassUiState,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  smaliSyntaxTheme: SmaliSyntaxTheme? = null,
  onCopySmali: ((String) -> Unit)? = null,
  onExportSmali: ((fileName: String, smaliText: String) -> Unit)? = null,
  dexEditingEnabled: Boolean = false,
) {
  BackHandler(onBack = onBack)

  val editorSettings = LocalEditorSettings.current
  val activeSyntaxTheme = smaliSyntaxTheme ?: editorSettings.smaliSyntaxTheme
  val readyState = state as? SmaliClassUiState.Ready

  Column(modifier = modifier.fillMaxSize()) {
    TopAppBar(
      windowInsets = WindowInsets(0, 0, 0, 0),
      title = {
        Column {
          Text(
            text = "${state.dexClass.simpleName}.smali",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("smali_class_title"),
          )
          Text(
            text = state.dexClass.descriptor,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = JetBrainsMonoFontFamily,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      },
      navigationIcon = {
        IconButton(
          onClick = onBack,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("smali_back_btn"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
          )
        }
      },
      actions = {},
    )

    if (readyState != null) {
      val accessLabel = formatAccessFlags(state.dexClass.accessFlags).ifBlank { "public" }

      Row(
        modifier =
          Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TagPill(text = accessLabel)
        TagPill(text = state.dexClass.sourceEntryName)
        TagPill(
          text = "${readyState.methodCount} methods",
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        TagPill(text = "${readyState.fieldCount} fields")
        TagPill(text = "${readyState.lines.size} lines")
      }
    }

    Spacer(Modifier.height(4.dp))

    Box(
      modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
      contentAlignment = Alignment.Center,
    ) {
      when (state) {
        is SmaliClassUiState.Loading -> {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            CircularProgressIndicator()
            Text(
              text = stringResource(R.string.smali_disassembling),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        is SmaliClassUiState.Error -> {
          Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Code,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp),
              )
              Text(
                text = stringResource(R.string.smali_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text = state.message.ifBlank { stringResource(R.string.smali_empty_body) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        is SmaliClassUiState.Ready -> {
          Box(modifier = Modifier.fillMaxSize()) {
            SmaliSoraEditor(
              text = state.smaliText,
              isReadOnly = true,
              activeLineIndex = null,
              onLineClick = {},
              onLabelJump = {},
              syntaxTheme = activeSyntaxTheme,
              fontSizeSp = editorSettings.fontSizeSp,
              showLineNumbers = editorSettings.showLineNumbers,
              modifier = Modifier.fillMaxSize().testTag("smali_syntax_code_viewer"),
            )
          }
        }
      }
    }
  }
}
