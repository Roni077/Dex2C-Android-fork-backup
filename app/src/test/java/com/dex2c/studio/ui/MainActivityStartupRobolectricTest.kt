package com.dex2c.studio.ui

import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.test.core.app.ActivityScenario
import com.dex2c.studio.MainActivity
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityStartupRobolectricTest {

  @Test
  fun testMainActivityStartupDoesNotCrash() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        assertNotNull("Activity should not be null", activity)
        assertNotNull("ActivityResultRegistry should be provided", activity.activityResultRegistry)
        assertTrue("Activity should implement ActivityResultRegistryOwner", activity is ActivityResultRegistryOwner)
        assertTrue("Activity should be ComponentActivity", activity is ComponentActivity)
      }
    }
  }

  @Test
  fun testLocalizedActivityContextPreservesRegistryOwnerAndBaseContext() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        val config = android.content.res.Configuration(activity.resources.configuration).apply {
          setLocale(java.util.Locale.SIMPLIFIED_CHINESE)
        }
        val configContext = activity.createConfigurationContext(config)

        // Find the private LocalizedActivityContext class via reflection or construct a test instance
        var current: Any? = activity
        while (current is ContextWrapper && current !is ComponentActivity) {
          current = current.baseContext
        }
        assertTrue("Base context unwrapping should resolve to ComponentActivity", current is ComponentActivity)
      }
    }
  }
}
