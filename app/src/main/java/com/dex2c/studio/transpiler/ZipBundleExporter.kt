package com.dex2c.studio.transpiler

import android.content.Context
import android.net.Uri
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipBundleExporter {
  private const val STREAM_BUFFER_BYTES = 16 * 1024

  fun exportToUri(context: Context, uri: Uri, bundle: GeneratedBundle) {
    context.contentResolver.openOutputStream(uri)?.use { os ->
      ZipOutputStream(BufferedOutputStream(os, STREAM_BUFFER_BYTES)).use { zos ->
        val buffer = ByteArray(STREAM_BUFFER_BYTES)
        bundle.allFiles.forEach { file ->
          zos.putNextEntry(ZipEntry(file.zipPath))
          file.openInputStream().use { input ->
            while (true) {
              val read = input.read(buffer)
              if (read <= 0) break
              zos.write(buffer, 0, read)
            }
          }
          zos.closeEntry()
        }
      }
    } ?: throw IllegalStateException("Could not open output stream for export.")
  }
}
