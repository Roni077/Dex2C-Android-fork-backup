package com.dex2c.studio.transpiler

import com.dex2c.studio.dex.DexClassInfo
import com.dex2c.studio.dex.DexEncodedMethod
import com.dex2c.studio.dex.DexParsedFile
import com.dex2c.studio.nativeengine.DexNativeEngine

data class FilterMatchSummary(
  val matchedMethodIds: Set<String>,
  val matchedClassCount: Int,
  val skippedInitCount: Int,
  val invalidRules: List<String>,
  val matchedClassKeys: Set<String> = emptySet(),
)

object FilterRuleEngine {

  fun evaluate(dex: DexParsedFile, config: TranspilerConfig): FilterMatchSummary =
    evaluateAll(listOf(dex), config)

  fun evaluateAll(dexFiles: Collection<DexParsedFile>, config: TranspilerConfig): FilterMatchSummary {
    if (DexNativeEngine.isAvailable) {
      val nativeResult = tryNativeEvaluateAll(dexFiles, config)
      if (nativeResult != null) {
        return nativeResult
      }
    }

    val includes = mutableListOf<Regex>()
    val excludes = mutableListOf<Regex>()
    val exactMatches = mutableSetOf<String>()
    val invalid = mutableListOf<String>()

    config.filterRulesText.lineSequence().forEach { rawLine ->
      val line = rawLine.trim()
      if (line.isEmpty() || line.startsWith("#")) return@forEach
      if (line.startsWith("=")) {
        val exact = line.substring(1).trim()
        if (exact.isNotEmpty()) {
          exactMatches += exact
        }
        return@forEach
      }
      val isExclude = line.startsWith("!")
      val pattern = if (isExclude) line.substring(1).trim() else line
      if (pattern.isEmpty()) return@forEach

      val compiled = runCatching { Regex(pattern) }.getOrNull()
      if (compiled == null) {
        invalid += line
      } else if (isExclude) {
        excludes += compiled
      } else {
        includes += compiled
      }
    }

    val hasCatchAllInclude =
      (includes.isEmpty() && exactMatches.isEmpty()) || includes.any { it.pattern == ".*" || it.pattern == "^.*$" }

    val matchedIds = linkedSetOf<String>()
    val matchedClasses = linkedSetOf<String>()
    var skippedInits = 0

    for (cls in dexFiles.flatMap { it.classes }) {
      val classDesc = cls.descriptor
      val classNoSemi = classDesc.removeSuffix(";")
      val javaName = cls.fullName

      for (method in cls.allMethods) {
        if (!method.hasBytecode) continue
        if (!config.allowInit && method.isInitConstructor) {
          skippedInits++
          continue
        }
        if (config.skipSynthetic && method.isCoroutineOrSyntheticArtifact) {
          continue
        }
        if (matchesRules(javaName, classNoSemi, method, includes, excludes, exactMatches, hasCatchAllInclude)) {
          matchedIds += method.id
          matchedClasses += cls.uniqueKey
        }
      }
    }

    return FilterMatchSummary(
      matchedMethodIds = matchedIds,
      matchedClassCount = matchedClasses.size,
      skippedInitCount = skippedInits,
      invalidRules = invalid,
      matchedClassKeys = matchedClasses,
    )
  }

  private fun tryNativeEvaluateAll(
    dexFiles: Collection<DexParsedFile>,
    config: TranspilerConfig,
  ): FilterMatchSummary? {
    if (!DexNativeEngine.isAvailable || dexFiles.isEmpty()) return null
    val ruleLines =
      config.filterRulesText
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
    if (ruleLines.isEmpty()) return null

    val matchedIds = linkedSetOf<String>()
    val matchedClasses = linkedSetOf<String>()
    var skippedInits = 0

    for (dex in dexFiles) {
      if (dex.rawBytes.isEmpty()) return null
      val nativeResult =
        DexNativeEngine.evaluateFilterRulesFast(
          dexBytes = dex.rawBytes,
          rules = ruleLines,
          allowInit = config.allowInit,
          skipSynthetic = config.skipSynthetic,
        ) ?: return null

      val matchedClassIndices = nativeResult.matchedClassIndices
      val matchedMethodIndices = nativeResult.matchedMethodIndices

      for (cls in dex.classes) {
        if (cls.classIndex in matchedClassIndices) {
          matchedClasses += cls.uniqueKey
        }
        for (m in cls.allMethods) {
          if (!m.hasBytecode) continue
          if (!config.allowInit && m.isInitConstructor) {
            skippedInits++
            continue
          }
          if (m.methodIndex in matchedMethodIndices) {
            matchedIds += m.id
          }
        }
      }
    }

    return FilterMatchSummary(
      matchedMethodIds = matchedIds,
      matchedClassCount = matchedClasses.size,
      skippedInitCount = skippedInits,
      invalidRules = emptyList(),
      matchedClassKeys = matchedClasses,
    )
  }

  fun buildCloudFilterText(
    config: TranspilerConfig,
    selectedMethods: List<DexEncodedMethod> = emptyList(),
  ): String {
    val customExcludes = mutableListOf<String>()
    val customIncludes = mutableListOf<String>()

    config.filterRulesText.lineSequence().forEach { rawLine ->
      val line = rawLine.trim()
      if (line.isEmpty() || line.startsWith("#")) return@forEach
      if (line.startsWith("!")) {
        customExcludes += line
      } else {
        customIncludes += line
      }
    }

    val eligibleSelected =
      selectedMethods.filter { method ->
        method.hasBytecode &&
          (config.allowInit || !method.isInitConstructor) &&
          (!config.skipSynthetic || !method.isCoroutineOrSyntheticArtifact)
      }

    return buildString {
      appendLine("# Generated by Dex2C Studio for codehasan/dex2c")
      appendLine("# Lines starting with ! exclude matching classes/methods")
      val emittedExcludes = linkedSetOf<String>()
      if (config.skipSynthetic) {
        for (rule in MANDATORY_CLOUD_SAFETY_EXCLUSIONS) {
          if (emittedExcludes.add(rule)) {
            appendLine(rule)
          }
        }
      }
      for (rule in customExcludes) {
        if (emittedExcludes.add(rule)) {
          appendLine(rule)
        }
      }

      if (eligibleSelected.isNotEmpty()) {
        appendLine("# Scoped to ${eligibleSelected.size} selected method(s)")
        val emittedIncludes = linkedSetOf<String>()
        for (method in eligibleSelected) {
          val rawCls = method.methodRef.definingClass.removeSuffix(";")
          val methodName = method.methodRef.name
          val rule = "^${escapePortableRegex(rawCls)};${escapePortableRegex(methodName)}\\(.*"
          if (emittedIncludes.add(rule)) {
            appendLine(rule)
          }
        }
      } else {
        val includesToEmit = if (customIncludes.isEmpty()) listOf(".*") else customIncludes
        for (rule in includesToEmit) {
          appendLine(rule)
        }
      }
    }
  }

  private fun escapePortableRegex(input: String): String =
    buildString(input.length + 8) {
      for (ch in input) {
        if (ch in "\\.^$*+?()[]{}|") {
          append('\\')
        }
        append(ch)
      }
    }

  private fun matchesRules(
    javaClassName: String,
    classNoSemi: String,
    method: DexEncodedMethod,
    includes: List<Regex>,
    excludes: List<Regex>,
    exactMatches: Set<String>,
    hasCatchAllInclude: Boolean,
  ): Boolean {
    val dalvikSig = method.methodRef.dalvikSignature()
    val dccSig = "${method.methodRef.definingClass}${method.methodRef.name}${method.methodRef.jniSignature()}"
    val dotSig = "$javaClassName.${method.methodRef.name}"

    fun matchesAny(regex: Regex): Boolean {
      if (regex.containsMatchIn(dalvikSig)) return true
      if (regex.containsMatchIn(classNoSemi)) return true
      if (regex.containsMatchIn(dotSig)) return true
      return regex.containsMatchIn(dccSig)
    }

    if (excludes.isNotEmpty() && excludes.any(::matchesAny)) {
      return false
    }

    if (exactMatches.isNotEmpty()) {
      if (exactMatches.contains(dalvikSig) || exactMatches.contains(dccSig) || exactMatches.contains(dotSig)) {
        return true
      }
      val exactMatchPrefixes = exactMatches.filter { it.endsWith("(") || it.endsWith("()") }
      if (exactMatchPrefixes.any { dalvikSig.contains(it) || dccSig.contains(it) || dotSig.contains(it) }) {
        return true
      }
    }

    if (hasCatchAllInclude) return true
    return includes.any(::matchesAny)
  }

  /**
   * Generates canonical Dex2C wildcard filter rules from selected packages, classes, and methods,
   * matching `springmusk026/Dex2C-Android`'s `FilterBuilderViewModel.generateFilterRules`:
   * - Entire package selected -> `com/package/.*;.*`
   * - Entire class selected   -> `com/package/Class;.*`
   * - Specific method selected -> `com/package/Class;methodName\(.*`
   */
  fun generateWildcardRulesFromSelection(
    classes: List<DexClassInfo>,
    selectedMethodIds: Set<String>,
    allowInit: Boolean = false,
    isBlacklist: Boolean = false,
  ): List<StructuredFilterRule> {
    if (selectedMethodIds.isEmpty() || classes.isEmpty()) return emptyList()

    val rules = mutableListOf<StructuredFilterRule>()
    val byPackage = classes.groupBy { it.packageName }

    for ((pkgDot, pkgClasses) in byPackage) {
      val slashPkg = if (pkgDot == "(default)" || pkgDot.isBlank()) "" else pkgDot.replace('.', '/')
      val pkgEligibleIds =
        pkgClasses.flatMap { cls ->
          cls.allMethods.filter { it.hasBytecode && (allowInit || !it.isInitConstructor) }.map { it.id }
        }
      if (pkgEligibleIds.isEmpty()) continue

      val selectedInPkg = pkgEligibleIds.count { it in selectedMethodIds }
      if (selectedInPkg == 0) continue

      // If all classes in a non-default package with >=2 classes are completely selected, emit package wildcard
      if (slashPkg.isNotEmpty() && pkgClasses.size >= 2 && selectedInPkg == pkgEligibleIds.size) {
        rules +=
          StructuredFilterRule(
            packageName = slashPkg,
            className = "",
            methodName = "",
            isBlacklist = isBlacklist,
            ruleType = FilterRuleType.PACKAGE_ALL,
          )
        continue
      }

      for (cls in pkgClasses) {
        val clsEligible = cls.allMethods.filter { it.hasBytecode && (allowInit || !it.isInitConstructor) }
        if (clsEligible.isEmpty()) continue
        val clsSelected = clsEligible.filter { it.id in selectedMethodIds }
        if (clsSelected.isEmpty()) continue

        if (clsSelected.size == clsEligible.size) {
          rules +=
            StructuredFilterRule(
              packageName = slashPkg,
              className = cls.simpleName,
              methodName = "",
              isBlacklist = isBlacklist,
              ruleType = FilterRuleType.ALL_CLASS_METHODS,
            )
        } else {
          val seenMethodNames = linkedSetOf<String>()
          for (m in clsSelected) {
            val methodToken = "${m.methodRef.name}("
            if (seenMethodNames.add(methodToken)) {
              rules +=
                StructuredFilterRule(
                  packageName = slashPkg,
                  className = cls.simpleName,
                  methodName = methodToken,
                  isBlacklist = isBlacklist,
                  ruleType = FilterRuleType.CLASS_METHOD,
                )
            }
          }
        }
      }
    }
    return rules
  }

  fun parseStructuredRules(filterText: String): List<StructuredFilterRule> {
    if (filterText.isBlank()) return emptyList()
    return filterText
      .lineSequence()
      .map { it.trim() }
      .filter { it.isNotEmpty() && !it.startsWith("#") }
      .mapIndexed { idx, line ->
        val isExact = line.startsWith("=")
        val isBlacklist = line.startsWith("!")
        val clean = line.removePrefix("!").removePrefix("=").trim().removePrefix("^")
        val semiIdx = clean.indexOf(';')
        if (isExact) {
          StructuredFilterRule(
            id = "rule_${idx}_${line.hashCode()}",
            packageName = "",
            className = "",
            methodName = "",
            isBlacklist = false,
            ruleType = FilterRuleType.EXACT_SIGNATURE,
            customPattern = clean,
          )
        } else if (semiIdx > 0) {
          val classPart = clean.substring(0, semiIdx).removePrefix("L")
          val methodPart =
            clean
              .substring(semiIdx + 1)
              .replace("\\(", "(")
              .replace("\\)", ")")
              .removeSuffix(".*")
          val slashIdx = classPart.lastIndexOf('/')
          val pkg = if (slashIdx > 0) classPart.substring(0, slashIdx) else ""
          val cls = if (slashIdx >= 0) classPart.substring(slashIdx + 1) else classPart
          val ruleType =
            when {
              cls == ".*" && (methodPart.isEmpty() || methodPart == ".*") -> FilterRuleType.PACKAGE_ALL
              methodPart.isEmpty() || methodPart == ".*" -> FilterRuleType.ALL_CLASS_METHODS
              classPart == ".*" -> FilterRuleType.METHOD_ALL_CLASSES
              else -> FilterRuleType.CLASS_METHOD
            }
          StructuredFilterRule(
            id = "rule_${idx}_${line.hashCode()}",
            packageName = if (classPart == ".*") "" else pkg,
            className = if (cls == ".*") "" else cls,
            methodName = if (methodPart == ".*") "" else methodPart,
            isBlacklist = isBlacklist,
            ruleType = ruleType,
            customPattern = null,
          )
        } else {
          StructuredFilterRule(
            id = "rule_${idx}_${line.hashCode()}",
            packageName = "",
            className = "",
            methodName = "",
            isBlacklist = isBlacklist,
            ruleType = FilterRuleType.CUSTOM_REGEX,
            customPattern = clean,
          )
        }
      }
      .toList()
  }

  fun serializeStructuredRules(rules: List<StructuredFilterRule>): String =
    rules.joinToString("\n") { it.toFilterLine() }

  fun toggleRuleLine(filterText: String, ruleLine: String): String {
    val trimmedTarget = ruleLine.trim()
    if (trimmedTarget.isEmpty()) return filterText
    val existingLines =
      filterText
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toMutableList()
    if (trimmedTarget in existingLines) {
      existingLines.removeAll { it == trimmedTarget }
    } else {
      val opposite =
        if (trimmedTarget.startsWith("!")) trimmedTarget.removePrefix("!")
        else "!$trimmedTarget"
      existingLines.removeAll { it == opposite }
      existingLines.add(trimmedTarget)
    }
    return existingLines.joinToString("\n")
  }

  fun upsertStructuredRule(filterText: String, newRule: StructuredFilterRule): String {
    val targetLine = newRule.toFilterLine().trim()
    if (targetLine.isEmpty()) return filterText
    val expr = newRule.toRuleExpression().trim()
    val existingLines =
      filterText
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toMutableList()
    existingLines.removeAll { line ->
      val clean = line.removePrefix("!").trim()
      clean == expr
    }
    existingLines.add(targetLine)
    return existingLines.joinToString("\n")
  }

  fun removeStructuredRule(filterText: String, rule: StructuredFilterRule): String {
    val expr = rule.toRuleExpression().trim()
    val targetLine = rule.toFilterLine().trim()
    val normTarget = targetLine.replace("\\(", "(").replace("\\)", ")")
    val existingLines =
      filterText
        .lineSequence()
        .map { it.trim() }
        .filter { line ->
          if (line.isEmpty()) return@filter false
          if (line == targetLine || line.removePrefix("!").trim() == expr) return@filter false
          val normLine = line.replace("\\(", "(").replace("\\)", ")")
          normLine != normTarget && normLine.removePrefix("!").trim() != normTarget.removePrefix("!")
        }
        .toList()
    return existingLines.joinToString("\n")
  }

  fun mergeFilterLines(currentText: String, newLines: List<String>): String {
    val existing =
      currentText
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toCollection(LinkedHashSet())
    val hasOnlyWildcard = existing.size == 1 && existing.contains(".*")
    if (hasOnlyWildcard && newLines.any { it.trim().isNotEmpty() }) {
      existing.remove(".*")
    }
    for (line in newLines) {
      val trimmed = line.trim()
      if (trimmed.isNotEmpty()) {
        existing.add(trimmed)
      }
    }
    return existing.joinToString("\n")
  }
}

/**
 * Filter rule scope types modeled after `springmusk026/Dex2C-Android`'s `FilterBuilderScreen`.
 */
enum class FilterRuleType(val displayName: String, val description: String) {
  CLASS_METHOD("Specific Method", "Protect or exclude one method in a class"),
  ALL_CLASS_METHODS("All Class Methods", "Protect or exclude all methods in a class"),
  PACKAGE_ALL("Package All", "Protect or exclude all classes and methods in a package"),
  METHOD_ALL_CLASSES("Method in All Classes", "Match a method name across all classes"),
  CUSTOM_REGEX("Custom Pattern", "Raw regular expression pattern"),
  EXACT_SIGNATURE("Exact Signature", "Exact method signature equality (=Lpkg/Class;method(args)Ret)"),
}

data class FilterPresetItem(
  val id: String,
  val label: String,
  val ruleLine: String,
  val isBlacklist: Boolean,
  val description: String,
)

val LIFECYCLE_SECURITY_PRESETS =
  listOf(
    FilterPresetItem(
      id = "onCreate",
      label = "+onCreate()",
      ruleLine = ".*;onCreate\\(.*",
      isBlacklist = false,
      description = "Whitelist Activity/Fragment/Application onCreate lifecycle methods",
    ),
    FilterPresetItem(
      id = "attachBaseContext",
      label = "+attachBaseContext()",
      ruleLine = ".*;attachBaseContext\\(.*",
      isBlacklist = false,
      description = "Whitelist Application/Activity attachBaseContext entry points",
    ),
    FilterPresetItem(
      id = "onResume",
      label = "+onResume()",
      ruleLine = ".*;onResume\\(.*",
      isBlacklist = false,
      description = "Whitelist onResume lifecycle methods",
    ),
    FilterPresetItem(
      id = "verify",
      label = "+verify*()",
      ruleLine = ".*;verify.*\\(.*",
      isBlacklist = false,
      description = "Whitelist verification, signature, and token check methods",
    ),
    FilterPresetItem(
      id = "check",
      label = "+check*()",
      ruleLine = ".*;check.*\\(.*",
      isBlacklist = false,
      description = "Whitelist root/tamper/license check methods",
    ),
    FilterPresetItem(
      id = "decrypt",
      label = "+decrypt*()",
      ruleLine = ".*;decrypt.*\\(.*",
      isBlacklist = false,
      description = "Whitelist decryption and key derivation methods",
    ),
  )

val FRAMEWORK_BLACKLIST_PRESETS =
  listOf(
    FilterPresetItem(
      id = "androidx",
      label = "!androidx/*",
      ruleLine = "!androidx/.*;.*",
      isBlacklist = true,
      description = "Exclude AndroidX Jetpack library classes",
    ),
    FilterPresetItem(
      id = "kotlin",
      label = "!kotlin/*",
      ruleLine = "!kotlin/.*;.*",
      isBlacklist = true,
      description = "Exclude Kotlin standard library & coroutines classes",
    ),
    FilterPresetItem(
      id = "kotlinx",
      label = "!kotlinx/*",
      ruleLine = "!kotlinx/.*;.*",
      isBlacklist = true,
      description = "Exclude KotlinX serialization and coroutines runtime",
    ),
    FilterPresetItem(
      id = "material",
      label = "!material/*",
      ruleLine = "!com/google/android/material/.*;.*",
      isBlacklist = true,
      description = "Exclude Google Material Components classes",
    ),
    FilterPresetItem(
      id = "r_buildconfig",
      label = "!R$ / BuildConfig",
      ruleLine = "!.*/R\\$?.*;.*",
      isBlacklist = true,
      description = "Exclude generated R resource and BuildConfig classes",
    ),
  )

val COMMON_ANDROID_METHODS =
  listOf(
    "onCreate",
    "attachBaseContext",
    "onStart",
    "onResume",
    "onPause",
    "onStop",
    "onDestroy",
    "verify",
    "check",
    "decrypt",
    "onClick",
    "run",
    "execute",
    "doInBackground",
  )

data class StructuredFilterRule(
  val id: String = "rule_${System.nanoTime()}",
  val packageName: String,
  val className: String,
  val methodName: String,
  val isBlacklist: Boolean = false,
  val ruleType: FilterRuleType = FilterRuleType.CLASS_METHOD,
  val customPattern: String? = null,
) {
  fun toRuleExpression(): String {
    if (ruleType == FilterRuleType.EXACT_SIGNATURE) {
      val raw = customPattern?.trim().orEmpty()
      return if (raw.startsWith("=")) raw.substring(1).trim() else raw
    }
    if (ruleType == FilterRuleType.CUSTOM_REGEX && !customPattern.isNullOrBlank()) {
      return customPattern.trim()
    }
    val normPkg = packageName.trim().replace('.', '/').trim('/')
    val normCls = className.trim().removeSuffix(";")
    val normMethod = methodName.trim()
    val escapedMethod =
      if (normMethod.isEmpty() || normMethod == ".*") {
        ".*"
      } else if (normMethod.endsWith("(")) {
        "${normMethod.removeSuffix("(")}\\(.*"
      } else if ('(' in normMethod && "\\(" !in normMethod) {
        normMethod.replace("(", "\\(").replace(")", "\\)")
      } else {
        normMethod
      }
    return when (ruleType) {
      FilterRuleType.PACKAGE_ALL ->
        if (normPkg.isNotEmpty()) "$normPkg/.*;.*" else ".*;.*"
      FilterRuleType.ALL_CLASS_METHODS ->
        if (normPkg.isNotEmpty()) "$normPkg/$normCls;.*" else "$normCls;.*"
      FilterRuleType.METHOD_ALL_CLASSES ->
        ".*;$escapedMethod"
      FilterRuleType.CLASS_METHOD ->
        if (normPkg.isNotEmpty()) "$normPkg/$normCls;$escapedMethod" else "$normCls;$escapedMethod"
      FilterRuleType.CUSTOM_REGEX, FilterRuleType.EXACT_SIGNATURE ->
        customPattern?.trim().orEmpty()
    }
  }

  fun toDisplayLabel(): String {
    if (ruleType == FilterRuleType.EXACT_SIGNATURE) {
      val raw = customPattern?.trim().orEmpty()
      return if (raw.startsWith("=")) raw else "=$raw"
    }
    if (ruleType == FilterRuleType.CUSTOM_REGEX && !customPattern.isNullOrBlank()) {
      return customPattern.trim()
    }
    val normPkg = packageName.trim().replace('.', '/').trim('/')
    val normCls = className.trim()
    val normMethod = methodName.trim().ifEmpty { ".*" }
    return when (ruleType) {
      FilterRuleType.PACKAGE_ALL -> "${normPkg.ifEmpty { "*" }}/.*;.*"
      FilterRuleType.ALL_CLASS_METHODS ->
        if (normPkg.isNotEmpty()) "$normPkg/$normCls;.*" else "$normCls;.*"
      FilterRuleType.METHOD_ALL_CLASSES -> ".*;$normMethod"
      FilterRuleType.CLASS_METHOD ->
        if (normPkg.isNotEmpty()) "$normPkg/$normCls;$normMethod" else "$normCls;$normMethod"
      FilterRuleType.CUSTOM_REGEX -> customPattern.orEmpty()
      FilterRuleType.EXACT_SIGNATURE -> "=${customPattern.orEmpty()}"
    }
  }

  fun toFilterLine(): String {
    val expr = toRuleExpression()
    if (expr.isBlank()) return ""
    return when {
      ruleType == FilterRuleType.EXACT_SIGNATURE -> if (expr.startsWith("=")) expr else "=$expr"
      isBlacklist -> "!$expr"
      else -> expr
    }
  }
}

object DccConfigCodec {

  fun encodeDccCfg(config: TranspilerConfig): String {
    val libName = config.moduleName.ifBlank { "dex2c_native" }
    val obfuscate = config.stringEncryption || config.controlFlowFlattening
    val dynamicReg = config.jniStyle == JniBindingStyle.REGISTER_NATIVES
    val customLoader = config.customLoaderClass.ifBlank { "amimo.dcc.DccApplication" }
    val abis = config.targetAbis.sorted()
    val abiJson =
      if (abis.isEmpty()) {
        "[]"
      } else {
        abis.joinToString(prefix = "[\n    ", separator = ",\n    ", postfix = "\n  ]") {
          "\"${escapeJson(it)}\""
        }
      }

    val sig = config.signature
    val signatureJson =
      """
      |  "signature": {
      |    "keystore_path": "${escapeJson(sig.keystorePath)}",
      |    "alias": "${escapeJson(sig.alias)}",
      |    "keystore_pass": "${escapeJson(sig.keystorePass)}",
      |    "store_pass": "${escapeJson(sig.storePass)}",
      |    "v1_enabled": ${sig.v1Enabled},
      |    "v2_enabled": ${sig.v2Enabled},
      |    "v3_enabled": ${sig.v3Enabled}
      |  },
      """.trimMargin()

    val ollvmJson =
      if (config.ollvm.enabled) {
        val flags = config.ollvm.buildClangFlags()
        """
        |  "ollvm": {
        |    "enable": true,
        |    "flags": "${escapeJson(flags)}"
        |  },
        """.trimMargin()
      } else {
        """
        |  "ollvm": {
        |    "enable": false,
        |    "flags": "-fvisibility=hidden -mllvm -fla -mllvm -split -mllvm -split_num=5 -mllvm -sub -mllvm -sub_loop=5 -mllvm -sobf -mllvm -bcf_loop=5 -mllvm -bcf_prob=100"
        |  },
        """.trimMargin()
      }

    return buildString {
      appendLine("{")
      appendLine("  \"apktool\": \"tools/apktool.jar\",")
      appendLine("  \"ndk_dir\": \"/path/to/android-ndk\",")
      appendLine("  \"lib_name\": \"${escapeJson(libName)}\",")
      appendLine("  \"allow_init\": ${config.allowInit},")
      appendLine("  \"skip_synthetic\": ${config.skipSynthetic},")
      appendLine("  \"dynamic_register\": $dynamicReg,")
      appendLine("  \"obfuscate\": $obfuscate,")
      appendLine("  \"string_encryption\": ${config.stringEncryption},")
      appendLine("  \"control_flow_flattening\": ${config.controlFlowFlattening},")
      appendLine("  \"null_check_guards\": ${config.nullCheckGuards},")
      appendLine("  \"loader_mode\": \"${escapeJson(config.loaderMode.name.lowercase())}\",")
      appendLine("  \"custom_loader\": \"${escapeJson(customLoader)}\",")
      appendLine("  \"force_custom_loader\": ${config.forceCustomLoader},")
      appendLine("  \"source_lang\": \"${escapeJson(config.targetLanguage.extension)}\",")
      appendLine("  \"filter_file\": \"filter.txt\",")
      appendLine(signatureJson)
      append(ollvmJson)
      appendLine()
      appendLine("  \"abi_filters\": $abiJson")
      appendLine("}")
    }
  }

  fun decodeDccCfg(rawJson: String, base: TranspilerConfig): TranspilerConfig {
    val map = SimpleJsonParser(rawJson).parseObject()
    val abis =
      (map["abi_filters"] as? List<*>)
        ?.mapNotNull { it as? String }
        ?.filter { it in ALL_NDK_ABIS }
        ?.toSet()
        ?.ifEmpty { base.targetAbis }
        ?: base.targetAbis

    val dynamicReg =
      (map["dynamic_register"] as? Boolean) ?: (base.jniStyle == JniBindingStyle.REGISTER_NATIVES)
    val obf = (map["obfuscate"] as? Boolean) ?: false
    val strEnc = (map["string_encryption"] as? Boolean) ?: (obf || base.stringEncryption)
    val cfFlat = (map["control_flow_flattening"] as? Boolean) ?: (obf || base.controlFlowFlattening)
    val langStr = ((map["source_lang"] as? String) ?: base.targetLanguage.extension).lowercase()
    val lang = if (langStr == "c") TargetCLanguage.C11 else TargetCLanguage.CPP17
    val loaderModeStr = ((map["loader_mode"] as? String) ?: "").uppercase()
    val loaderMode =
      runCatching { LoaderMode.valueOf(loaderModeStr) }.getOrDefault(base.loaderMode)
    val moduleName = ((map["lib_name"] as? String) ?: base.moduleName).ifBlank { base.moduleName }
    val customLoader =
      ((map["custom_loader"] as? String) ?: base.customLoaderClass).ifBlank {
        base.customLoaderClass
      }

    val sigMap = map["signature"] as? Map<*, *>
    val resolvedSig =
      if (sigMap != null) {
        DccSignatureConfig(
          keystorePath = (sigMap["keystore_path"] as? String) ?: base.signature.keystorePath,
          alias = (sigMap["alias"] as? String) ?: base.signature.alias,
          keystorePass = (sigMap["keystore_pass"] as? String) ?: base.signature.keystorePass,
          storePass = (sigMap["store_pass"] as? String) ?: base.signature.storePass,
          v1Enabled = (sigMap["v1_enabled"] as? Boolean) ?: base.signature.v1Enabled,
          v2Enabled = (sigMap["v2_enabled"] as? Boolean) ?: base.signature.v2Enabled,
          v3Enabled = (sigMap["v3_enabled"] as? Boolean) ?: base.signature.v3Enabled,
        )
      } else {
        base.signature
      }

    val ollvmMap = map["ollvm"] as? Map<*, *>
    val ollvmEnable = (ollvmMap?.get("enable") as? Boolean) ?: base.ollvm.enabled
    val ollvmFlags = (ollvmMap?.get("flags") as? String) ?: ""
    val resolvedOllvm =
      if (ollvmMap != null) {
        base.ollvm.copy(
          enabled = ollvmEnable,
          controlFlowFlattening = if (ollvmFlags.isNotEmpty()) "-mllvm -fla" in ollvmFlags else base.ollvm.controlFlowFlattening,
          instructionSubstitution = if (ollvmFlags.isNotEmpty()) "-mllvm -sub" in ollvmFlags else base.ollvm.instructionSubstitution,
          bogusControlFlow = if (ollvmFlags.isNotEmpty()) "-mllvm -bcf" in ollvmFlags else base.ollvm.bogusControlFlow,
          basicBlockSplit = if (ollvmFlags.isNotEmpty()) "-mllvm -split" in ollvmFlags else base.ollvm.basicBlockSplit,
          stringObfuscation = if (ollvmFlags.isNotEmpty()) "-mllvm -sobf" in ollvmFlags else base.ollvm.stringObfuscation,
        )
      } else {
        base.ollvm
      }

    return base.copy(
      moduleName = moduleName,
      allowInit = (map["allow_init"] as? Boolean) ?: base.allowInit,
      skipSynthetic = (map["skip_synthetic"] as? Boolean) ?: base.skipSynthetic,
      jniStyle =
        if (dynamicReg) JniBindingStyle.REGISTER_NATIVES else JniBindingStyle.STATIC_SYMBOLS,
      stringEncryption = strEnc,
      controlFlowFlattening = cfFlat,
      nullCheckGuards = (map["null_check_guards"] as? Boolean) ?: base.nullCheckGuards,
      loaderMode = loaderMode,
      customLoaderClass = customLoader,
      forceCustomLoader = (map["force_custom_loader"] as? Boolean) ?: base.forceCustomLoader,
      targetLanguage = lang,
      targetAbis = abis,
      ollvm = resolvedOllvm,
      signature = resolvedSig,
    )
  }

  private fun escapeJson(value: String): String =
    buildString(value.length) {
      for (ch in value) {
        when (ch) {
          '\\' -> append("\\\\")
          '"' -> append("\\\"")
          '\n' -> append("\\n")
          '\r' -> append("\\r")
          '\t' -> append("\\t")
          else -> append(ch)
        }
      }
    }

  private class SimpleJsonParser(private val src: String) {
    private var pos = 0

    fun parseObject(): Map<String, Any?> {
      skipWhitespace()
      if (peek() != '{') throw IllegalArgumentException("Expected '{' in dcc.cfg")
      pos++
      val result = linkedMapOf<String, Any?>()
      skipWhitespace()
      if (peek() == '}') {
        pos++
        return result
      }
      while (pos < src.length) {
        skipWhitespace()
        val key = parseString()
        skipWhitespace()
        if (peek() != ':') throw IllegalArgumentException("Expected ':' after key '$key'")
        pos++
        skipWhitespace()
        result[key] = parseValue()
        skipWhitespace()
        when (peek()) {
          ',' -> pos++
          '}' -> {
            pos++
            return result
          }
          else -> throw IllegalArgumentException("Expected ',' or '}' in dcc.cfg at index $pos")
        }
      }
      throw IllegalArgumentException("Unterminated JSON object in dcc.cfg")
    }

    private fun parseValue(): Any? {
      skipWhitespace()
      return when (val ch = peek()) {
        '{' -> parseObject()
        '[' -> parseArray()
        '"' -> parseString()
        't' -> parseLiteral("true", true)
        'f' -> parseLiteral("false", false)
        'n' -> parseLiteral("null", null)
        '-', in '0'..'9' -> parseNumber()
        else -> throw IllegalArgumentException("Unexpected token '$ch' at index $pos")
      }
    }

    private fun parseArray(): List<Any?> {
      pos++ // '['
      val list = mutableListOf<Any?>()
      skipWhitespace()
      if (peek() == ']') {
        pos++
        return list
      }
      while (pos < src.length) {
        skipWhitespace()
        list += parseValue()
        skipWhitespace()
        when (peek()) {
          ',' -> pos++
          ']' -> {
            pos++
            return list
          }
          else -> throw IllegalArgumentException("Expected ',' or ']' in array at index $pos")
        }
      }
      throw IllegalArgumentException("Unterminated JSON array")
    }

    private fun parseString(): String {
      if (peek() != '"') throw IllegalArgumentException("Expected '\"' at index $pos")
      pos++
      val sb = StringBuilder()
      while (pos < src.length) {
        val ch = src[pos++]
        when (ch) {
          '"' -> return sb.toString()
          '\\' -> {
            if (pos >= src.length) throw IllegalArgumentException("Unterminated escape")
            when (val esc = src[pos++]) {
              '"', '\\', '/' -> sb.append(esc)
              'b' -> sb.append('\b')
              'f' -> sb.append('\u000C')
              'n' -> sb.append('\n')
              'r' -> sb.append('\r')
              't' -> sb.append('\t')
              'u' -> {
                if (pos + 4 > src.length) throw IllegalArgumentException("Invalid unicode escape")
                val hex = src.substring(pos, pos + 4)
                sb.append(hex.toInt(16).toChar())
                pos += 4
              }
              else -> sb.append(esc)
            }
          }
          else -> sb.append(ch)
        }
      }
      throw IllegalArgumentException("Unterminated string literal")
    }

    private fun parseLiteral(expected: String, value: Any?): Any? {
      if (!src.startsWith(expected, pos)) {
        throw IllegalArgumentException("Expected '$expected' at index $pos")
      }
      pos += expected.length
      return value
    }

    private fun parseNumber(): Number {
      val start = pos
      while (pos < src.length && src[pos] in "0123456789.-+eE") {
        pos++
      }
      val raw = src.substring(start, pos)
      return raw.toLongOrNull() ?: raw.toDoubleOrNull()
        ?: throw IllegalArgumentException("Invalid number '$raw'")
    }

    private fun skipWhitespace() {
      while (pos < src.length && src[pos].isWhitespace()) pos++
    }

    private fun peek(): Char =
      if (pos < src.length) src[pos] else throw IllegalArgumentException("Unexpected end of JSON")
  }
}
