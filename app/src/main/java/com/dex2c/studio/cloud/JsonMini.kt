package com.dex2c.studio.cloud

/**
 * Pure-Kotlin JSON parser and builder so GitHub REST API models run cleanly on both
 * Android minSdk 24+ and pure JVM unit tests without org.json stub issues.
 */
object JsonMini {

  fun parseObject(rawJson: String): Map<String, Any?> = Parser(rawJson).parseObject()

  fun escape(value: String): String =
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

  fun objectJson(entries: List<Pair<String, Any?>>): String =
    buildString {
      append('{')
      entries.forEachIndexed { idx, (k, v) ->
        if (idx > 0) append(',')
        append('"').append(escape(k)).append("\":")
        append(valueJson(v))
      }
      append('}')
    }

  private fun valueJson(v: Any?): String =
    when (v) {
      null -> "null"
      is Boolean -> v.toString()
      is Number -> v.toString()
      is Map<*, *> ->
        objectJson(v.entries.map { (mk, mv) -> mk.toString() to mv })
      is List<*> ->
        v.joinToString(prefix = "[", separator = ",", postfix = "]") { valueJson(it) }
      else -> "\"${escape(v.toString())}\""
    }

  private class Parser(private val src: String) {
    private var pos = 0

    fun parseObject(): Map<String, Any?> {
      skipWhitespace()
      if (peek() != '{') throw IllegalArgumentException("Expected '{' in JSON")
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
        if (peek() != ':') throw IllegalArgumentException("Expected ':' after '$key'")
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
          else -> throw IllegalArgumentException("Expected ',' or '}' at index $pos")
        }
      }
      throw IllegalArgumentException("Unterminated JSON object")
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
      pos++
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
          else -> throw IllegalArgumentException("Expected ',' or ']' at index $pos")
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
                sb.append(src.substring(pos, pos + 4).toInt(16).toChar())
                pos += 4
              }
              else -> sb.append(esc)
            }
          }
          else -> sb.append(ch)
        }
      }
      throw IllegalArgumentException("Unterminated string")
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
      while (pos < src.length && src[pos] in "0123456789.-+eE") pos++
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
