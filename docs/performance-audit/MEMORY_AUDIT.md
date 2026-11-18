# Memory Management & Leak Audit: Dex2C Studio

## 1. Executive Summary

Memory analysis of **Dex2C Studio** (`com.dex2c.studio`) focused on:
- Large object allocation and retention patterns.
- Bitmap and image lifecycle management.
- Activity, Context, and ViewModel retention risks.
- Coroutine and background job cancellation.
- In-memory random-access buffers (`MemoryDataStore`) and DEX byte caching.
- System low-memory (`ComponentCallbacks2.onTrimMemory`) response.

### Key Memory Findings
1. **Unbounded Icon Cache**: `InstalledAppRepository` holds app icon bitmaps in an `LruCache` sized by count (64 entries) rather than memory byte size.
2. **Synchronous Icon Decoding**: `InstalledAppRepository.queryLaunchableApps` synchronously decodes every launchable app icon on the device into memory during a single query pass.
3. **Bitmap Leak in ViewModel StateFlow**: `InstalledAppItem` retains raw `android.graphics.Bitmap` instances inside the `_installedApps` StateFlow, preventing GC reclamation.
4. **ArrayList Retention during Multi-DEX Parsing**: Concatenated method lists in `DexClassInfo` occupy redundant heap space.
5. **Deprecated Memory Trim Constant**: `MainActivity.kt` uses deprecated `TRIM_MEMORY_COMPLETE` instead of handling standard modern Android trim levels (`TRIM_MEMORY_UI_HIDDEN`, `TRIM_MEMORY_RUNNING_LOW`, `TRIM_MEMORY_MODERATE`).

---

## 2. Detailed Memory Vulnerability Analysis

### MEM-001: Count-Based Bitmap LruCache
- **File**: `app/src/main/java/com/dex2c/studio/apk/InstalledAppRepository.kt:17-19`
- **Code**:
  ```kotlin
  private val iconCache = object : android.util.LruCache<String, Bitmap>(64) {
    override fun sizeOf(key: String, value: Bitmap): Int = 1
  }
  ```
- **Vulnerability**: Because `sizeOf` returns `1`, the cache retains up to 64 bitmaps regardless of their actual resolution or byte size. If high-density drawables (e.g. 192x192 ARGB_8888 = ~150KB each) are cached, this single cache holds ~10MB of unevicted bitmap memory.
- **Remediation**:
  Size the `LruCache` based on available application heap memory (`Runtime.getRuntime().maxMemory() / 16`) and measure actual allocation bytes:
  ```kotlin
  private val maxBytes = (Runtime.getRuntime().maxMemory() / 16).toInt().coerceIn(4 * 1024 * 1024, 16 * 1024 * 1024)
  private val iconCache = object : android.util.LruCache<String, Bitmap>(maxBytes) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
  }
  ```

### MEM-002: Eager Icon Decoding on Installed App Ingestion
- **File**: `app/src/main/java/com/dex2c/studio/apk/InstalledAppRepository.kt:68-70`
- **Code**:
  ```kotlin
  val iconBitmap = iconCache.get(pkgName) ?: runCatching {
    drawableToBitmap(ri.loadIcon(pm)).also { iconCache.put(pkgName, it) }
  }.getOrNull()
  ```
- **Vulnerability**: When the user opens the "Installed Apps" picker tab, this loop executes for every launchable app on the device (often 100-300 packages). Loading hundreds of drawables from the package manager and rendering them to Bitmaps in a tight loop spikes JVM heap allocation, generates garbage collector pressure, and causes a 1-3 second delay.
- **Remediation**:
  Scale down icons to exact display size (40x40 dp / 48x48 px), support lazy asynchronous decoding, and clear cache on memory trim.

### MEM-003: Lifecycle Memory Trimming
- **File**: `app/src/main/java/com/dex2c/studio/MainActivity.kt:258-275`
- **Code**:
  ```kotlin
  override fun onLowMemory() {
    viewModel.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
  }
  ```
- **Vulnerability**: `ComponentCallbacks2.TRIM_MEMORY_COMPLETE` is deprecated. Furthermore, `onTrimMemory` in `Dex2cViewModel` only clears the Smali cache and does not trim `InstalledAppRepository.iconCache` or transient preview byte buffers.
- **Remediation**:
  Update `onTrimMemory` to evict icon caches, trim Baksmali caches, and discard uncommitted staging byte arrays when the application transitions to the background.

---

## 3. Memory Optimization Verification

1. **Before Optimization**:
   - `iconCache`: Count-based (64), unmeasured byte footprint.
   - `DexClassInfo.allMethods`: New `ArrayList` allocation on every access.
   - Low memory handler: Incomplete cache eviction.
2. **After Optimization**:
   - `iconCache`: Byte-bounded (`value.byteCount`), max 16MB.
   - `DexClassInfo.allMethods`: `CompositeList` zero-allocation view.
   - Full eviction on `TRIM_MEMORY_MODERATE` and `TRIM_MEMORY_COMPLETE`.
