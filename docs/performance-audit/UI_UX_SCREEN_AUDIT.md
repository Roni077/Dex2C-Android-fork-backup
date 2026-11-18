# Screen-by-Screen UI/UX Quality & Accessibility Audit: Dex2C Studio

## 1. Executive Summary

This report delivers a screen-by-screen audit of all 8 screens, sheets, dialogs, and navigation destinations in **Dex2C Studio** (`com.dex2c.studio`).

---

## 2. Screen-by-Screen Quality Matrix

| Screen / Component | Route / Location | Touch Targets (>=48dp) | Dark / Light / AMOLED | Two-Pane / Adaptive | Status & Issues |
|---|---|---|---|---|---|
| **HomeScreen** | `ROUTE_HOME` | Pass | Pass | Pass (Wide Row layout) | Verified. Metrics grid, hero cards, transpile action banner. |
| **ClassesScreen** | `ROUTE_CLASSES` | Pass | Pass | Pass (Two-Pane split) | **Issue UI-003**: Uses deprecated `Icons.Rounded.OpenInNew`. Package tree memoization needs optimization. |
| **PipelineConfigScreen** | `ROUTE_BUILD` | Pass | Pass | Pass (Segmented tabs) | Verified. Loader mode, NDK compiler passes, filter rules builder. |
| **CloudBuildScreen** | `ROUTE_CLOUD` | Pass | Pass | Pass (Setup/Dispatch/Monitor) | Verified. GitHub Device Flow card, run monitor, artifact list. |
| **MethodDetailScreen** | `ROUTE_METHOD_DETAIL` | Pass | Pass | Pass | Verified. C++ JNI preview, Java AST, Dalvik CFG visualization. |
| **SmaliClassScreen** | `ROUTE_SMALI_CLASS` | Pass | Pass | Pass | **Issue UI-001**: `TopAppBar` actions block is empty. `onCopySmali` and `onExportSmali` are disconnected. **Issue UI-002**: `dexEditingEnabled` ignored. |
| **SettingsScreen** | `ROUTE_SETTINGS` | Pass | Pass | Pass (Two-Pane detail) | Verified. 8 sections with sub-screen navigation, color pickers, and diagnostics. |
| **OnboardingScreen** | `ROUTE_ONBOARDING` | Pass | Pass | Pass (Pager) | Verified. 3-step onboarding walkthrough with code preview cards. |
| **ApkPickerSheet** | Modal Bottom Sheet | Pass | Pass | Pass (Adaptive tabs) | Storage, installed apps, and recent items tabs. Icon loading needs memory optimization. |
| **ApkPreviewSheet** | Modal Bottom Sheet | Pass | Pass | Pass | Multi-DEX selection checkboxes, package details, certificate badges. |

---

## 3. Detailed UI/UX Findings & Corrections

### UI-001: Stranded Copy and Export Actions in `SmaliClassScreen`
- **Location**: `app/src/main/java/com/dex2c/studio/ui/screens/SmaliClassScreen.kt:102`
- **Code**:
  ```kotlin
  TopAppBar(
    ...
    actions = {},
  )
  ```
- **Defect**:
  `SmaliClassScreen` declares callbacks:
  `onCopySmali: ((String) -> Unit)? = null`
  `onExportSmali: ((fileName: String, smaliText: String) -> Unit)? = null`
  However, `actions = {}` is empty! As a result, the user has no button to copy or export the Smali code when inspecting a class.
- **Fix**:
  Add `IconButton` for Copy Smali and `IconButton` for Export Smali into `TopAppBar(actions = { ... })` whenever `readyState != null`.

### UI-002: Hardcoded Read-Only in `SmaliClassScreen`
- **Location**: `app/src/main/java/com/dex2c/studio/ui/screens/SmaliClassScreen.kt:60,184`
- **Defect**:
  `dexEditingEnabled: Boolean = false` is accepted as a parameter, but `SmaliSoraEditor` is called with hardcoded `isReadOnly = true`.
- **Fix**:
  Pass `isReadOnly = !dexEditingEnabled` so users can edit the Smali bytecode if DEX editing is enabled in build configuration.

### UI-003: Deprecated Icon Reference
- **Location**: `app/src/main/java/com/dex2c/studio/ui/screens/ClassesScreen.kt:1154`
- **Defect**:
  `Icons.Rounded.OpenInNew` generates compiler deprecation warning.
- **Fix**:
  Update to `Icons.AutoMirrored.Rounded.OpenInNew`.
