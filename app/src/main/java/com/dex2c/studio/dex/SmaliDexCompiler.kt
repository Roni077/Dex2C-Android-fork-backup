package com.dex2c.studio.dex

import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.instruction.DexBackedInstruction
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction31c
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.value.EncodedValue
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction31c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import com.android.tools.smali.dexlib2.immutable.value.ImmutableStringEncodedValue
import com.android.tools.smali.dexlib2.rewriter.DexRewriter
import com.android.tools.smali.dexlib2.rewriter.Rewriter
import com.android.tools.smali.dexlib2.rewriter.RewriterModule
import com.android.tools.smali.dexlib2.rewriter.Rewriters
import com.android.tools.smali.dexlib2.writer.builder.DexBuilder
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import com.android.tools.smali.smali.smaliFlexLexer
import com.android.tools.smali.smali.smaliParser
import com.android.tools.smali.smali.smaliTreeWalker
import org.antlr.runtime.CommonTokenStream
import org.antlr.runtime.RecognitionException
import org.antlr.runtime.tree.CommonTree
import org.antlr.runtime.tree.CommonTreeNodeStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Deprecated("Use MemoryDataStore instead", ReplaceWith("MemoryDataStore"))
typealias CustomDexDataStore = MemoryDataStore

sealed interface DexCompileResult {
  data class Success(
    val updatedDex: DexParsedFile,
    val updatedDexBytes: ByteArray,
    val durationMs: Long,
  ) : DexCompileResult

  data class Error(
    val message: String,
    val line: Int? = null,
  ) : DexCompileResult
}

object SmaliDexCompiler {

  fun assembleSmali(smaliText: String, apiLevel: Int): Pair<ClassDef?, DexCompileResult.Error?> {
    val errorMessages = mutableListOf<String>()
    var errorLine: Int? = null

    val opcodes = Opcodes.forApi(apiLevel)
    val dexBuilder = DexBuilder(opcodes)
    val reader = StringReader(smaliText)
    val lexer = smaliFlexLexer(reader, apiLevel)
    val tokens = CommonTokenStream(lexer)

    val parser = object : smaliParser(tokens) {
      override fun displayRecognitionError(tokenNames: Array<out String>?, e: RecognitionException?) {
        if (e != null && errorLine == null) errorLine = e.line
        val msg = getErrorMessage(e, tokenNames)
        val header = getErrorHeader(e)
        errorMessages.add("$header $msg")
      }
    }
    parser.setVerboseErrors(true)
    parser.setAllowOdex(false)
    parser.setApiLevel(apiLevel)

    val fileReturn = parser.smali_file()
    if (parser.numberOfSyntaxErrors > 0 || lexer.numberOfSyntaxErrors > 0) {
      val message = if (errorMessages.isNotEmpty()) {
        errorMessages.joinToString("\n")
      } else {
        "Syntax error encountered while parsing Smali"
      }
      return null to DexCompileResult.Error(message = message, line = errorLine)
    }

    val tree = fileReturn.tree as? CommonTree
      ?: return null to DexCompileResult.Error(message = "Smali parser did not produce an AST tree.", line = errorLine)

    val treeStream = CommonTreeNodeStream(tree)
    treeStream.tokenStream = tokens

    val treeWalker = object : smaliTreeWalker(treeStream) {
      override fun displayRecognitionError(tokenNames: Array<out String>?, e: RecognitionException?) {
        if (e != null && errorLine == null) errorLine = e.line
        val msg = getErrorMessage(e, tokenNames)
        val header = getErrorHeader(e)
        errorMessages.add("$header $msg")
      }
    }
    treeWalker.setApiLevel(apiLevel)
    treeWalker.setVerboseErrors(true)
    treeWalker.setDexBuilder(dexBuilder)

    val classDef = treeWalker.smali_file()
    if (treeWalker.numberOfSyntaxErrors > 0 || classDef == null) {
      val message = if (errorMessages.isNotEmpty()) {
        errorMessages.joinToString("\n")
      } else {
        "Tree walker error encountered while assembling Smali"
      }
      return null to DexCompileResult.Error(message = message, line = errorLine)
    }

    return classDef to null
  }

  fun compileAndReplaceClass(
    dex: DexParsedFile,
    targetClassDescriptor: String,
    editedSmaliText: String,
  ): DexCompileResult {
    val startNanos = System.nanoTime()
    return runCatching {
      val apiLevel = resolveApiLevelForVersion(dex.header.version)
      val (compiledClassDef, compileError) = assembleSmali(editedSmaliText, apiLevel)
      if (compileError != null) {
        return compileError
      }
      if (compiledClassDef == null) {
        return DexCompileResult.Error(message = "Compiled Smali class is null.")
      }

      val opcodes = Opcodes.forApi(apiLevel)
      val dexPool = DexPool(opcodes)
      val backing = SmaliClassDisassembler.getOrParseDexBackedFileStatic(
        dex.entryName,
        dex.header.sha1Hex,
        dex.rawBytes,
        apiLevel,
      )

      var replaced = false
      for (cls in backing.classes) {
        if (cls.type == targetClassDescriptor || cls.type == compiledClassDef.type) {
          if (!replaced) {
            dexPool.internClass(compiledClassDef)
            replaced = true
          }
        } else {
          dexPool.internClass(cls)
        }
      }

      if (!replaced) {
        dexPool.internClass(compiledClassDef)
      }

      val dataStore = MemoryDataStore()
      dexPool.writeTo(dataStore)
      val newBytes = dataStore.toByteArray()

      val updatedParsed = DexParser.parseDex(
        rawBytes = newBytes,
        sourceLabel = dex.sourceLabel,
        entryName = dex.entryName,
      )

      val duration = ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(1L)
      DexCompileResult.Success(
        updatedDex = updatedParsed,
        updatedDexBytes = newBytes,
        durationMs = duration,
      )
    }.getOrElse { t ->
      DexCompileResult.Error(message = t.message ?: "Failed to assemble Smali class.")
    }
  }

  fun deleteClassesFromDex(
    dex: DexParsedFile,
    descriptorsToDelete: Set<String>,
  ): DexCompileResult {
    val startNanos = System.nanoTime()
    return runCatching {
      val apiLevel = resolveApiLevelForVersion(dex.header.version)
      val opcodes = Opcodes.forApi(apiLevel)
      val dexPool = DexPool(opcodes)
      val backing = SmaliClassDisassembler.getOrParseDexBackedFileStatic(
        dex.entryName,
        dex.header.sha1Hex,
        dex.rawBytes,
        apiLevel,
      )

      for (cls in backing.classes) {
        if (cls.type !in descriptorsToDelete) {
          dexPool.internClass(cls)
        }
      }

      val dataStore = MemoryDataStore()
      dexPool.writeTo(dataStore)
      val newBytes = dataStore.toByteArray()

      val updatedParsed = DexParser.parseDex(
        rawBytes = newBytes,
        sourceLabel = dex.sourceLabel,
        entryName = dex.entryName,
      )

      val duration = ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(1L)
      DexCompileResult.Success(
        updatedDex = updatedParsed,
        updatedDexBytes = newBytes,
        durationMs = duration,
      )
    }.getOrElse { t ->
      DexCompileResult.Error(message = t.message ?: "Failed to remove classes.")
    }
  }

  fun replaceStringLiteralsInDex(
    dex: DexParsedFile,
    searchQuery: String,
    replacement: String,
    ignoreCase: Boolean,
  ): Pair<DexCompileResult, Int> {
    if (searchQuery.isEmpty()) {
      return DexCompileResult.Error(message = "Search query cannot be empty.") to 0
    }
    val startNanos = System.nanoTime()
    return runCatching {
      val apiLevel = resolveApiLevelForVersion(dex.header.version)
      val opcodes = Opcodes.forApi(apiLevel)
      val dexPool = DexPool(opcodes)
      val backing = SmaliClassDisassembler.getOrParseDexBackedFileStatic(
        dex.entryName,
        dex.header.sha1Hex,
        dex.rawBytes,
        apiLevel,
      )

      var replacementCount = 0
      val seenInstructionOffsets = mutableSetOf<Int>()
      val seenFallbackInstructions = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Instruction, Boolean>())
      val seenRawEncodedValues = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<EncodedValue, Boolean>())

      fun replaceInString(source: String): Pair<String, Int> {
        if (!source.contains(searchQuery, ignoreCase = ignoreCase)) {
          return source to 0
        }
        var count = 0
        var idx = 0
        val sb = StringBuilder()
        while (idx < source.length) {
          val found = source.indexOf(searchQuery, idx, ignoreCase = ignoreCase)
          if (found < 0) {
            sb.append(source.substring(idx))
            break
          }
          sb.append(source.substring(idx, found))
          sb.append(replacement)
          idx = found + searchQuery.length
          count++
        }
        return sb.toString() to count
      }

      val rewriterModule = object : RewriterModule() {
        override fun getInstructionRewriter(rewriters: Rewriters): Rewriter<Instruction> {
          val defaultRewriter = super.getInstructionRewriter(rewriters)
          return Rewriter { rawInsn ->
            val offset = (rawInsn as? DexBackedInstruction)?.instructionStart
            val isFirstVisit = if (offset != null) seenInstructionOffsets.add(offset) else seenFallbackInstructions.add(rawInsn)
            val insn = defaultRewriter.rewrite(rawInsn)
            when (insn.opcode) {
              Opcode.CONST_STRING -> {
                val refInsn = insn as? Instruction21c
                val stringRef = refInsn?.reference as? StringReference
                if (stringRef != null) {
                  val (newStr, count) = replaceInString(stringRef.string)
                  if (count > 0) {
                    if (isFirstVisit) replacementCount += count
                    ImmutableInstruction21c(Opcode.CONST_STRING, refInsn.registerA, ImmutableStringReference(newStr))
                  } else {
                    insn
                  }
                } else {
                  insn
                }
              }
              Opcode.CONST_STRING_JUMBO -> {
                val refInsn = insn as? Instruction31c
                val stringRef = refInsn?.reference as? StringReference
                if (stringRef != null) {
                  val (newStr, count) = replaceInString(stringRef.string)
                  if (count > 0) {
                    if (isFirstVisit) replacementCount += count
                    ImmutableInstruction31c(Opcode.CONST_STRING_JUMBO, refInsn.registerA, ImmutableStringReference(newStr))
                  } else {
                    insn
                  }
                } else {
                  insn
                }
              }
              else -> insn
            }
          }
        }

        override fun getEncodedValueRewriter(rewriters: Rewriters): Rewriter<EncodedValue> {
          val defaultRewriter = super.getEncodedValueRewriter(rewriters)
          return Rewriter { rawValue ->
            val isFirstVisit = seenRawEncodedValues.add(rawValue)
            val value = defaultRewriter.rewrite(rawValue)
            if (value is StringEncodedValue) {
              val (newStr, count) = replaceInString(value.value)
              if (count > 0) {
                if (isFirstVisit) replacementCount += count
                ImmutableStringEncodedValue(newStr)
              } else {
                value
              }
            } else {
              value
            }
          }
        }
      }

      val dexRewriter = DexRewriter(rewriterModule)
      for (cls in backing.classes) {
        val rewrittenClass = dexRewriter.classDefRewriter.rewrite(cls)
        dexPool.internClass(rewrittenClass)
      }

      val dataStore = MemoryDataStore()
      dexPool.writeTo(dataStore)
      val newBytes = dataStore.toByteArray()

      val updatedParsed = DexParser.parseDex(
        rawBytes = newBytes,
        sourceLabel = dex.sourceLabel,
        entryName = dex.entryName,
      )

      val duration = ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(1L)
      val result = DexCompileResult.Success(
        updatedDex = updatedParsed,
        updatedDexBytes = newBytes,
        durationMs = duration,
      )
      result to replacementCount
    }.getOrElse { t ->
      DexCompileResult.Error(message = t.message ?: "Failed to replace strings.") to 0
    }
  }

  fun isSignatureOrManifestEntry(name: String): Boolean {
    val upper = name.uppercase()
    if (!upper.startsWith("META-INF/")) return false
    if (upper == "META-INF/MANIFEST.MF") return true
    if (upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC")) return true
    if (upper.startsWith("META-INF/SIG-")) return true
    return false
  }

  fun repackApkWithPatchedDexes(
    originalApkStream: java.io.InputStream,
    patchedDexByEntryName: Map<String, ByteArray>,
    outputStream: java.io.OutputStream,
  ) {
    val countingOut = CountingOutputStream(outputStream)
    ZipInputStream(originalApkStream).use { zis ->
      ZipOutputStream(countingOut).use { zos ->
        var entry = zis.nextEntry
        val processedEntries = mutableSetOf<String>()

        fun writeAlignedEntry(
          name: String,
          bytes: ByteArray,
          forceStored: Boolean,
          alignment: Int,
        ) {
          val newEntry = ZipEntry(name)
          val nameBytes = name.toByteArray(Charsets.UTF_8)
          if (forceStored) {
            newEntry.method = ZipEntry.STORED
            val crc = CRC32().apply { update(bytes) }
            newEntry.size = bytes.size.toLong()
            newEntry.compressedSize = bytes.size.toLong()
            newEntry.crc = crc.value

            val unalignedDataOffset = countingOut.count + 30L + nameBytes.size
            val padding = ((alignment - (unalignedDataOffset % alignment)) % alignment).toInt()
            if (padding > 0) {
              newEntry.extra = ByteArray(padding)
            }
          } else {
            newEntry.method = ZipEntry.DEFLATED
          }
          zos.putNextEntry(newEntry)
          zos.write(bytes)
          zos.closeEntry()
          processedEntries.add(name)
        }

        while (entry != null) {
          val name = entry.name
          if (isSignatureOrManifestEntry(name)) {
            entry = zis.nextEntry
            continue
          }

          val bytes = if (name in patchedDexByEntryName) {
            patchedDexByEntryName.getValue(name)
          } else {
            zis.readBytes()
          }

          val isSo = name.endsWith(".so", ignoreCase = true)
          val isArsc = name.equals("resources.arsc", ignoreCase = true)
          val forceStored = isSo || isArsc || entry.method == ZipEntry.STORED
          val alignment = when {
            isSo -> 16384 // 16KB page alignment for Android 15/16 compatibility
            isArsc -> 4   // 4-byte alignment for resources.arsc
            forceStored -> 4
            else -> 1
          }

          writeAlignedEntry(name, bytes, forceStored, alignment)
          entry = zis.nextEntry
        }

        patchedDexByEntryName.forEach { (name, bytes) ->
          if (name !in processedEntries) {
            writeAlignedEntry(name, bytes, forceStored = false, alignment = 1)
          }
        }
      }
    }
  }

  fun buildDexContainerZip(dexByEntryName: Map<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zos ->
      dexByEntryName.forEach { (name, bytes) ->
        zos.putNextEntry(ZipEntry(name))
        zos.write(bytes)
        zos.closeEntry()
      }
    }
    return out.toByteArray()
  }

  private fun resolveApiLevelForVersion(version: String): Int =
    when (version.trim()) {
      "035" -> 23
      "037" -> 25
      "038" -> 27
      "039" -> 29
      "040" -> 34
      "041" -> 35
      else -> 35
    }

  private class CountingOutputStream(private val out: OutputStream) : OutputStream() {
    var count: Long = 0L
      private set

    override fun write(b: Int) {
      out.write(b)
      count++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
      out.write(b, off, len)
      count += len
    }

    override fun flush() = out.flush()
    override fun close() = out.close()
  }
}
