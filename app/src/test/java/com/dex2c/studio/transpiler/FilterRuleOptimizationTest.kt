package com.dex2c.studio.transpiler

import com.dex2c.studio.dex.DexClassInfo
import com.dex2c.studio.dex.DexCodeMeta
import com.dex2c.studio.dex.DexEncodedMethod
import com.dex2c.studio.dex.DexHeaderInfo
import com.dex2c.studio.dex.DexMethodRef
import com.dex2c.studio.dex.DexParsedFile
import com.dex2c.studio.dex.DexProto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterRuleOptimizationTest {

  private fun createDummyClass(
    pkg: String,
    name: String,
    methodCount: Int,
  ): DexClassInfo {
    val descriptor = "L$pkg/$name;"
    val methods = (0 until methodCount).map { idx ->
      val methodName = if (idx == 0) "<init>" else "method$idx"
      val isInit = idx == 0
      DexEncodedMethod(
        id = "$descriptor->$methodName()V",
        methodIndex = idx,
        methodRef = DexMethodRef(
          definingClass = descriptor,
          name = methodName,
          proto = DexProto(shorty = "V", returnType = "V", parameterTypes = emptyList()),
        ),
        accessFlags = if (isInit) 0x10001 else 0x1,
        isDirect = isInit,
        codeMeta = DexCodeMeta(
          registersSize = 2,
          insSize = 1,
          outsSize = 0,
          triesSize = 0,
          insnsCount = 4,
          insnsOffsetBytes = 0,
        ),
        sourceEntryName = "classes.dex",
      )
    }

    return DexClassInfo(
      classIndex = 0,
      descriptor = descriptor,
      fullName = "$pkg.$name".replace('/', '.'),
      simpleName = name,
      packageName = pkg.replace('/', '.'),
      superclass = "Ljava/lang/Object;",
      interfaces = emptyList(),
      sourceFile = "$name.java",
      accessFlags = 1,
      directMethods = methods.filter { it.isInitConstructor },
      virtualMethods = methods.filter { !it.isInitConstructor },
      instanceFields = emptyList(),
      staticFields = emptyList(),
      sourceEntryName = "classes.dex",
    )
  }

  private fun createDummyDex(classes: List<DexClassInfo>): DexParsedFile {
    return DexParsedFile(
      sourceLabel = "test.dex",
      entryName = "classes.dex",
      rawBytes = ByteArray(128),
      header = DexHeaderInfo(
        magic = "dex\n035\u0000",
        version = "035",
        checksum = 0L,
        sha1Hex = "0000000000000000000000000000000000000000",
        fileSize = 128,
        endianTag = 0x12345678L,
        stringIdsSize = 1,
        typeIdsSize = 1,
        protoIdsSize = 0,
        fieldIdsSize = 0,
        methodIdsSize = classes.sumOf { it.allMethods.size },
        classDefsSize = classes.size,
      ),
      strings = listOf("Ljava/lang/Object;"),
      types = listOf("Ljava/lang/Object;"),
      protos = emptyList(),
      fields = emptyList(),
      methods = emptyList(),
      classes = classes,
    )
  }

  @Test
  fun testDefaultRulesMatchNonExcludedMethods() {
    val classes = listOf(
      createDummyClass("com/example/app", "MainActivity", 5),
      createDummyClass("androidx/core/app", "ComponentActivity", 5),
    )
    val dex = createDummyDex(classes)

    val config = TranspilerConfig(
      filterRulesText = """
        !^Landroidx/.*
        .*
      """.trimIndent(),
      allowInit = false,
    )

    val summary = FilterRuleEngine.evaluateAll(listOf(dex), config)

    // MainActivity: 4 virtual methods (since <init> is skipped)
    // androidx: all 5 methods excluded
    assertEquals(4, summary.matchedMethodIds.size)
    assertEquals(1, summary.matchedClassCount)
    assertEquals(2, summary.skippedInitCount)
  }

  @Test
  fun testLargeClassSetEvaluationPerformance() {
    // Generate 1,000 classes with 10 methods each (10,000 methods total)
    val classes = (0 until 1000).map { i ->
      val pkg = if (i < 200) "androidx/test/pkg$i" else "com/mycompany/app/feature$i"
      createDummyClass(pkg, "Class$i", 10)
    }
    val dex = createDummyDex(classes)

    val config = TranspilerConfig(
      filterRulesText = """
        !^Landroidx/.*
        !.*->(<clinit>)\(.*
        .*
      """.trimIndent(),
      allowInit = true,
    )

    val start = System.currentTimeMillis()
    val summary = FilterRuleEngine.evaluateAll(listOf(dex), config)
    val elapsed = System.currentTimeMillis() - start

    // 800 non-androidx classes * 10 methods = 8,000 matched methods
    assertEquals(8000, summary.matchedMethodIds.size)
    assertEquals(800, summary.matchedClassCount)
    assertEquals(800, summary.matchedClassKeys.size)

    // Verify O(1) class set lookup performance across 1,000 classes
    val classLookupStart = System.nanoTime()
    val matchingClasses = classes.filter { it.uniqueKey in summary.matchedClassKeys }
    val classLookupElapsedMs = (System.nanoTime() - classLookupStart) / 1_000_000.0
    assertEquals(800, matchingClasses.size)
    assertTrue("Class O(1) matching took ${classLookupElapsedMs}ms, expected < 10ms", classLookupElapsedMs < 10.0)

    // Ensure 10,000 methods are evaluated in < 6000ms on JVM
    assertTrue("Elapsed time was ${elapsed}ms, expected < 6000ms", elapsed < 6000)
  }

  @Test
  fun testRemoveAndUpsertStructuredRule() {
    val initialFilter = """
      com/example/MainActivity;onCreate(.*
      !com/example/SecretClass;.*
    """.trimIndent()

    val ruleToRemove = StructuredFilterRule(
      id = "rule_1",
      packageName = "com/example",
      className = "MainActivity",
      methodName = "onCreate(.*",
      isBlacklist = false,
      ruleType = FilterRuleType.CLASS_METHOD,
    )

    val afterRemove = FilterRuleEngine.removeStructuredRule(initialFilter, ruleToRemove)
    assertTrue(!afterRemove.contains("MainActivity"))
    assertTrue(afterRemove.contains("SecretClass"))

    val ruleToUpsert = StructuredFilterRule(
      id = "rule_2",
      packageName = "com/example",
      className = "NewActivity",
      methodName = "onResume(.*",
      isBlacklist = false,
      ruleType = FilterRuleType.CLASS_METHOD,
    )

    val afterUpsert = FilterRuleEngine.upsertStructuredRule(afterRemove, ruleToUpsert)
    assertTrue(afterUpsert.contains("NewActivity"))
  }

  @Test
  fun testExactMatchFilterRuleParityWithUpstream() {
    val mainClass = createDummyClass("com/example", "MainActivity", 4)
    val dex = createDummyDex(listOf(mainClass))

    val config = TranspilerConfig(
      filterRulesText = "=Lcom/example/MainActivity;method1()V",
      allowInit = false,
    )

    val summary = FilterRuleEngine.evaluateAll(listOf(dex), config)
    assertEquals(1, summary.matchedMethodIds.size)
    assertEquals(1, summary.matchedClassCount)

    val rules = FilterRuleEngine.parseStructuredRules("=Lcom/example/MainActivity;method1()V")
    assertEquals(1, rules.size)
    assertEquals(FilterRuleType.EXACT_SIGNATURE, rules[0].ruleType)
    assertEquals("=Lcom/example/MainActivity;method1()V", rules[0].toFilterLine())
  }
}
