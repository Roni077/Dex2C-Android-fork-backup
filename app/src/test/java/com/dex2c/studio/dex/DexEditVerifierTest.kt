package com.dex2c.studio.dex

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DexEditVerifierTest {

  private fun createDummyClass(descriptor: String): DexClassInfo {
    return DexClassInfo(
      classIndex = 0,
      descriptor = descriptor,
      fullName = descriptor.removePrefix("L").removeSuffix(";").replace('/', '.'),
      simpleName = descriptor.substringAfterLast('/').removeSuffix(";"),
      packageName = descriptor.removePrefix("L").substringBeforeLast('/', "").replace('/', '.'),
      superclass = "Ljava/lang/Object;",
      interfaces = emptyList(),
      sourceFile = null,
      accessFlags = 1,
      directMethods = emptyList(),
      virtualMethods = emptyList(),
      instanceFields = emptyList(),
      staticFields = emptyList(),
    )
  }

  private fun createDummyDex(classes: List<DexClassInfo>): DexParsedFile {
    val hdr = DexHeaderInfo(
      magic = "dex\n035",
      version = "035",
      checksum = 1234L,
      sha1Hex = "0123456789abcdef",
      fileSize = 100,
      endianTag = 0x12345678L,
      stringIdsSize = 1,
      typeIdsSize = 1,
      protoIdsSize = 1,
      fieldIdsSize = 1,
      methodIdsSize = 1,
      classDefsSize = classes.size,
    )
    return DexParsedFile(
      sourceLabel = "test.apk",
      entryName = "classes.dex",
      rawBytes = ByteArray(100),
      header = hdr,
      classes = classes,
    )
  }

  @Test
  fun testVerifyClassPresentSuccess() {
    val c1 = createDummyClass("Lcom/example/TargetClass;")
    val dex = createDummyDex(listOf(c1))

    val result = DexEditVerifier.verifyClassPresent(dex, "Lcom/example/TargetClass;")
    assertNull(result)
  }

  @Test
  fun testVerifyClassPresentFailsWhenAbsent() {
    val c1 = createDummyClass("Lcom/example/OtherClass;")
    val dex = createDummyDex(listOf(c1))

    val result = DexEditVerifier.verifyClassPresent(dex, "Lcom/example/TargetClass;")
    assertNotNull(result)
    assertTrue(result!!.contains("Lcom/example/TargetClass;"))
  }

  @Test
  fun testVerifyClassesDeletedSuccess() {
    val c1 = createDummyClass("Lcom/example/RetainedClass;")
    val dex = createDummyDex(listOf(c1))

    val result = DexEditVerifier.verifyClassesDeleted(
      dex,
      setOf("Lcom/example/DeletedClass1;", "Lcom/example/DeletedClass2;"),
    )
    assertNull(result)
  }

  @Test
  fun testVerifyClassesDeletedFailsWhenClassRemains() {
    val c1 = createDummyClass("Lcom/example/DeletedClass1;")
    val c2 = createDummyClass("Lcom/example/RetainedClass;")
    val dex = createDummyDex(listOf(c1, c2))

    val result = DexEditVerifier.verifyClassesDeleted(
      dex,
      setOf("Lcom/example/DeletedClass1;"),
    )
    assertNotNull(result)
    assertTrue(result!!.contains("Failed to remove 1 class(es)"))
  }

  @Test
  fun testVerifyReplacementCount() {
    assertNull(DexEditVerifier.verifyReplacementCount(5, "oldString"))
    val resultZero = DexEditVerifier.verifyReplacementCount(0, "oldString")
    assertNotNull(resultZero)
    assertTrue(resultZero!!.contains("oldString"))

    val resultNegative = DexEditVerifier.verifyReplacementCount(-1, "oldString")
    assertNotNull(resultNegative)
  }
}
