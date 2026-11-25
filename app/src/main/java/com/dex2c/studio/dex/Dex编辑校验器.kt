package com.dex2c.studio.dex

/**
 * Pure post-condition verification gates to ensure DEX modifications meet invariants
 * before in-memory state is replaced.
 */
object DexEditVerifier {

  /**
   * Asserts that [descriptor] is present in [parsed].
   * Returns an error description if missing, or null if verification passed.
   */
  fun verifyClassPresent(parsed: DexParsedFile, descriptor: String): String? {
    val found = parsed.classes.any { it.descriptor == descriptor }
    return if (!found) {
      "Target class $descriptor was not found in assembled DEX output."
    } else null
  }

  /**
   * Asserts that all [deletedDescriptors] are completely absent from [parsed].
   * Returns an error description if any deleted class is still present, or null if passed.
   */
  fun verifyClassesDeleted(parsed: DexParsedFile, deletedDescriptors: Set<String>): String? {
    val remaining = parsed.classes.filter { it.descriptor in deletedDescriptors }
    return if (remaining.isNotEmpty()) {
      "Failed to remove ${remaining.size} class(es); still present in DEX output."
    } else null
  }

  /**
   * Asserts that [count] of replacements is greater than zero.
   * Returns an error description if 0 or negative, or null if passed.
   */
  fun verifyReplacementCount(count: Int, query: String): String? {
    return if (count <= 0) {
      "No string literals matching '$query' found in active DEX."
    } else null
  }
}
