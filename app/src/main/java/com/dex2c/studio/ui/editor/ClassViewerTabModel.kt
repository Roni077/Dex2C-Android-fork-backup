package com.dex2c.studio.ui.editor

import com.dex2c.studio.dex.DexClassInfo
import com.dex2c.studio.dex.SmaliMethodSpan
import java.util.UUID

/**
 * Represents an open class tab in the Class Viewer & Selection Studio.
 */
data class ClassViewerTab(
  val id: String = UUID.randomUUID().toString(),
  val dexClass: DexClassInfo,
  val initialLines: List<String>,
  val editedLines: List<String>? = null,
  val methodSpans: List<SmaliMethodSpan> = emptyList(),
  val isDirty: Boolean = false,
  val focusMethodName: String? = null,
  val inspectedLineIndex: Int = -1,
  val activeLineIndex: Int? = null,
  val activeColumnIndex: Int? = null,
  val isReadOnly: Boolean = true,
) {
  val lines: List<String> get() = editedLines ?: initialLines
  val smaliText: String get() = lines.joinToString("\n")
  val title: String get() = dexClass.simpleName
  val packageName: String get() = dexClass.packageName
}
