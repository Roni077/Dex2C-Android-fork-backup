package com.dex2c.studio.dex

/**
 * Snapshot of a DEX entry's raw bytes before an edit operation was applied.
 */
data class DexEditUndoEntry(
  val id: Long,
  val label: String,
  val entryName: String,
  val beforeBytes: ByteArray,
  val timestampMs: Long = System.currentTimeMillis(),
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as DexEditUndoEntry
    return id == other.id &&
      label == other.label &&
      entryName == other.entryName &&
      beforeBytes.contentEquals(other.beforeBytes) &&
      timestampMs == other.timestampMs
  }

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + label.hashCode()
    result = 31 * result + entryName.hashCode()
    result = 31 * result + beforeBytes.contentHashCode()
    result = 31 * result + timestampMs.hashCode()
    return result
  }
}

/**
 * UI prompt data emitted to trigger an interactive Undo action (e.g. snackbar).
 */
data class DexEditUndoPrompt(
  val id: Long,
  val message: String,
  val entryName: String,
)

/**
 * Session-scoped bounded LIFO undo buffer for DEX binary modifications.
 * Holds at most [maxEntries] snapshots (default 3) to prevent unbounded memory growth.
 */
class DexEditSession(private val maxEntries: Int = 3) {
  private val stack = ArrayDeque<DexEditUndoEntry>()

  val canUndo: Boolean
    @Synchronized get() = stack.isNotEmpty()

  val size: Int
    @Synchronized get() = stack.size

  /**
   * Records a snapshot of [beforeBytes] for [entryName] before applying changes.
   * Evicts the oldest entry if [maxEntries] is reached.
   */
  @Synchronized
  fun record(label: String, entryName: String, beforeBytes: ByteArray): DexEditUndoEntry {
    require(maxEntries > 0) { "maxEntries must be positive" }
    while (stack.size >= maxEntries) {
      stack.removeFirst()
    }
    val entry = DexEditUndoEntry(
      id = System.nanoTime(),
      label = label,
      entryName = entryName,
      beforeBytes = beforeBytes.copyOf(),
    )
    stack.addLast(entry)
    return entry
  }

  /**
   * Returns the most recent undo snapshot without removing it.
   */
  @Synchronized
  fun peek(): DexEditUndoEntry? = stack.lastOrNull()

  /**
   * Pops and returns the most recent undo snapshot.
   */
  @Synchronized
  fun pop(): DexEditUndoEntry? = stack.removeLastOrNull()

  /**
   * Clears all recorded undo snapshots.
   */
  @Synchronized
  fun clear() {
    stack.clear()
  }
}
