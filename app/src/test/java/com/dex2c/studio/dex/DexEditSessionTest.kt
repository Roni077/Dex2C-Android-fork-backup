package com.dex2c.studio.dex

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DexEditSessionTest {

  @Test
  fun testInitialStateIsEmpty() {
    val session = DexEditSession(maxEntries = 3)
    assertFalse(session.canUndo)
    assertEquals(0, session.size)
    assertNull(session.peek())
    assertNull(session.pop())
  }

  @Test
  fun testPushAndPopLifo() {
    val session = DexEditSession(maxEntries = 3)
    val bytes1 = byteArrayOf(1, 2, 3)
    val bytes2 = byteArrayOf(4, 5, 6)

    val entry1 = session.record("Edit 1", "classes.dex", bytes1)
    assertEquals(1, session.size)
    assertTrue(session.canUndo)
    assertEquals(entry1, session.peek())

    val entry2 = session.record("Edit 2", "classes.dex", bytes2)
    assertEquals(2, session.size)
    assertEquals(entry2, session.peek())

    val popped2 = session.pop()
    assertEquals(entry2, popped2)
    assertEquals(1, session.size)

    val popped1 = session.pop()
    assertEquals(entry1, popped1)
    assertEquals(0, session.size)
    assertFalse(session.canUndo)
  }

  @Test
  fun testBoundedCapacityEvictsOldest() {
    val session = DexEditSession(maxEntries = 3)
    val e1 = session.record("Edit 1", "classes.dex", byteArrayOf(1))
    val e2 = session.record("Edit 2", "classes.dex", byteArrayOf(2))
    val e3 = session.record("Edit 3", "classes.dex", byteArrayOf(3))
    assertEquals(3, session.size)

    // 4th entry evicts e1
    val e4 = session.record("Edit 4", "classes.dex", byteArrayOf(4))
    assertEquals(3, session.size)

    assertEquals(e4, session.pop())
    assertEquals(e3, session.pop())
    assertEquals(e2, session.pop())
    assertNull(session.pop())
  }

  @Test
  fun testClearEmptiesSession() {
    val session = DexEditSession(maxEntries = 3)
    session.record("Edit 1", "classes.dex", byteArrayOf(1))
    session.record("Edit 2", "classes.dex", byteArrayOf(2))
    assertEquals(2, session.size)

    session.clear()
    assertEquals(0, session.size)
    assertFalse(session.canUndo)
    assertNull(session.peek())
    assertNull(session.pop())
  }

  @Test
  fun testDefensiveCopyOfBeforeBytes() {
    val session = DexEditSession(maxEntries = 3)
    val original = byteArrayOf(10, 20, 30)
    val entry = session.record("Edit", "classes.dex", original)

    // Mutate the original buffer after recording
    original[0] = 99

    assertNotNull(entry)
    assertArrayEquals(byteArrayOf(10, 20, 30), entry.beforeBytes)
    val popped = session.pop()
    assertNotNull(popped)
    assertArrayEquals(byteArrayOf(10, 20, 30), popped?.beforeBytes)
  }
}
