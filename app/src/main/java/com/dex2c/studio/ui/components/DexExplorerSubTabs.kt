package com.dex2c.studio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dex2c.studio.BuildConfig
import com.dex2c.studio.dex.DexClassInfo
import com.dex2c.studio.dex.DexEncodedMethod
import com.dex2c.studio.dex.DexParsedFile
import com.dex2c.studio.ui.MethodScopeFilter
import com.dex2c.studio.ui.matchesMethodScope
import com.dex2c.studio.ui.theme.JetBrainsMonoFontFamily

@Composable
fun DexMethodsExplorerTab(
  classes: List<DexClassInfo>,
  selectedMethodIds: Set<String>,
  onToggleMethodSelection: (String) -> Unit,
  onInspectMethod: (DexClassInfo, DexEncodedMethod) -> Unit,
  onInspectClassSmali: (DexClassInfo, String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var scopeFilter by rememberSaveable { mutableStateOf(MethodScopeFilter.ALL) }

  val allMethodsWithClass = remember(classes) {
    classes.flatMap { cls ->
      (cls.directMethods + cls.virtualMethods).map { method -> cls to method }
    }
  }

  val filteredMethods = remember(allMethodsWithClass, searchQuery, scopeFilter) {
    val q = searchQuery.trim()
    allMethodsWithClass.filter { (cls, method) ->
      if (!matchesMethodScope(method, scopeFilter)) return@filter false
      if (q.isNotEmpty()) {
        val matchesName = method.methodRef.name.contains(q, ignoreCase = true)
        val matchesClass = cls.fullName.contains(q, ignoreCase = true)
        if (!matchesName && !matchesClass) return@filter false
      }
      true
    }
  }

  Surface(
    modifier = modifier.fillMaxSize(),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
  ) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        singleLine = true,
        placeholder = { Text("Search methods or classes…") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("methods_explorer_search"),
      )
      Spacer(Modifier.height(8.dp))
      Text(
        text = "Total Methods: ${filteredMethods.size}",
        style = MaterialTheme.typography.labelMedium,
        fontFamily = JetBrainsMonoFontFamily,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(6.dp))
      LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("methods_explorer_list"),
        contentPadding = PaddingValues(vertical = 4.dp),
      ) {
        itemsIndexed(
          items = filteredMethods,
          key = { _, (_, method) -> method.id },
        ) { idx, (cls, method) ->
          val isQueued = method.id in selectedMethodIds
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onInspectMethod(cls, method) }
              .padding(vertical = 6.dp, horizontal = 4.dp)
              .testTag("method_explorer_row_$idx"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Checkbox(
              checked = isQueued,
              onCheckedChange = { onToggleMethodSelection(method.id) },
              modifier = Modifier.minimumInteractiveComponentSize(),
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = method.methodRef.prettySignature(),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = cls.fullName,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            TextButton(
              onClick = { onInspectClassSmali(cls, method.methodRef.name) },
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
              modifier = Modifier.testTag("method_explorer_smali_btn_$idx"),
            ) {
              Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(Modifier.width(4.dp))
              Text("Smali", style = MaterialTheme.typography.labelSmall)
            }
          }
        }
      }
    }
  }
}

@Composable
fun DexFieldsExplorerTab(
  classes: List<DexClassInfo>,
  onInspectClassSmali: (DexClassInfo) -> Unit,
  modifier: Modifier = Modifier,
) {
  var searchQuery by rememberSaveable { mutableStateOf("") }

  val allFieldsWithClass = remember(classes) {
    classes.flatMap { cls ->
      (cls.staticFields + cls.instanceFields).map { field -> cls to field }
    }
  }

  val filteredFields = remember(allFieldsWithClass, searchQuery) {
    val q = searchQuery.trim()
    if (q.isEmpty()) allFieldsWithClass
    else allFieldsWithClass.filter { (cls, field) ->
      field.fieldRef.name.contains(q, ignoreCase = true) ||
        field.fieldRef.type.contains(q, ignoreCase = true) ||
        cls.fullName.contains(q, ignoreCase = true)
    }
  }

  Surface(
    modifier = modifier.fillMaxSize(),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
  ) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        singleLine = true,
        placeholder = { Text("Search fields, types, or classes…") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("fields_explorer_search"),
      )
      Spacer(Modifier.height(8.dp))
      Text(
        text = "Total Fields: ${filteredFields.size}",
        style = MaterialTheme.typography.labelMedium,
        fontFamily = JetBrainsMonoFontFamily,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(6.dp))
      LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("fields_explorer_list"),
        contentPadding = PaddingValues(vertical = 4.dp),
      ) {
        itemsIndexed(
          items = filteredFields,
          key = { _, (cls, field) -> "${cls.uniqueKey}::${field.fieldIndex}::${field.fieldRef.name}" },
        ) { idx, (cls, field) ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onInspectClassSmali(cls) }
              .padding(vertical = 6.dp, horizontal = 4.dp)
              .testTag("field_explorer_row_$idx"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "${field.fieldRef.name} : ${field.fieldRef.type}",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = cls.fullName,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMonoFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun DexStringsExplorerTab(
  parsedDex: DexParsedFile,
  onReplaceStringInDex: (searchQuery: String, replacement: String, ignoreCase: Boolean) -> Unit,
  onCopyString: (String) -> Unit,
  modifier: Modifier = Modifier,
  dexEditingEnabled: Boolean = BuildConfig.ENABLE_DEX_EDITING,
) {
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var replaceQuery by rememberSaveable { mutableStateOf("") }
  var showReplaceConfirmation by remember { mutableStateOf(false) }

  val strings = parsedDex.strings
  val filteredStrings = remember(strings, searchQuery) {
    val q = searchQuery.trim()
    if (q.isEmpty()) strings else strings.filter { it.contains(q, ignoreCase = true) }
  }

  if (showReplaceConfirmation) {
    AlertDialog(
      onDismissRequest = { showReplaceConfirmation = false },
      title = { Text("Confirm String Replacement") },
      text = {
        Text("Replace all occurrences of \"$searchQuery\" with \"$replaceQuery\" in active DEX? (${filteredStrings.size} matching constants found)")
      },
      confirmButton = {
        Button(
          onClick = {
            showReplaceConfirmation = false
            onReplaceStringInDex(searchQuery, replaceQuery, false)
          },
          modifier = Modifier.testTag("strings_replace_confirm_btn"),
        ) {
          Text("Replace")
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showReplaceConfirmation = false },
          modifier = Modifier.testTag("strings_replace_cancel_btn"),
        ) {
          Text("Cancel")
        }
      },
      modifier = Modifier.testTag("strings_replace_confirm_dialog"),
    )
  }

  Surface(
    modifier = modifier.fillMaxSize(),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
  ) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        singleLine = true,
        placeholder = { Text("Search string literals…") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("strings_explorer_search"),
      )
      Spacer(Modifier.height(6.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = replaceQuery,
          onValueChange = { replaceQuery = it },
          singleLine = true,
          placeholder = { Text("Replace search query with…") },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.weight(1f).testTag("strings_replace_input"),
        )
        Button(
          onClick = {
            if (searchQuery.isNotEmpty()) {
              showReplaceConfirmation = true
            }
          },
          enabled = searchQuery.isNotEmpty() && dexEditingEnabled,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.testTag("strings_replace_btn"),
        ) {
          Text("Replace")
        }
      }
      if (!dexEditingEnabled) {
        Spacer(Modifier.height(4.dp))
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth().testTag("strings_editing_disabled_note"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Info,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = "DEX string replacement is temporarily disabled while the compiler is being upgraded.",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      Spacer(Modifier.height(8.dp))
      Text(
        text = "String Constants: ${filteredStrings.size} / ${strings.size}",
        style = MaterialTheme.typography.labelMedium,
        fontFamily = JetBrainsMonoFontFamily,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(6.dp))
      LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("strings_explorer_list"),
        contentPadding = PaddingValues(vertical = 4.dp),
      ) {
        itemsIndexed(
          items = filteredStrings,
          key = { idx, str -> "$idx::${str.hashCode()}" },
        ) { idx, str ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 4.dp, horizontal = 4.dp)
              .testTag("string_explorer_row_$idx"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = str,
              style = MaterialTheme.typography.bodySmall,
              fontFamily = JetBrainsMonoFontFamily,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            IconButton(
              onClick = { onCopyString(str) },
              modifier = Modifier.size(28.dp).testTag("string_copy_btn_$idx"),
            ) {
              Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy string", modifier = Modifier.size(14.dp))
            }
          }
        }
      }
    }
  }
}
