package com.dex2c.studio.cloud

object CloudNamingConvention {

  /**
   * Sanitizes any raw input (file name, app label, package name, or path) into a safe,
   * human-readable base name suitable for git paths, GitHub Actions artifacts, and SAF export files.
   *
   * Examples:
   * - "Dex2C Studio (base.apk)" -> "dex2c_studio"
   * - "com.example.app-v1.0.apk" -> "com.example.app-v1.0"
   * - "MyApp (Debug).apk" -> "myapp_debug"
   * - "target.apk" -> "target"
   */
  fun sanitizeBaseName(raw: String?, fallback: String = "app"): String {
    if (raw.isNullOrBlank()) return fallback
    val withoutPath = raw.substringAfterLast('/').substringAfterLast('\\')
    val withoutExtension = withoutPath
      .removeSuffix(".apk")
      .removeSuffix(".APK")
      .removeSuffix(".dex")
      .removeSuffix(".DEX")
      .removeSuffix(".zip")
      .removeSuffix(".ZIP")
      .removeSuffix(".log")
      .removeSuffix(".LOG")
      .removeSuffix(".txt")
      .removeSuffix(".TXT")
    val cleanedParentheses = withoutExtension
      .replace(Regex("""\s*\((?:base(?:\.apk)?)?\)\s*""", RegexOption.IGNORE_CASE), "")
      .replace('(', '_')
      .replace(')', '_')
    val safeChars = cleanedParentheses.trim().replace(Regex("""[^A-Za-z0-9._-]"""), "_")
    val collapsed = safeChars
      .replace(Regex("""_{2,}"""), "_")
      .replace(Regex("""\.{2,}"""), ".")
      .trim('_', '.', '-')
    return collapsed.ifBlank { fallback }.lowercase()
  }

  fun uploadApkRepoPath(baseName: String): String = "input/$baseName.apk"

  fun uploadCommitMessage(baseName: String): String = "Upload $baseName.apk for Dex2C build"

  fun signedApkFileName(baseName: String): String {
    val clean = if (baseName.endsWith("_dex2c")) baseName else "${baseName}_dex2c"
    return "${clean}_signed.apk"
  }

  fun buildLogFileName(baseName: String): String {
    val clean = if (baseName.endsWith("_dex2c")) baseName else "${baseName}_dex2c"
    return "${clean}_build.log"
  }

  fun signedApkArtifactName(baseName: String): String = "$baseName-signed-apk"

  fun buildLogArtifactName(baseName: String): String = "$baseName-build-log"
}
