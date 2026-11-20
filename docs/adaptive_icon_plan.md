# Dex2C True Adaptive Icon Architecture & Implementation Plan

## Goal Description
Upgrade **all four** icon variants (`Cyber Shodo`, `Cursive Neon`, `Monogram Ligature`, and `Classic Tech`) into **true Android Adaptive Icons** compliant with Google's Android Adaptive Icon specification (API 26+ and API 33+ Material You).

### The Adaptive Icon Problem & Solution
Currently, the icon assets contain artificial squircle or circle borders baked directly into the foreground image. When OEM launchers (Google Pixel, Samsung One UI, Xiaomi HyperOS, OnePlus OxygenOS, etc.) apply their own mask (circle, squircle, teardrop, or rounded rectangle), this causes an unsightly "double border / badge inside a badge" artifact and breaks launcher 3D parallax effects.

This plan removes all artificial outer borders, extracts pure transparent foregrounds for the calligraphic emblems, centers them strictly within Android's **72dp safe keyline zone**, provides tailored Material You vector silhouettes for each style, and regenerates the full multi-density mipmap matrix.

---

### Adaptive Showcase (Pure Foreground Floating Over Continuous Background)

| Icon Variant | Adaptive Foreground Content | Background Canvas | Safe Zone Diameter | Material You Monochrome |
| :--- | :--- | :--- | :--- | :--- |
| **Cyber Shodo (Default)** | Isolated dry-brush `D2C` + cyan/mint neon splatter on 100% alpha transparency | Full 108dp `#061323` Obsidian Void | 72dp (inner circle) | Tailored Shodo brush vector silhouette |
| **Cursive Neon** | Isolated fluid cursive `dex2c` with cyan-to-mint luminous glow on 100% alpha transparency | Full 108dp `#061323` Obsidian Void | 70dp (centered) | Tailored cursive script vector silhouette |
| **Monogram Ligature** | Isolated calligraphic `D2C` ligature with metallic chisel sheen on 100% alpha transparency | Full 108dp `#061323` Obsidian Void | 68dp (centered) | Tailored monogram ligature vector silhouette |
| **Classic Tech** | Isolated hexagonal bytecode blocks & `{}` code traces on 100% alpha transparency | Full 108dp `#061323` Obsidian Void | 70dp (centered) | Tailored circuit & bytecode vector silhouette |

---

## User Review Required

> [!IMPORTANT]
> **Adaptive Parallax & Safe Zone Standard**:
> In Android's adaptive icon standard:
> 1. The total canvas is **108dp × 108dp**.
> 2. The visible masked area on any launcher (Pixel circular, Samsung squircle, etc.) is the inner **72dp** circle.
> 3. The outer **18dp on all sides** is reserved for launcher physics (scale-down on touch, drag, and parallax motion).
> 
> By isolating the calligraphic lettering and removing hard squircle/circular borders from the foreground, the emblem will seamlessly adapt to any phone's launcher mask with zero edge clipping.

> [!NOTE]
> **Dedicated Monochrome Vector Silhouette per Style**:
> Currently, all 4 icons share a single generic monochrome vector. Under this plan, each icon will receive a dedicated matching monochrome vector in `app/src/main/res/drawable/` so Android 13+ Material You themed icons match the exact calligraphy style selected by the user.

---

## Proposed Changes

### Component 1: Foreground Drawables & Asset Generation
- `app/src/main/res/drawable/ic_dex2c_icon_src.png` (Clean alpha-isolated Shodo master)
- `app/src/main/res/drawable/ic_dex2c_shodo_src.png`
- `app/src/main/res/drawable/ic_dex2c_cursive_src.png`
- `app/src/main/res/drawable/ic_dex2c_monogram_src.png`
- `app/src/main/res/drawable/ic_dex2c_classic_src.png`
- Update `ic_launcher_foreground_*.xml` layer-lists centered strictly within 68-72dp.

### Component 2: Material You Dynamic Theming (Monochrome Vectors)
- `app/src/main/res/drawable/ic_launcher_monochrome_shodo.xml`
- `app/src/main/res/drawable/ic_launcher_monochrome_cursive.xml`
- `app/src/main/res/drawable/ic_launcher_monochrome_monogram.xml`
- `app/src/main/res/drawable/ic_launcher_monochrome_classic.xml`

### Component 3: Adaptive Icon XML Descriptors in `mipmap-anydpi-v26/`
- Wire respective foregrounds, backgrounds, and style-specific monochrome vectors.

### Component 4: Multi-Density Mipmap Suite (40 PNG files)
- Regenerate all densities (48px, 72px, 96px, 144px, 192px) for both standard and `_round` variants.

---

## Verification Plan
1. **Automated Asset Matrix Validation**: Verify 0% alpha outside strokes, exact density dimensions, and XML descriptor linkages.
2. **Gradle Build Verification**: Run `./gradlew assembleDebug` to ensure AAPT2 passes without resource merging errors.
3. **Gradle Unit Tests**: Run `./gradlew testDebugUnitTest --no-daemon` to ensure zero regressions.
