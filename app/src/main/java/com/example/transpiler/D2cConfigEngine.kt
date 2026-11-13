package com.example.transpiler

import com.example.dex.DexEncodedMethod
import com.example.dex.DexParsedFile

data class FilterMatchSummary(
  val matchedMethodIds: Set<String>,
  val matchedClassCount: Int,
  val skippedInitCount: Int,
  val invalidRules: List<String>,
)

object FilterRuleEngine {

  fun evaluate(dex: DexParsedFile, config: TranspilerConfig): FilterMatchSummary =
    evaluateAll(listOf(dex), config)

  fun evaluateAll(dexFiles: Collection<DexParsedFile>, config: TranspilerConfig): FilterMatchSummary {
    val includes = mutableListOf<Regex>()
    val excludes = mutableListOf<Regex>()
    val invalid = mutableListOf<String>()

    config.filterRulesText.lineSequence().forEach { rawLine ->
      val line = rawLine.trim()
      if (line.isEmpty() || line.startsWith("#")) return@forEach
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

    val matchedIds = linkedSetOf<String>()
    val matchedClasses = linkedSetOf<String>()
    var skippedInits = 0

    for (dex in dexFiles) {
      for (cls in dex.classes) {
        for (method in cls.allMethods) {
          if (!method.hasBytecode) continue
          if (!config.allowInit && method.isInitConstructor) {
            skippedInits++
            continue
          }
          if (matchesRules(cls.fullName, method, includes, excludes)) {
            matchedIds += method.id
            matchedClasses += cls.uniqueKey
          }
        }
      }
    }

    return FilterMatchSummary(
      matchedMethodIds = matchedIds,
      matchedClassCount = matchedClasses.size,
      skippedInitCount = skippedInits,
      invalidRules = invalid,
    )
  }

  private fun matchesRules(
    javaClassName: String,
    method: DexEncodedMethod,
    includes: List<Regex>,
    excludes: List<Regex>,
  ): Boolean {
    val dalvikSig = method.methodRef.dalvikSignature()
    val dotSig = "$javaClassName.${method.methodRef.name}"

    val isExcluded = excludes.any { it.containsMatchIn(dalvikSig) || it.containsMatchIn(dotSig) }
    if (isExcluded) return false

    if (includes.isEmpty()) return true
    return includes.any { it.containsMatchIn(dalvikSig) || it.containsMatchIn(dotSig) }
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

    return buildString {
      appendLine("{")
      appendLine("  \"ndk_dir\": \"/path/to/android-ndk\",")
      appendLine("  \"lib_name\": \"${escapeJson(libName)}\",")
      appendLine("  \"allow_init\": ${config.allowInit},")
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

    return base.copy(
      moduleName = moduleName,
      allowInit = (map["allow_init"] as? Boolean) ?: base.allowInit,
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
