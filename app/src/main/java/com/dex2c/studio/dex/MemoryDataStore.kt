package com.dex2c.studio.dex

import com.android.tools.smali.dexlib2.writer.io.DexDataStore
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore as SmaliMemoryDataStore
import java.io.InputStream
import java.io.OutputStream

/**
 * High-performance random-access in-memory implementation of [DexDataStore].
 *
 * Supports non-linear seeking at arbitrary offsets during multi-pass DEX serialization,
 * replacing the previous naive ByteArrayOutputStream that corrupted DEX tables.
 */
class MemoryDataStore(initialCapacity: Int = 0) : DexDataStore {
  private val delegate = SmaliMemoryDataStore(initialCapacity)

  override fun outputAt(fileOffset: Int): OutputStream = delegate.outputAt(fileOffset)

  override fun readAt(fileOffset: Int): InputStream = delegate.readAt(fileOffset)

  override fun close() = delegate.close()

  val size: Int get() = delegate.size

  fun toByteArray(): ByteArray = delegate.data
}
