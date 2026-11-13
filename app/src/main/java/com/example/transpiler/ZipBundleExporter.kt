package com.example.transpiler

import android.content.Context
import android.net.Uri
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipBundleExporter {
  fun exportToUri(context: Context, uri: Uri, bundle: GeneratedBundle) {
    context.contentResolver.openOutputStream(uri)?.use { os ->
      ZipOutputStream(os).use { zos ->
        bundle.allFiles.forEach { file ->
          zos.putNextEntry(ZipEntry(file.zipPath))
          zos.write(file.content.toByteArray(Charsets.UTF_8))
          zos.closeEntry()
        }
      }
    } ?: throw IllegalStateException("Could not open output stream for export.")
  }
}
