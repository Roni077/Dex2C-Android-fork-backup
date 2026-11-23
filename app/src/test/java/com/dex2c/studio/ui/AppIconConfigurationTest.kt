package com.dex2c.studio.ui

import com.dex2c.studio.data.AppIconStyle
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class AppIconConfigurationTest {

  private fun findProjectRootDir(): File {
    var dir: File? = File(".").canonicalFile
    while (dir != null && !File(dir, "app").exists()) {
      dir = dir.parentFile
    }
    return dir ?: File(".")
  }

  @Test
  fun appIconStyle_hasOnlyDarkAndLight() {
    val entries = AppIconStyle.entries
    assertTrue("Should have exactly 2 icon styles", entries.size == 2)
    assertTrue("Contains DARK", entries.any { it.name == "DARK" })
    assertTrue("Contains LIGHT", entries.any { it.name == "LIGHT" })
    assertTrue("DEFAULT is DARK", AppIconStyle.DEFAULT == AppIconStyle.DARK)
  }

  @Test
  fun appIconStyle_manifestAliasesMatchEntries() {
    val root = findProjectRootDir()
    val manifestFile = File(root, "app/src/main/AndroidManifest.xml")
    assertTrue("AndroidManifest.xml must exist", manifestFile.exists())

    val factory = DocumentBuilderFactory.newInstance()
    val builder = factory.newDocumentBuilder()
    val doc = builder.parse(manifestFile)
    val aliasNodes = doc.getElementsByTagName("activity-alias")

    val declaredAliases = mutableSetOf<String>()
    for (i in 0 until aliasNodes.length) {
      val element = aliasNodes.item(i) as Element
      val name = element.getAttribute("android:name")
      declaredAliases.add(name.removePrefix("."))
    }

    AppIconStyle.entries.forEach { style ->
      assertTrue(
        "Alias ${style.aliasName} must be declared in AndroidManifest.xml",
        declaredAliases.contains(style.aliasName)
      )
    }
  }

  @Test
  fun appIconStyle_previewDrawablesExist() {
    val root = findProjectRootDir()
    val drawableDir = File(root, "app/src/main/res/drawable")
    assertTrue(File(drawableDir, "ic_dex2c_dark_src.png").exists())
    assertTrue(File(drawableDir, "ic_dex2c_light_src.png").exists())
    assertTrue(File(drawableDir, "ic_dex2c_icon_src.png").exists())
    assertTrue(File(drawableDir, "ic_launcher_monochrome.xml").exists())
  }
}
