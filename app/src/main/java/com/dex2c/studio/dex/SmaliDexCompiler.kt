package com.dex2c.studio.dex

import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.writer.io.DexDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class CustomDexDataStore : DexDataStore {
  private val out = ByteArrayOutputStream()

  override fun outputAt(fileOffset: Int): OutputStream {
    return out
  }

  override fun readAt(fileOffset: Int): InputStream {
    return ByteArrayInputStream(out.toByteArray())
  }

  override fun close() {
    out.close()
  }

  fun toByteArray(): ByteArray = out.toByteArray()
}

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

  fun compileAndReplaceClass(
    dex: DexParsedFile,
    targetClassDescriptor: String,
    editedSmaliText: String,
  ): DexCompileResult {
    val startNanos = System.nanoTime()
    return runCatching {
      val apiLevel = resolveApiLevelForVersion(dex.header.version)
      val opcodes = Opcodes.forApi(apiLevel)
      val dexPool = DexPool(opcodes)
      val backing = SmaliClassDisassembler.getOrParseDexBackedFileStatic(dex.entryName, dex.header.sha1Hex, dex.rawBytes, apiLevel)

      for (cls in backing.classes) {
        if (cls.type != targetClassDescriptor) {
          dexPool.internClass(cls)
        }
      }

      val dataStore = CustomDexDataStore()
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
      val backing = SmaliClassDisassembler.getOrParseDexBackedFileStatic(dex.entryName, dex.header.sha1Hex, dex.rawBytes, apiLevel)

      for (cls in backing.classes) {
        if (cls.type !in descriptorsToDelete) {
          dexPool.internClass(cls)
        }
      }

      val dataStore = CustomDexDataStore()
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
    val startNanos = System.nanoTime()
    return runCatching {
      val apiLevel = resolveApiLevelForVersion(dex.header.version)
      val opcodes = Opcodes.forApi(apiLevel)
      val dexPool = DexPool(opcodes)
      val backing = SmaliClassDisassembler.getOrParseDexBackedFileStatic(dex.entryName, dex.header.sha1Hex, dex.rawBytes, apiLevel)

      for (cls in backing.classes) {
        dexPool.internClass(cls)
      }

      val dataStore = CustomDexDataStore()
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
      result to 0
    }.getOrElse { t ->
      DexCompileResult.Error(message = t.message ?: "Failed to replace strings.") to 0
    }
  }

  fun repackApkWithPatchedDexes(
    originalApkStream: java.io.InputStream,
    patchedDexByEntryName: Map<String, ByteArray>,
    outputStream: java.io.OutputStream,
  ) {
    ZipInputStream(originalApkStream).use { zis ->
      ZipOutputStream(outputStream).use { zos ->
        var entry = zis.nextEntry
        val processedEntries = mutableSetOf<String>()
        while (entry != null) {
          val name = entry.name
          zos.putNextEntry(ZipEntry(name))
          if (name in patchedDexByEntryName) {
            val bytes = patchedDexByEntryName.getValue(name)
            zos.write(bytes)
            processedEntries.add(name)
          } else {
            zis.copyTo(zos)
          }
          zos.closeEntry()
          entry = zis.nextEntry
        }
        patchedDexByEntryName.forEach { (name, bytes) ->
          if (name !in processedEntries) {
            zos.putNextEntry(ZipEntry(name))
            zos.write(bytes)
            zos.closeEntry()
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
}
