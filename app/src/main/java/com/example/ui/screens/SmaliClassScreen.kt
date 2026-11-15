package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.SmaliSyntaxTheme
import com.example.dex.DexEncodedMethod
import com.example.dex.SmaliMethodSpan
import com.example.dex.formatAccessFlags
import com.example.ui.SmaliClassUiState
import com.example.ui.components.SmaliInteractiveToken
import com.example.ui.components.SmaliSearchCategory
import com.example.ui.components.SmaliSearchMatch
import com.example.ui.components.SmaliSearchOptions
import com.example.ui.components.SmaliSyntaxCodeViewer
import com.example.ui.components.SmaliTokenKind
import com.example.ui.components.TagPill
import com.example.ui.components.extractLineInteractiveTokens
import com.example.ui.components.extractQuickMethodTokens
import com.example.ui.components.findSmaliSearchMatches
import com.example.ui.components.highlightSmaliLine
import com.example.ui.theme.BytecodeHighContrastSyntaxPalette
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalEditorSettings
import com.example.ui.theme.LocalSyntaxPalette
import com.example.ui.theme.SyntaxPalette
import com.example.ui.theme.resolveSmaliSyntaxPalette
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmaliClassScreen(
  state: SmaliClassUiState,
  onCopySmali: (String) -> Unit,
  onExportSmali: (fileName: String, smaliText: String) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  selectedMethodIds: Set<String> = emptySet(),
  onToggleMethodSelection: (String) -> Unit = {},
  onInspectMethodC: (DexEncodedMethod) -> Unit = {},
  smaliSyntaxTheme: SmaliSyntaxTheme? = null,
  onSelectSmaliSyntaxTheme: ((SmaliSyntaxTheme) -> Unit)? = null,
) {
  BackHandler(onBack = onBack)

  val editorSettings = LocalEditorSettings.current
  val defaultSyntaxPalette = LocalSyntaxPalette.current
  var showThemeBar by rememberSaveable { mutableStateOf(false) }
  var localSyntaxThemeName by rememberSaveable {
    mutableStateOf((smaliSyntaxTheme ?: editorSettings.smaliSyntaxTheme).name)
  }
  val activeSyntaxTheme =
    smaliSyntaxTheme
      ?: SmaliSyntaxTheme.entries.firstOrNull { it.name == localSyntaxThemeName }
      ?: editorSettings.smaliSyntaxTheme
  val activeSyntaxPalette =
    remember(activeSyntaxTheme, defaultSyntaxPalette) {
      resolveSmaliSyntaxPalette(activeSyntaxTheme, defaultSyntaxPalette)
    }

  fun selectSyntaxTheme(theme: SmaliSyntaxTheme) {
    localSyntaxThemeName = theme.name
    onSelectSmaliSyntaxTheme?.invoke(theme)
  }

  var showSearchBar by rememberSaveable { mutableStateOf(false) }
  var showMethodsSheet by rememberSaveable { mutableStateOf(false) }
  var showMatchesDrawer by rememberSaveable { mutableStateOf(false) }
  var searchQuery by rememberSaveable(state.dexClass.uniqueKey) { mutableStateOf("") }
  var selectedCategoryName by rememberSaveable(state.dexClass.uniqueKey) {
    mutableStateOf(SmaliSearchCategory.ALL.name)
  }
  var caseSensitive by rememberSaveable(state.dexClass.uniqueKey) { mutableStateOf(false) }
  var wholeWord by rememberSaveable(state.dexClass.uniqueKey) { mutableStateOf(false) }
  var useRegex by rememberSaveable(state.dexClass.uniqueKey) { mutableStateOf(false) }
  var activeMatchPos by rememberSaveable(state.dexClass.uniqueKey) { mutableIntStateOf(0) }
  var pinnedMethodStartLine by rememberSaveable(state.dexClass.uniqueKey) { mutableIntStateOf(-1) }
  var inspectedLineIndex by rememberSaveable(state.dexClass.uniqueKey) { mutableIntStateOf(-1) }

  val selectedCategory =
    remember(selectedCategoryName) {
      SmaliSearchCategory.entries.firstOrNull { it.name == selectedCategoryName }
        ?: SmaliSearchCategory.ALL
    }

  val readyState = state as? SmaliClassUiState.Ready
  val lines = readyState?.lines.orEmpty()
  val methodSpans = readyState?.methodSpans.orEmpty()

  val searchOptions =
    remember(searchQuery, selectedCategory, caseSensitive, wholeWord, useRegex) {
      SmaliSearchOptions(
        query = searchQuery,
        category = selectedCategory,
        caseSensitive = caseSensitive,
        wholeWord = wholeWord,
        useRegex = useRegex,
      )
    }

  val searchResult =
    remember(lines, methodSpans, searchOptions) {
      findSmaliSearchMatches(
        lines = lines,
        methodSpans = methodSpans,
        options = searchOptions,
      )
    }

  val matchingLines = remember(searchResult) { searchResult.matches.map { it.lineIndex } }

  LaunchedEffect(matchingLines) {
    activeMatchPos = 0
  }

  val activeMatchLineIndex =
    if (matchingLines.isNotEmpty()) matchingLines[activeMatchPos.coerceIn(matchingLines.indices)]
    else null

  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()

  LaunchedEffect(readyState?.dexClass?.uniqueKey, readyState?.initialFocusMethodName) {
    val ready = readyState ?: return@LaunchedEffect
    val methodName = ready.initialFocusMethodName?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
    val span =
      ready.methodSpans.firstOrNull { it.name == methodName }
    val targetLine =
      span?.startLineIndex
        ?: ready.lines.indexOfFirst { line ->
          line.trimStart().startsWith(".method ") && line.contains(" $methodName(")
        }
    if (targetLine >= 0) {
      pinnedMethodStartLine = targetLine
      listState.scrollToItem(targetLine)
    }
  }

  LaunchedEffect(activeMatchLineIndex) {
    if (activeMatchLineIndex != null) {
      listState.animateScrollToItem(activeMatchLineIndex)
    }
  }

  val currentTopLine by remember {
    derivedStateOf { listState.firstVisibleItemIndex }
  }

  val activeMethodSpan: SmaliMethodSpan? =
    remember(methodSpans, pinnedMethodStartLine, currentTopLine) {
      val pinned =
        if (pinnedMethodStartLine >= 0) {
          methodSpans.firstOrNull { it.startLineIndex == pinnedMethodStartLine }
        } else null
      pinned
        ?: methodSpans.firstOrNull { currentTopLine in it.startLineIndex..it.endLineIndex }
        ?: methodSpans.firstOrNull { it.startLineIndex >= currentTopLine }
        ?: methodSpans.firstOrNull()
    }

  val activeMethodRange: IntRange? =
    remember(activeMethodSpan) {
      activeMethodSpan?.let { it.startLineIndex..it.endLineIndex }
    }

  val inspectedLineTokens: List<SmaliInteractiveToken> =
    remember(lines, inspectedLineIndex) {
      if (inspectedLineIndex in lines.indices) {
        extractLineInteractiveTokens(lines[inspectedLineIndex])
      } else {
        emptyList()
      }
    }

  val quickMethodTokens: List<SmaliInteractiveToken> =
    remember(lines, activeMethodRange, inspectedLineTokens) {
      if (inspectedLineTokens.isNotEmpty()) {
        inspectedLineTokens
      } else {
        extractQuickMethodTokens(lines, activeMethodRange)
      }
    }

  fun onSelectInteractiveToken(token: SmaliInteractiveToken) {
    showSearchBar = true
    searchQuery = token.text
    useRegex = false
    when (token.kind) {
      SmaliTokenKind.REGISTER -> {
        selectedCategoryName = SmaliSearchCategory.REGISTERS.name
        wholeWord = true
      }
      SmaliTokenKind.OPCODE -> {
        selectedCategoryName = SmaliSearchCategory.OPCODES.name
        wholeWord = true
      }
      SmaliTokenKind.METHOD -> {
        selectedCategoryName = SmaliSearchCategory.ALL.name
        wholeWord = false
      }
      SmaliTokenKind.FIELD -> {
        selectedCategoryName = SmaliSearchCategory.ALL.name
        wholeWord = false
      }
      SmaliTokenKind.STRING -> {
        selectedCategoryName = SmaliSearchCategory.ALL.name
        wholeWord = false
      }
    }
  }

  Column(modifier = modifier.fillMaxSize()) {
    TopAppBar(
      windowInsets = WindowInsets(0, 0, 0, 0),
      title = {
        Column {
          Text(
            text = "${state.dexClass.simpleName}.smali",
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
      actions = {
        if (readyState != null && (methodSpans.isNotEmpty() || state.dexClass.allMethods.isNotEmpty())) {
          IconButton(
            onClick = { showMethodsSheet = true },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("smali_methods_btn"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.List,
              contentDescription = "Methods in ${state.dexClass.simpleName}",
            )
          }
        }
        IconButton(
          onClick = { showThemeBar = !showThemeBar },
          enabled = readyState != null,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("smali_theme_switcher_btn"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Palette,
            contentDescription = "Switch Smali syntax theme",
            tint =
              if (showThemeBar || activeSyntaxTheme != SmaliSyntaxTheme.APP_DEFAULT) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.onSurface
              },
          )
        }
        IconButton(
          onClick = { showSearchBar = !showSearchBar },
          enabled = readyState != null,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("smali_search_toggle_btn"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = stringResource(R.string.action_search_smali),
          )
        }
        IconButton(
          onClick = { readyState?.let { onCopySmali(it.smaliText) } },
          enabled = readyState != null,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("smali_copy_btn"),
        ) {
          Icon(
            imageVector = Icons.Rounded.ContentCopy,
            contentDescription = stringResource(R.string.action_copy_code),
          )
        }
        IconButton(
          onClick = {
            readyState?.let {
              val baseName = state.dexClass.simpleName.ifBlank { "Class" }.replace('$', '_')
              onExportSmali("$baseName.smali", it.smaliText)
            }
          },
          enabled = readyState != null,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("smali_export_btn"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Download,
            contentDescription = stringResource(R.string.action_export_smali),
          )
        }
      },
    )

    // Dex Editor Plus style Sub-Header Bar: *ClassName on left, line & [start - end] : methodName() on right
    if (readyState != null) {
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier =
          Modifier
            .fillMaxWidth()
            .clickable(enabled = methodSpans.isNotEmpty() || state.dexClass.allMethods.isNotEmpty()) {
              showMethodsSheet = true
            }
            .testTag("smali_method_range_bar"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "*${state.dexClass.simpleName}",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          Spacer(Modifier.width(12.dp))
          val cursorLine =
            (activeMatchLineIndex ?: activeMethodSpan?.startLineIndex ?: currentTopLine) + 1
          val rangeLabel =
            if (activeMethodSpan != null) {
              val start1 = activeMethodSpan.startLineIndex + 1
              val end1 = activeMethodSpan.endLineIndex + 1
              "$cursorLine:${readyState.lineCount} | [$start1 - $end1] : ${activeMethodSpan.name}()"
            } else {
              "$cursorLine:${readyState.lineCount} lines"
            }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = rangeLabel,
              style = MaterialTheme.typography.labelSmall,
              fontFamily = JetBrainsMonoFontFamily,
              color = MaterialTheme.colorScheme.primary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.testTag("smali_active_method_range_text"),
            )
            if (methodSpans.isNotEmpty()) {
              Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Jump to method",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
              )
            }
          }
        }
      }
    }

    // Metadata Pills
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val accessLabel = formatAccessFlags(state.dexClass.accessFlags).ifBlank { "package-private" }
      TagPill(text = accessLabel)
      TagPill(text = state.dexClass.sourceEntryName)
      if (readyState != null) {
        TagPill(
          text = "${readyState.methodCount} methods",
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        TagPill(text = "${readyState.fieldCount} fields")
        TagPill(text = "${readyState.lineCount} lines")
        TagPill(
          text = "dexlib2 • ${readyState.durationMs} ms",
          containerColor = MaterialTheme.colorScheme.secondaryContainer,
          contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        TagPill(
          text = activeSyntaxTheme.shortLabel,
          containerColor =
            if (activeSyntaxTheme == SmaliSyntaxTheme.BYTECODE_HIGH_CONTRAST) {
              MaterialTheme.colorScheme.tertiaryContainer
            } else {
              MaterialTheme.colorScheme.surfaceContainerHigh
            },
          contentColor =
            if (activeSyntaxTheme == SmaliSyntaxTheme.BYTECODE_HIGH_CONTRAST) {
              MaterialTheme.colorScheme.onTertiaryContainer
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
      }
    }

    // Collapsible Smali Syntax Theme Switcher Bar
    AnimatedVisibility(visible = showThemeBar && readyState != null) {
      SmaliThemeSelectorBar(
        activeTheme = activeSyntaxTheme,
        onSelectTheme = { selectSyntaxTheme(it) },
      )
    }

    // Interactive Line Symbol Inspector Bar when a line is tapped outside or inside search mode
    AnimatedVisibility(visible = inspectedLineIndex in lines.indices && inspectedLineTokens.isNotEmpty()) {
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier =
          Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .testTag("smali_inspected_line_bar"),
      ) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .padding(horizontal = 10.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "L${inspectedLineIndex + 1} Symbols:",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
          )
          inspectedLineTokens.forEach { token ->
            SmaliTokenQuickChip(
              token = token,
              isSelected =
                searchOptions.trimmedQuery.equals(token.text, ignoreCase = !caseSensitive) &&
                  showSearchBar,
              onClick = { onSelectInteractiveToken(token) },
              testTag = "smali_line_token_${token.text}",
            )
          }
          TextButton(
            onClick = { inspectedLineIndex = -1 },
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            modifier = Modifier.height(26.dp).testTag("smali_inspected_line_clear_btn"),
          ) {
            Text("Dismiss", style = MaterialTheme.typography.labelSmall)
          }
        }
      }
    }

    AnimatedVisibility(visible = showSearchBar && readyState != null) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        // Row 1: Search Input + Match Counter + Prev / Next + Drawer Toggle
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            singleLine = true,
            placeholder = {
              Text(
                text = stringResource(R.string.smali_search_hint),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
            trailingIcon = {
              if (searchQuery.isNotEmpty() || selectedCategory != SmaliSearchCategory.ALL) {
                IconButton(
                  onClick = {
                    searchQuery = ""
                    selectedCategoryName = SmaliSearchCategory.ALL.name
                  },
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("smali_search_clear_btn"),
                ) {
                  Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                }
              }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f).testTag("smali_search_input"),
          )
          val countText =
            if (!searchOptions.isActive || matchingLines.isEmpty()) "0/0"
            else "${activeMatchPos.coerceIn(matchingLines.indices) + 1}/${matchingLines.size}"
          Text(
            text = countText,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = JetBrainsMonoFontFamily,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp).testTag("smali_search_count"),
          )
          IconButton(
            onClick = {
              if (matchingLines.isNotEmpty()) {
                val next = if (activeMatchPos <= 0) matchingLines.lastIndex else activeMatchPos - 1
                activeMatchPos = next
                scope.launch { listState.animateScrollToItem(matchingLines[next]) }
              }
            },
            enabled = matchingLines.isNotEmpty(),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("smali_search_prev_btn"),
          ) {
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Previous match")
          }
          IconButton(
            onClick = {
              if (matchingLines.isNotEmpty()) {
                val next = (activeMatchPos + 1) % matchingLines.size
                activeMatchPos = next
                scope.launch { listState.animateScrollToItem(matchingLines[next]) }
              }
            },
            enabled = matchingLines.isNotEmpty(),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("smali_search_next_btn"),
          ) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Next match")
          }
          IconButton(
            onClick = { showMatchesDrawer = !showMatchesDrawer },
            enabled = matchingLines.isNotEmpty(),
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .testTag("smali_search_matches_drawer_toggle"),
          ) {
            Icon(
              imageVector = Icons.Rounded.FilterList,
              contentDescription = "Toggle matching lines drawer",
              tint =
                if (showMatchesDrawer && matchingLines.isNotEmpty()) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
          }
        }

        // Row 2: Match Mode Toggles (Whole-word, Regex, Case) + Category Filter Chips
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          FilterChip(
            selected = wholeWord,
            onClick = { wholeWord = !wholeWord },
            label = {
              Text(
                text = "\\b Word",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
              )
            },
            modifier = Modifier.height(30.dp).testTag("smali_search_toggle_word"),
          )
          FilterChip(
            selected = useRegex,
            onClick = { useRegex = !useRegex },
            label = {
              Text(
                text = ".* Regex",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
              )
            },
            modifier = Modifier.height(30.dp).testTag("smali_search_toggle_regex"),
          )
          FilterChip(
            selected = caseSensitive,
            onClick = { caseSensitive = !caseSensitive },
            label = {
              Text(
                text = "Aa Case",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
              )
            },
            modifier = Modifier.height(30.dp).testTag("smali_search_toggle_case"),
          )

          SmaliSearchCategory.entries.forEach { category ->
            FilterChip(
              selected = selectedCategory == category,
              onClick = {
                selectedCategoryName =
                  if (selectedCategory == category && category != SmaliSearchCategory.ALL) {
                    SmaliSearchCategory.ALL.name
                  } else {
                    category.name
                  }
              },
              label = {
                Text(
                  text = category.label,
                  style = MaterialTheme.typography.labelSmall,
                )
              },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                  selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
              modifier =
                Modifier.height(30.dp)
                  .testTag("smali_search_category_${category.tagSuffix}"),
            )
          }
        }

        // Regex Error Banner if invalid regex
        if (searchResult.regexError != null) {
          Text(
            text = "Regex error: ${searchResult.regexError}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            fontFamily = JetBrainsMonoFontFamily,
            modifier = Modifier.testTag("smali_search_regex_error"),
          )
        }

        // Row 3: Quick Register & Opcode Chips from active method or tapped line
        if (quickMethodTokens.isNotEmpty()) {
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("smali_quick_tokens_row"),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = if (inspectedLineTokens.isNotEmpty()) "Line tokens:" else "Quick highlight:",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            quickMethodTokens.forEach { token ->
              SmaliTokenQuickChip(
                token = token,
                isSelected =
                  searchOptions.trimmedQuery.equals(token.text, ignoreCase = !caseSensitive),
                onClick = { onSelectInteractiveToken(token) },
                testTag = "smali_quick_token_${token.text}",
              )
            }
          }
        }

        // Collapsible Matching Lines Drawer
        AnimatedVisibility(visible = showMatchesDrawer && searchResult.matches.isNotEmpty()) {
          SmaliMatchingLinesDrawer(
            matches = searchResult.matches,
            lines = lines,
            activeMatchPos = activeMatchPos,
            palette = activeSyntaxPalette,
            onSelectMatch = { indexInMatches, match ->
              activeMatchPos = indexInMatches
              scope.launch { listState.animateScrollToItem(match.lineIndex) }
            },
            onClose = { showMatchesDrawer = false },
          )
        }
      }
    }

    Spacer(Modifier.height(4.dp))

    Box(
      modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
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
          SmaliSyntaxCodeViewer(
            lines = state.lines,
            searchQuery = searchOptions.trimmedQuery,
            activeMatchLineIndex = activeMatchLineIndex,
            activeMethodRange = activeMethodRange,
            listState = listState,
            searchOptions = searchOptions,
            lineMatchRanges = searchResult.matchesByLine,
            inspectedLineIndex = inspectedLineIndex.takeIf { it >= 0 },
            onLineClick = { clickedIdx ->
              inspectedLineIndex = if (inspectedLineIndex == clickedIdx) -1 else clickedIdx
            },
            syntaxThemeOverride = activeSyntaxTheme,
            modifier = Modifier.fillMaxSize(),
          )
        }
      }
    }
  }

  if (showMethodsSheet && readyState != null) {
    SmaliMethodsNavigatorSheet(
      dexClass = state.dexClass,
      methodSpans = methodSpans,
      selectedMethodIds = selectedMethodIds,
      onJumpToSpan = { span ->
        pinnedMethodStartLine = span.startLineIndex
        showMethodsSheet = false
        scope.launch { listState.animateScrollToItem(span.startLineIndex) }
      },
      onToggleMethodSelection = onToggleMethodSelection,
      onInspectMethodC = { method ->
        showMethodsSheet = false
        onInspectMethodC(method)
      },
      onDismiss = { showMethodsSheet = false },
    )
  }
}

@Composable
private fun SmaliTokenQuickChip(
  token: SmaliInteractiveToken,
  isSelected: Boolean,
  onClick: () -> Unit,
  testTag: String,
) {
  val containerColor =
    if (isSelected) {
      MaterialTheme.colorScheme.primaryContainer
    } else {
      MaterialTheme.colorScheme.surfaceContainerHighest
    }
  val contentColor =
    if (isSelected) {
      MaterialTheme.colorScheme.onPrimaryContainer
    } else {
      MaterialTheme.colorScheme.onSurface
    }

  Surface(
    color = containerColor,
    contentColor = contentColor,
    shape = RoundedCornerShape(8.dp),
    border =
      BorderStroke(
        width = if (isSelected) 1.dp else 0.5.dp,
        color =
          if (isSelected) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
      ),
    modifier =
      Modifier.clip(RoundedCornerShape(8.dp))
        .clickable(onClick = onClick)
        .testTag(testTag),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = token.kind.badgeLabel,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 9.sp,
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
      )
      Text(
        text = token.text,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = JetBrainsMonoFontFamily,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun SmaliThemeSelectorBar(
  activeTheme: SmaliSyntaxTheme,
  onSelectTheme: (SmaliSyntaxTheme) -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    shape = RoundedCornerShape(14.dp),
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp)
        .testTag("smali_theme_selector_bar"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Smali Syntax Theme",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = activeTheme.summary,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        SmaliSyntaxTheme.entries.forEach { theme ->
          FilterChip(
            selected = activeTheme == theme,
            onClick = { onSelectTheme(theme) },
            label = {
              Text(
                text = theme.label,
                style = MaterialTheme.typography.labelSmall,
              )
            },
            modifier =
              Modifier.height(32.dp)
                .testTag("smali_theme_chip_${theme.tagSuffix}"),
          )
        }
      }
      if (activeTheme == SmaliSyntaxTheme.BYTECODE_HIGH_CONTRAST) {
        val hc = BytecodeHighContrastSyntaxPalette
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .testTag("smali_high_contrast_legend"),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          HighContrastLegendDot("Branch/Jump", hc.opcodeBranch)
          HighContrastLegendDot("Invoke", hc.opcodeInvoke)
          HighContrastLegendDot("Field/Op", hc.opcodeAccess)
          HighContrastLegendDot("Move/Const", hc.opcode)
          HighContrastLegendDot("v* Local", hc.register)
          HighContrastLegendDot("p* Param", hc.parameterRegister)
          HighContrastLegendDot("->Target", hc.memberTarget)
        }
      }
    }
  }
}

@Composable
private fun HighContrastLegendDot(label: String, color: Color) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Box(
      modifier =
        Modifier.size(8.dp)
          .clip(CircleShape)
          .background(color)
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      fontSize = 10.sp,
      fontFamily = JetBrainsMonoFontFamily,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun SmaliMatchingLinesDrawer(
  matches: List<SmaliSearchMatch>,
  lines: List<String>,
  activeMatchPos: Int,
  palette: SyntaxPalette = LocalSyntaxPalette.current,
  onSelectMatch: (indexInMatches: Int, match: SmaliSearchMatch) -> Unit,
  onClose: () -> Unit,
) {
  val matchBg = MaterialTheme.colorScheme.primaryContainer
  val matchFg = MaterialTheme.colorScheme.onPrimaryContainer

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    modifier = Modifier.fillMaxWidth().testTag("smali_matching_lines_drawer"),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Matching Lines (${matches.size})",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
        )
        TextButton(
          onClick = onClose,
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
          modifier = Modifier.height(26.dp),
        ) {
          Text("Hide", style = MaterialTheme.typography.labelSmall)
        }
      }
      HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
      )
      LazyColumn(
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(max = 160.dp)
            .testTag("smali_matching_lines_list"),
        contentPadding = PaddingValues(vertical = 4.dp),
      ) {
        itemsIndexed(matches) { idx, match ->
          val isSelected = idx == activeMatchPos
          val rawLine = lines.getOrElse(match.lineIndex) { match.linePreview }
          val highlightedPreview =
            remember(rawLine, match.ranges, palette, matchBg, matchFg) {
              highlightSmaliLine(
                line = rawLine.trim(),
                palette = palette,
                matchBackground = matchBg,
                matchForeground = matchFg,
              )
            }
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                  if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                  else MaterialTheme.colorScheme.surfaceContainerHigh
                )
                .clickable { onSelectMatch(idx, match) }
                .padding(horizontal = 8.dp, vertical = 5.dp)
                .testTag("smali_match_result_row_${match.lineIndex}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "L${match.lineNumber}",
              style = MaterialTheme.typography.labelSmall,
              fontFamily = JetBrainsMonoFontFamily,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
            )
            if (!match.enclosingMethodName.isNullOrBlank()) {
              Text(
                text = "${match.enclosingMethodName}()",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
                color = MaterialTheme.colorScheme.tertiary,
                maxLines = 1,
              )
            }
            Text(
              text = highlightedPreview,
              style = MaterialTheme.typography.labelSmall,
              fontFamily = JetBrainsMonoFontFamily,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmaliMethodsNavigatorSheet(
  dexClass: com.example.dex.DexClassInfo,
  methodSpans: List<SmaliMethodSpan>,
  selectedMethodIds: Set<String>,
  onJumpToSpan: (SmaliMethodSpan) -> Unit,
  onToggleMethodSelection: (String) -> Unit,
  onInspectMethodC: (DexEncodedMethod) -> Unit,
  onDismiss: () -> Unit,
) {
  val allMethods = dexClass.allMethods
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = "${dexClass.simpleName} Methods (${methodSpans.size.coerceAtLeast(allMethods.size)})",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = "Tap a method to jump to its .method block in Smali, queue it for C compilation, or inspect its C/CFG.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
      LazyColumn(
        modifier = Modifier.fillMaxWidth().height(360.dp).testTag("smali_methods_sheet_list"),
        contentPadding = PaddingValues(vertical = 4.dp),
      ) {
        if (methodSpans.isNotEmpty()) {
          itemsIndexed(methodSpans) { idx, span ->
            val matchedMethod =
              allMethods.getOrNull(idx)?.takeIf { it.methodRef.name == span.name }
                ?: allMethods.firstOrNull { "${it.methodRef.name}${it.methodRef.jniSignature()}" == span.signature }
                ?: allMethods.firstOrNull { it.methodRef.name == span.name }
            Row(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clickable { onJumpToSpan(span) }
                  .padding(vertical = 6.dp, horizontal = 4.dp)
                  .testTag("smali_method_jump_item_$idx"),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              if (matchedMethod != null) {
                Checkbox(
                  checked = matchedMethod.id in selectedMethodIds,
                  onCheckedChange = { onToggleMethodSelection(matchedMethod.id) },
                  modifier = Modifier.minimumInteractiveComponentSize(),
                )
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = span.signature,
                  style = MaterialTheme.typography.labelMedium,
                  fontFamily = JetBrainsMonoFontFamily,
                  fontWeight = FontWeight.SemiBold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "[${span.startLineIndex + 1} - ${span.endLineIndex + 1}] : ${span.name}()",
                  style = MaterialTheme.typography.labelSmall,
                  fontSize = 11.sp,
                  fontFamily = JetBrainsMonoFontFamily,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
              if (matchedMethod != null) {
                TextButton(
                  onClick = { onInspectMethodC(matchedMethod) },
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                  modifier = Modifier.testTag("smali_method_inspect_c_btn_$idx"),
                ) {
                  Text("C / CFG", style = MaterialTheme.typography.labelSmall)
                  Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                  )
                }
              }
            }
            if (idx < methodSpans.lastIndex) {
              HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
              )
            }
          }
        } else {
          itemsIndexed(allMethods) { idx, method ->
            Row(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clickable { onInspectMethodC(method) }
                  .padding(vertical = 6.dp, horizontal = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Checkbox(
                checked = method.id in selectedMethodIds,
                onCheckedChange = { onToggleMethodSelection(method.id) },
                modifier = Modifier.minimumInteractiveComponentSize(),
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = method.methodRef.prettySignature(),
                  style = MaterialTheme.typography.labelMedium,
                  fontFamily = JetBrainsMonoFontFamily,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "Inspect C/CFG",
                modifier = Modifier.size(16.dp),
              )
            }
          }
        }
      }
      Spacer(Modifier.height(12.dp))
    }
  }
}
