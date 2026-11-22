package com.dex2c.studio.ui

import com.dex2c.studio.data.AppIconStyle
import com.dex2c.studio.data.AppLanguage
import com.dex2c.studio.data.AppThemeMode
import com.dex2c.studio.data.SmaliSyntaxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ChineseLocalizationParityTest {

  private fun parseStringKeys(file: File): Map<String, String> {
    assertTrue("Resource file ${file.path} must exist", file.exists())
    val factory = DocumentBuilderFactory.newInstance()
    val builder = factory.newDocumentBuilder()
    val doc = builder.parse(file)
    val stringNodes = doc.getElementsByTagName("string")

    val map = mutableMapOf<String, String>()
    for (i in 0 until stringNodes.length) {
      val node = stringNodes.item(i) as Element
      val name = node.getAttribute("name")
      val text = node.textContent
      map[name] = text
    }
    return map
  }

  private fun findProjectRootDir(): File {
    var dir: File? = File(".").canonicalFile
    while (dir != null && !File(dir, "app").exists()) {
      dir = dir.parentFile
    }
    return dir ?: File(".")
  }

  @Test
  fun valuesZh_hasFullKeyParityWithValuesDefault() {
    val root = findProjectRootDir()
    val enFile = File(root, "app/src/main/res/values/strings.xml")
    val zhFile = File(root, "app/src/main/res/values-zh/strings.xml")
    val zhCnFile = File(root, "app/src/main/res/values-zh-rCN/strings.xml")

    val enMap = parseStringKeys(enFile)
    val zhMap = parseStringKeys(zhFile)
    val zhCnMap = parseStringKeys(zhCnFile)

    assertTrue("Base strings.xml must contain at least 300 keys", enMap.size >= 300)

    val missingInZh = enMap.keys - zhMap.keys
    assertTrue("Missing in values-zh: $missingInZh", missingInZh.isEmpty())

    val missingInZhCn = enMap.keys - zhCnMap.keys
    assertTrue("Missing in values-zh-rCN: $missingInZhCn", missingInZhCn.isEmpty())

    // Also assert that none of the Chinese translations are empty
    zhMap.forEach { (key, value) ->
      assertTrue("values-zh key '$key' must not be blank", value.isNotBlank())
    }
    zhCnMap.forEach { (key, value) ->
      assertTrue("values-zh-rCN key '$key' must not be blank", value.isNotBlank())
    }
  }

  @Test
  fun appLocalesConfig_declaresAllChineseVariants() {
    val root = findProjectRootDir()
    val localesFile = File(root, "app/src/main/res/xml/locales_config.xml")
    assertTrue("locales_config.xml must exist", localesFile.exists())
    val content = localesFile.readText()

    assertTrue("Must support en", content.contains("android:name=\"en\""))
    assertTrue("Must support zh", content.contains("android:name=\"zh\""))
    assertTrue("Must support zh-CN", content.contains("android:name=\"zh-CN\""))
    assertTrue("Must support zh-Hans", content.contains("android:name=\"zh-Hans\""))
  }

  @Test
  fun uiEnums_haveValidStringResourceIdentifiers() {
    AppThemeMode.entries.forEach { mode ->
      assertTrue("AppThemeMode.${mode.name} labelRes must be non-zero", mode.labelRes != 0)
      assertTrue("AppThemeMode.${mode.name} summaryRes must be non-zero", mode.summaryRes != 0)
    }

    SmaliSyntaxTheme.entries.forEach { theme ->
      assertTrue("SmaliSyntaxTheme.${theme.name} labelRes must be non-zero", theme.labelRes != 0)
      assertTrue("SmaliSyntaxTheme.${theme.name} shortLabelRes must be non-zero", theme.shortLabelRes != 0)
    }

    AppIconStyle.entries.forEach { iconStyle ->
      assertTrue("AppIconStyle.${iconStyle.name} labelRes must be non-zero", iconStyle.labelRes != 0)
      assertTrue("AppIconStyle.${iconStyle.name} descriptionRes must be non-zero", iconStyle.descriptionRes != 0)
    }

    assertEquals("zh", AppLanguage.CHINESE_SIMPLIFIED.code)
  }
}
