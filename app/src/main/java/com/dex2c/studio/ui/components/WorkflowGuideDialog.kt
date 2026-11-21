package com.dex2c.studio.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily

data class WorkflowSection(
  val id: String,
  val shortLabel: String,
  val title: String,
  val userSteps: List<String>,
  val technicalNotes: List<String>,
  val diagramOrCode: String? = null,
)

enum class WorkflowViewMode {
  INTERACTIVE,
  RAW_MARKDOWN,
}

object WorkflowDocumentationRepository {

  val sections: List<WorkflowSection> =
    listOf(
      WorkflowSection(
        id = "architecture",
        shortLabel = "1. Architecture",
        title = "1. High-Level System Architecture & Data Pipeline",
        userSteps =
          listOf(
            "Dex2C Studio unifies Dalvik-to-C++17 native method transpilation (codehasan/dex2c) with Dex-Editor-Android's in-memory Smali editing, Java decompilation, and patched DEX/APK export.",
            "Every operation runs 100% in memory using streaming ZIP/DEX readers and MemoryDataStore without leaking temporary files."
          ),
        technicalNotes =
          listOf(
            "Pipeline stages: Binary Ingestion -> Multi-DEX Streaming -> Explorer & Search-Replace -> Smali Editor / Java Decompiler / C++17 Transpiler -> Local SAF Export or GitHub Actions Cloud CI."
          ),
        diagramOrCode =
          """
          [SAF / Installed APK / Self APK] ──► [ApkDexExtractor] ──► [DexParser]
                 │
                 ├──► [DEX Explorer: Classes | Methods | Fields | Strings | Rules]
                 │
                 ├──► [Smali Editor & Java Decompiler] ──► [SmaliDexCompiler (DexPool)]
                 │                                                └──► [Patched DEX / APK]
                 └──► [Dex2cTranspiler (CFG + C++17 JNI)] ───────────► [NDK ZIP / Cloud CI]
          """.trimIndent(),
      ),
      WorkflowSection(
        id = "ingestion",
        shortLabel = "2. Ingestion",
        title = "2. Phase 1: Binary Ingestion & Multi-DEX Streaming",
        userSteps =
          listOf(
            "Open the Home screen and load an APK or DEX via Device Storage (SAF), Installed Apps, Self-APK Inspection, or Recent Binary History.",
            "Switch between Overview (metrics, bytecode summary, quick filter focus) and Header & Map (raw DEX magic 035–041, SHA-1 signature, Adler32 checksum, and map_list items).",
            "For multi-DEX APKs, switch active classesN.dex entries or inspect merged multi-DEX metrics."
          ),
        technicalNotes =
          listOf(
            "ApkDexExtractor streams classes*.dex and AndroidManifest.xml directly from APK ZIP streams.",
            "DexParser decodes string_ids, type_ids, proto_ids, field_ids, method_ids, class_defs, and code_item bytecode.",
            "Unused multi-DEX pools are released automatically when switching entries to minimize RAM usage."
          ),
      ),
      WorkflowSection(
        id = "explorer",
        shortLabel = "3. Explorer",
        title = "3. Phase 2: DEX Explorer (Classes, Methods, Fields, Strings & Rules)",
        userSteps =
          listOf(
            "CLASSES Tab: Browse Package Tree or Flat List, toggle Select Mode for batch C++ selection, apply +W / !B filter rules, or delete classes from the active DEX.",
            "METHODS Tab: Search all method signatures across the active DEX, filter by public/native/static flags, and jump directly to method inspection.",
            "FIELDS Tab: Search static and instance fields by field name, declaring class, or type descriptor.",
            "STRINGS Tab: Inspect string_ids and execute Global Search & Replace across all classes in memory.",
            "RULES Tab: Build Whitelist and Blacklist regex rules visually or in raw filter.txt mode."
          ),
        technicalNotes =
          listOf(
            "All explorer search queries use debounced background coroutine workers (Dispatchers.Default) for smooth 60–120fps scrolling on large APKs."
          ),
      ),
      WorkflowSection(
        id = "smali_java",
        shortLabel = "4. Smali & Java",
        title = "4. Phase 3: Interactive Smali Editor, Java Decompiler & DEX Assembly",
        userSteps =
          listOf(
            "View Mode: Inspect syntax-highlighted Smali with collapsible method spans, register/label highlighting, and one-tap Label Jump (:cond_*, :goto_*, :try_start_*).",
            "Edit Mode: Edit Smali instructions with Auto-Complete suggestion chips (opcodes, registers, descriptors), multi-step Undo/Redo, Search & Replace, and one-tap Compile to DEX.",
            "Java Mode: Preview reconstructed Java source code for the entire class alongside Smali."
          ),
        technicalNotes =
          listOf(
            "SmaliClassDisassembler uses Google baksmali ClassDefinition to generate structured lines and method spans.",
            "SmaliDexCompiler runs smaliFlexLexer, smaliParser, and smaliTreeWalker in memory and rebuilds the DEX via DexPool and MemoryDataStore."
          ),
      ),
      WorkflowSection(
        id = "transpiler",
        shortLabel = "5. C++17 & CFG",
        title = "5. Phase 4: Method Inspection, Control Flow Graph (CFG) & C++17 Transpiler",
        userSteps =
          listOf(
            "C++17 / JNI Tab: Inspect generated JNIEXPORT functions, RegisterNatives tables, register variables, and exception checks.",
            "Java Tab: Compare reconstructed Java statements side-by-side with C++17 output.",
            "Instructions & Basic Blocks (CFG) Tab: Inspect Dalvik opcodes, basic blocks (BB_0, BB_1), branch edges, try/catch handlers, and switch payloads."
          ),
        technicalNotes =
          listOf(
            "Dex2cTranspiler supports both RegisterNatives (JNI_OnLoad) and static JNI symbols in C++17 (env->) or C99 ((*env)->) dialects.",
            "MethodCfgBuilder computes basic block boundaries from branch offsets, fallthroughs, and payload tables."
          ),
      ),
      WorkflowSection(
        id = "export_cloud",
        shortLabel = "6. Export & CI",
        title = "6. Phase 5 & 6: Filter Rules, Patched DEX/APK Export & GitHub Cloud CI",
        userSteps =
          listOf(
            "Export Patched DEX / APK: Use the Classes top bar menu to save patched_classes.dex or repackage patched_app.apk via Android SAF.",
            "Pipeline Configuration: Configure dcc.cfg passes, symbol bindings, and filter rules in the Build pipeline tab before compiling or dispatching to CI.",
            "GitHub Cloud CI: Sign in via GitHub OAuth Device Flow, sync .github/workflows/dex2c.yml, dispatch cloud NDK builds, and receive WorkManager push notifications."
          ),
        technicalNotes =
          listOf(
            "Bounded LRU caches for Smali disassembly, Java decompilation, and C++ transpilation auto-trim on navigation and OS onTrimMemory callbacks."
          ),
      ),
    )

  fun loadRawMarkdown(context: Context): String {
    return runCatching {
      context.assets.open("WORKFLOW.md").bufferedReader().use { it.readText() }
    }.getOrElse {
      buildFallbackMarkdown()
    }
  }

  fun buildFallbackMarkdown(): String =
    buildString {
      appendLine("# Dex2C Studio — End-to-End Application Workflow & Technical Architecture")
      appendLine()
      sections.forEach { sec ->
        appendLine("## ${sec.title}")
        appendLine()
        if (sec.diagramOrCode != null) {
          appendLine("```")
          appendLine(sec.diagramOrCode)
          appendLine("```")
          appendLine()
        }
        appendLine("### User Workflow")
        sec.userSteps.forEach { step -> appendLine("- $step") }
        appendLine()
        appendLine("### Technical Architecture")
        sec.technicalNotes.forEach { note -> appendLine("- $note") }
        appendLine()
      }
    }
}

@Composable
fun WorkflowGuideDialog(
  onDismiss: () -> Unit,
  onCopyText: (String) -> Unit,
) {
  val context = LocalContext.current
  val rawMarkdown = remember { WorkflowDocumentationRepository.loadRawMarkdown(context) }
  var viewMode by rememberSaveable { mutableStateOf(WorkflowViewMode.INTERACTIVE) }
  var selectedSectionId by rememberSaveable { mutableStateOf("ALL") }
  var searchQuery by rememberSaveable { mutableStateOf("") }

  val filteredSections =
    remember(selectedSectionId, searchQuery) {
      val q = searchQuery.trim().lowercase()
      WorkflowDocumentationRepository.sections.filter { section ->
        val matchesChip = selectedSectionId == "ALL" || section.id == selectedSectionId
        val matchesQuery =
          q.isEmpty() ||
            section.title.lowercase().contains(q) ||
            section.userSteps.any { it.lowercase().contains(q) } ||
            section.technicalNotes.any { it.lowercase().contains(q) } ||
            (section.diagramOrCode?.lowercase()?.contains(q) == true)
        matchesChip && matchesQuery
      }
    }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(horizontal = 12.dp, vertical = 16.dp)
          .widthIn(max = 880.dp)
          .testTag("workflow_guide_dialog"),
      shape = RoundedCornerShape(28.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 4.dp,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
      Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
          ) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.primaryContainer,
              contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(38.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AccountTree, contentDescription = null, modifier = Modifier.size(20.dp))
              }
            }
            Column {
              Text(
                text = "App Workflow & Architecture",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = "WORKFLOW.md • End-to-End Guide & Technical Pipeline",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("workflow_guide_close_btn"),
          ) {
            Icon(Icons.Rounded.Close, contentDescription = "Close workflow guide")
          }
        }

        Spacer(Modifier.height(10.dp))

        // Mode Toggle + Copy Markdown Button
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
            SegmentedButton(
              selected = viewMode == WorkflowViewMode.INTERACTIVE,
              onClick = { viewMode = WorkflowViewMode.INTERACTIVE },
              shape = SegmentedButtonDefaults.itemShape(0, 2),
              modifier = Modifier.testTag("workflow_mode_interactive_btn"),
            ) {
              Text("Interactive Guide", style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
            SegmentedButton(
              selected = viewMode == WorkflowViewMode.RAW_MARKDOWN,
              onClick = { viewMode = WorkflowViewMode.RAW_MARKDOWN },
              shape = SegmentedButtonDefaults.itemShape(1, 2),
              modifier = Modifier.testTag("workflow_mode_raw_btn"),
            ) {
              Text("WORKFLOW.md", style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
          }

          Spacer(Modifier.width(8.dp))

          OutlinedButton(
            onClick = { onCopyText(rawMarkdown) },
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("workflow_copy_md_btn"),
          ) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Copy .md", style = MaterialTheme.typography.labelMedium)
          }
        }

        Spacer(Modifier.height(10.dp))

        AnimatedContent(
          targetState = viewMode,
          transitionSpec = { ExpressiveMotion.sharedAxisHorizontal(targetState == WorkflowViewMode.RAW_MARKDOWN) },
          modifier = Modifier.weight(1f).fillMaxWidth(),
          label = "workflowViewModeTransition",
        ) { mode ->
          when (mode) {
            WorkflowViewMode.INTERACTIVE -> {
              Column(modifier = Modifier.fillMaxSize()) {
                OutlinedTextField(
                  value = searchQuery,
                  onValueChange = { searchQuery = it },
                  placeholder = { Text("Search workflow steps, Smali, C++17, CFG, or rules...") },
                  leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                  singleLine = true,
                  shape = RoundedCornerShape(16.dp),
                  modifier = Modifier.fillMaxWidth().testTag("workflow_search_input"),
                )

                Spacer(Modifier.height(8.dp))

                Row(
                  modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  FilterChip(
                    selected = selectedSectionId == "ALL",
                    onClick = { selectedSectionId = "ALL" },
                    label = { Text("All Phases (${WorkflowDocumentationRepository.sections.size})") },
                    modifier = Modifier.testTag("workflow_chip_ALL"),
                  )
                  WorkflowDocumentationRepository.sections.forEach { section ->
                    FilterChip(
                      selected = selectedSectionId == section.id,
                      onClick = { selectedSectionId = section.id },
                      label = { Text(section.shortLabel) },
                      modifier = Modifier.testTag("workflow_chip_${section.id}"),
                    )
                  }
                }

                Spacer(Modifier.height(8.dp))

                LazyColumn(
                  modifier = Modifier.fillMaxSize().testTag("workflow_sections_list"),
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                  contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                  items(filteredSections, key = { it.id }) { section ->
                    WorkflowSectionCard(section = section, onCopyText = onCopyText)
                  }
                }
              }
            }

            WorkflowViewMode.RAW_MARKDOWN -> {
              Surface(
                modifier = Modifier.fillMaxSize().testTag("workflow_raw_markdown_box"),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
              ) {
                Column(
                  modifier =
                    Modifier
                      .fillMaxSize()
                      .padding(14.dp)
                      .verticalScroll(rememberScrollState())
                      .horizontalScroll(rememberScrollState()),
                ) {
                  Text(
                    text = rawMarkdown,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily, lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                }
              }
            }
          }
        }

        Spacer(Modifier.height(8.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          Button(
            onClick = onDismiss,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("workflow_done_btn"),
          ) {
            Text("Done")
          }
        }
      }
    }
  }
}

@Composable
private fun WorkflowSectionCard(
  section: WorkflowSection,
  onCopyText: (String) -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth().testTag("workflow_card_${section.id}"),
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = section.title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.weight(1f),
        )
        IconButton(
          onClick = {
            val text =
              buildString {
                appendLine(section.title)
                section.userSteps.forEach { appendLine("• $it") }
                section.technicalNotes.forEach { appendLine("• $it") }
              }
            onCopyText(text)
          },
          modifier = Modifier.size(32.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.ContentCopy,
            contentDescription = "Copy section",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (section.diagramOrCode != null) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHighest,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Box(
            modifier =
              Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .horizontalScroll(rememberScrollState()),
          ) {
            Text(
              text = section.diagramOrCode,
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily, lineHeight = 16.sp),
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }

      Text(
        text = "User Workflow",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.secondary,
      )
      section.userSteps.forEach { step ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("•", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
          Text(
            text = step,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.Code,
          contentDescription = null,
          modifier = Modifier.size(14.dp),
          tint = MaterialTheme.colorScheme.tertiary,
        )
        Text(
          text = "Technical Architecture",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.tertiary,
        )
      }
      section.technicalNotes.forEach { note ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("▸", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
          Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}
