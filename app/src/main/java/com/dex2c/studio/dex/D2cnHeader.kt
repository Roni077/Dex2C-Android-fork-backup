package com.dex2c.studio.dex

/**
 * Header metadata for the packed D2CN native binary buffer.
 * Magic is 0x4E433244 ('D', '2', 'C', 'N' in Little-Endian).
 */
data class D2cnHeader(
  val magic: Int,
  val version: Short,
  val flags: Short,
  val headerSize: Int,
  val stringCount: Int,
  val classCount: Int,
  val methodCount: Int,
  val stringPoolOffset: Int,
  val classesOffset: Int,
) {
  companion object {
    const val MAGIC: Int = 0x4E433244 // "D2CN" in Little-Endian
    const val CURRENT_VERSION: Short = 1
    const val HEADER_SIZE: Int = 32
  }
}
