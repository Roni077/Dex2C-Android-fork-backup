package com.dex2c.studio.cloud

import com.dex2c.studio.transpiler.JniBindingStyle
import com.dex2c.studio.transpiler.TranspilerConfig

object Dex2cWorkflowGenerator {

  const val WORKFLOW_PATH = ".github/workflows/dex2c.yml"
  const val DCC_CFG_PATH = "dcc.cfg"
  const val FILTER_TXT_PATH = "filter.txt"
  const val DEFAULT_APK_REPO_PATH = "input/target.apk"

  fun generateDex2cYaml(
    settings: CloudSettings,
    transpilerConfig: TranspilerConfig,
  ): String {
    val ndkVersion = settings.ndkVersion.ifBlank { "r25b" }
    val jdkVersion = settings.jdkVersion.ifBlank { "17" }
    val defaultLoader = transpilerConfig.customLoaderClass.ifBlank { "amimo.dcc.DccApplication" }
    val defaultObfuscate = transpilerConfig.stringEncryption || transpilerConfig.controlFlowFlattening
    val defaultDynamicReg = transpilerConfig.jniStyle == JniBindingStyle.REGISTER_NATIVES

    return """
      |name: Dex2C Cloud Build
      |
      |on:
      |  workflow_dispatch:
      |    inputs:
      |      apk_path:
      |        description: 'Repository path to input APK'
      |        required: false
      |        default: '$DEFAULT_APK_REPO_PATH'
      |      apk_url:
      |        description: 'Optional direct URL fallback for large APKs'
      |        required: false
      |        default: ''
      |      allow_init:
      |        description: 'Transpile <init> constructors (-a / --allow-init)'
      |        required: false
      |        default: '${transpilerConfig.allowInit}'
      |      skip_synthetic:
      |        description: 'Skip synthetic bridge, lambda, and coroutine methods (--skip-synthetic)'
      |        required: false
      |        default: '${transpilerConfig.skipSynthetic}'
      |      custom_loader:
      |        description: 'Application loader class (-c / --custom-loader)'
      |        required: false
      |        default: '$defaultLoader'
      |      force_custom_loader:
      |        description: 'Force replace existing Application class with custom loader (-r)'
      |        required: false
      |        default: '${transpilerConfig.forceCustomLoader}'
      |      obfuscate:
      |        description: 'Enable Kirlif/d2c obfuscation passes (-p)'
      |        required: false
      |        default: '$defaultObfuscate'
      |      dynamic_register:
      |        description: 'Use RegisterNatives JNI_OnLoad binding (-d)'
      |        required: false
      |        default: '$defaultDynamicReg'
      |      signing_mode:
      |        description: 'APK signing mode (DEBUG_AUTO or REPO_KEYSTORE_SECRETS)'
      |        required: false
      |        default: '${settings.signingMode.name}'
      |
      |jobs:
      |  dex2c-apk:
      |    runs-on: ubuntu-latest
      |    steps:
      |      - name: Checkout Private Workspace
      |        uses: actions/checkout@v6
      |
      |      - name: Set up Python 3
      |        uses: actions/setup-python@v6
      |        with:
      |          python-version: '3.11'
      |
      |      - name: Set up JDK $jdkVersion
      |        uses: actions/setup-java@v5
      |        with:
      |          distribution: 'temurin'
      |          java-version: '$jdkVersion'
      |
      |      - name: Install Python Dependencies & NDK $ndkVersion
      |        run: |
      |          python3 -m pip install --upgrade pip
      |          pip install "lxml>=4.3.0"
      |          wget -q "https://dl.google.com/android/repository/android-ndk-${ndkVersion}-linux.zip" -O /tmp/ndk.zip
      |          unzip -q /tmp/ndk.zip -d /opt/
      |          NDK_DIR=${'$'}(find /opt -maxdepth 1 -type d -name "android-ndk-*" | head -n 1)
      |          echo "ANDROID_NDK_HOME=${'$'}NDK_DIR" >> "${'$'}GITHUB_ENV"
      |
      |      - name: Clone Kirlif/d2c & Patch dcc.cfg + C++ Sanitizer
      |        run: |
      |          git clone --depth 1 https://github.com/Kirlif/d2c.git /tmp/d2c
      |          if [ -f /tmp/d2c/requirements.txt ]; then
      |            pip install -r /tmp/d2c/requirements.txt
      |          fi
      |          python3 - <<'PY'
      |          import json, os, re
      |          base_cfg = {}
      |          upstream_cfg_path = "/tmp/d2c/dcc.cfg"
      |          if os.path.exists(upstream_cfg_path):
      |              with open(upstream_cfg_path, "r", encoding="utf-8") as f:
      |                  base_cfg = json.load(f)
      |          with open("dcc.cfg", "r", encoding="utf-8") as f:
      |              workspace_cfg = json.load(f)
      |          for k, v in workspace_cfg.items():
      |              if k != "ndk_dir":
      |                  base_cfg[k] = v
      |          base_cfg["ndk_dir"] = os.environ["ANDROID_NDK_HOME"]
      |          base_cfg["filter"] = "/tmp/d2c/filter.txt"
      |          with open(upstream_cfg_path, "w", encoding="utf-8") as f:
      |              json.dump(base_cfg, f, indent=2)
      |
      |          # Patch template Android.mk to permit D8 register reuse casts in Clang++
      |          android_mk = "/tmp/d2c/project/jni/Android.mk"
      |          if os.path.exists(android_mk):
      |              with open(android_mk, "r", encoding="utf-8") as f:
      |                  mk_src = f.read()
      |              if "-fpermissive" not in mk_src:
      |                  mk_src += "\nLOCAL_CPPFLAGS += -fpermissive -Wno-int-conversion -Wno-error\nLOCAL_CFLAGS += -Wno-int-conversion -Wno-error\n"
      |                  with open(android_mk, "w", encoding="utf-8") as f:
      |                      f.write(mk_src)
      |
      |          # Inject pre-ndk-build C++ register-type sanitizer into /tmp/d2c/dcc.py
      |          import textwrap
      |          dcc_py = "/tmp/d2c/dcc.py"
      |          if os.path.exists(dcc_py):
      |              with open(dcc_py, "r", encoding="utf-8") as f:
      |                  dcc_src = f.read()
      |              sanitizer_fn = textwrap.dedent('''
      |                  def _dex2c_sanitize_generated_cpp(project_dir):
      |                      import glob, os, re
      |                      nc_dir = os.path.join(project_dir, "jni", "nc")
      |                      if not os.path.isdir(nc_dir):
      |                          return
      |                      decl_re = re.compile(r"\\b(jobject|jstring|jclass|jthrowable|jarray|\\w+Array)\\s+(v\\d+)\\b")
      |                      assign_re = re.compile(r"^(\\s*)(v\\d+)\\s*=\\s*(-?(?:0x[0-9a-fA-F]+|\\d+))[uUlL]*\\s*;(\\s*)$", re.M)
      |                      for cpp_path in glob.glob(os.path.join(nc_dir, "*.cpp")):
      |                          try:
      |                              with open(cpp_path, "r", encoding="utf-8", errors="ignore") as cf:
      |                                  code = cf.read()
      |                              obj_regs = {m.group(2): m.group(1) for m in decl_re.finditer(code)}
      |                              if not obj_regs:
      |                                  continue
      |                              def _fix_assign(m):
      |                                  indent, reg, lit, tail = m.group(1), m.group(2), m.group(3), m.group(4)
      |                                  if reg in obj_regs and lit not in ("0", "0x0"):
      |                                      jtype = obj_regs[reg]
      |                                      return f"{indent}{reg} = reinterpret_cast<{jtype}>(static_cast<intptr_t>({lit}));{tail}"
      |                                  return m.group(0)
      |                              patched = assign_re.sub(_fix_assign, code)
      |                              if patched != code:
      |                                  with open(cpp_path, "w", encoding="utf-8") as cf:
      |                                      cf.write(patched)
      |                          except Exception as exc:
      |                              print("Warning: sanitizer skipped", cpp_path, exc)
      |              ''').strip()
      |              if "subprocess.check_call([NDKBUILD" in dcc_src and "_dex2c_sanitize_generated_cpp" not in dcc_src:
      |                  dcc_src = sanitizer_fn + "\n\n" + re.sub(
      |                      r"^(\s*)(subprocess\.check_call\(\[NDKBUILD)",
      |                      r"\1_dex2c_sanitize_generated_cpp(project_dir)\n\1\2",
      |                      dcc_src,
      |                      flags=re.M,
      |                  )
      |                  with open(dcc_py, "w", encoding="utf-8") as f:
      |                      f.write(dcc_src)
      |          PY
      |          cp filter.txt /tmp/d2c/filter.txt
      |
      |      - name: Prepare Target APK
      |        run: |
      |          mkdir -p /tmp/work
      |          if [ -n "${'$'}{{ github.event.inputs.apk_url }}" ]; then
      |            echo "Downloading APK from fallback URL..."
      |            curl -fsSL "${'$'}{{ github.event.inputs.apk_url }}" -o /tmp/work/input.apk
      |          else
      |            cp "${'$'}{{ github.event.inputs.apk_path }}" /tmp/work/input.apk
      |          fi
      |          ls -lh /tmp/work/input.apk
      |
      |      - name: Run Kirlif/d2c Compilation
      |        working-directory: /tmp/d2c
      |        run: |
      |          set -o pipefail
      |          ARGS=(
      |            -i /tmp/work/input.apk
      |            -o /tmp/work/unsigned_dex2c.apk
      |            -f /tmp/d2c/filter.txt
      |          )
      |          if [ "${'$'}{{ github.event.inputs.allow_init }}" = "true" ]; then
      |            ARGS+=(-a)
      |          fi
      |          if [ "${'$'}{{ github.event.inputs.skip_synthetic }}" != "false" ]; then
      |            ARGS+=(--skip-synthetic)
      |          fi
      |          if [ "${'$'}{{ github.event.inputs.obfuscate }}" = "true" ]; then
      |            ARGS+=(-p)
      |          fi
      |          if [ "${'$'}{{ github.event.inputs.dynamic_register }}" = "true" ]; then
      |            ARGS+=(-d)
      |          fi
      |          if [ -n "${'$'}{{ github.event.inputs.custom_loader }}" ]; then
      |            ARGS+=(-c "${'$'}{{ github.event.inputs.custom_loader }}")
      |          fi
      |          if [ "${'$'}{{ github.event.inputs.force_custom_loader }}" = "true" ]; then
      |            ARGS+=(-r)
      |          fi
      |          MAX_ATTEMPTS=3
      |          for ATTEMPT in ${'$'}(seq 1 ${'$'}MAX_ATTEMPTS); do
      |            echo "=== Kirlif/d2c compilation attempt ${'$'}ATTEMPT/${'$'}MAX_ATTEMPTS ==="
      |            rm -f /tmp/work/unsigned_dex2c.apk
      |            if python3 /tmp/d2c/dcc.py "${'$'}{ARGS[@]}" 2>&1 | tee /tmp/work/dex2c_build.log; then
      |              break
      |            fi
      |            if [ "${'$'}ATTEMPT" -lt "${'$'}MAX_ATTEMPTS" ]; then
      |              python3 - <<'PY' || break
      |          import re, sys
      |          with open("/tmp/work/dex2c_build.log", "r", encoding="utf-8", errors="ignore") as f:
      |              log_text = f.read()
      |          failed_cpp = sorted(set(re.findall(r"jni/nc/([A-Za-z0-9_]+)\.cpp:\d+:\d+:\s*error:", log_text)))
      |          if not failed_cpp:
      |              sys.exit(1)
      |          def jni_unescape(s):
      |              s = re.sub(r"_0([0-9a-fA-F]{4})", lambda m: chr(int(m.group(1), 16)), s)
      |              return s.replace("_1", "_")
      |          new_excludes = []
      |          for sym in failed_cpp:
      |              body = sym[5:] if sym.startswith("Java_") else sym
      |              if "__" in body:
      |                  body = body.split("__", 1)[0]
      |              parts = body.split("_")
      |              if len(parts) >= 2:
      |                  method_part = jni_unescape(parts[-1])
      |                  cls_part = "/".join(jni_unescape(p) for p in parts[:-1])
      |                  new_excludes.append(f"!^L{re.escape(cls_part)};{re.escape(method_part)}.*")
      |                  new_excludes.append(f"!.*;{re.escape(method_part)}\\(.*")
      |          if not new_excludes:
      |              sys.exit(1)
      |          with open("/tmp/d2c/filter.txt", "r", encoding="utf-8") as f:
      |              existing = f.read()
      |          prepended = "# Auto-excluded failing C++ method units\n" + "\n".join(dict.fromkeys(new_excludes)) + "\n" + existing
      |          with open("/tmp/d2c/filter.txt", "w", encoding="utf-8") as f:
      |              f.write(prepended)
      |          print("Auto-excluded failing method units from filter.txt:", new_excludes)
      |          PY
      |            fi
      |          done
      |          if [ ! -f /tmp/work/unsigned_dex2c.apk ]; then
      |            echo "::error::dcc.py finished without producing /tmp/work/unsigned_dex2c.apk"
      |            exit 1
      |          fi
      |
      |      - name: Align & Auto-Sign Output APK
      |        env:
      |          KEYSTORE_B64: ${'$'}{{ secrets.KEYSTORE_BASE64 }}
      |          KEYSTORE_PASS: ${'$'}{{ secrets.KEYSTORE_PASS }}
      |          KEY_ALIAS: ${'$'}{{ secrets.KEY_ALIAS }}
      |          KEY_PASS: ${'$'}{{ secrets.KEY_PASS }}
      |        run: |
      |          BUILD_TOOLS=${'$'}(find "${'$'}ANDROID_HOME/build-tools" -maxdepth 1 -mindepth 1 | sort -V | tail -n 1)
      |          "${'$'}BUILD_TOOLS/zipalign" -p -f 4 /tmp/work/unsigned_dex2c.apk /tmp/work/aligned_dex2c.apk
      |          if [ "${'$'}{{ github.event.inputs.signing_mode }}" = "REPO_KEYSTORE_SECRETS" ] && [ -n "${'$'}KEYSTORE_B64" ]; then
      |            echo "${'$'}KEYSTORE_B64" | base64 --decode > /tmp/work/release.jks
      |            "${'$'}BUILD_TOOLS/apksigner" sign \
      |              --ks /tmp/work/release.jks \
      |              --ks-pass "env:KEYSTORE_PASS" \
      |              --ks-key-alias "${'$'}KEY_ALIAS" \
      |              --key-pass "env:KEY_PASS" \
      |              --out /tmp/work/dex2c-signed-output.apk \
      |              /tmp/work/aligned_dex2c.apk
      |          else
      |            keytool -genkeypair -v \
      |              -keystore /tmp/work/debug.keystore \
      |              -storepass android -alias androiddebugkey -keypass android \
      |              -keyalg RSA -keysize 2048 -validity 10000 \
      |              -dname "CN=Dex2C Cloud,O=Dex2C Studio,C=US"
      |            "${'$'}BUILD_TOOLS/apksigner" sign \
      |              --ks /tmp/work/debug.keystore \
      |              --ks-pass pass:android \
      |              --ks-key-alias androiddebugkey \
      |              --key-pass pass:android \
      |              --out /tmp/work/dex2c-signed-output.apk \
      |              /tmp/work/aligned_dex2c.apk
      |          fi
      |
      |      - name: Upload Signed Dex2C APK Artifact
      |        if: success()
      |        uses: actions/upload-artifact@v4
      |        with:
      |          name: dex2c-signed-apk
      |          path: |
      |            /tmp/work/dex2c-signed-output.apk
      |            /tmp/work/dex2c_build.log
      |          retention-days: 14
      |
      |      - name: Upload Failure Build Log
      |        if: failure()
      |        uses: actions/upload-artifact@v4
      |        with:
      |          name: dex2c-build-log
      |          path: /tmp/work/dex2c_build.log
      |          if-no-files-found: ignore
      |          retention-days: 14
      """.trimMargin() + "\n"
  }
}
