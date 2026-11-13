package com.example.cloud

import com.example.transpiler.JniBindingStyle
import com.example.transpiler.TranspilerConfig

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
      |      - name: Clone Kirlif/d2c & Patch dcc.cfg
      |        run: |
      |          git clone --depth 1 https://github.com/Kirlif/d2c.git /tmp/d2c
      |          if [ -f /tmp/d2c/requirements.txt ]; then
      |            pip install -r /tmp/d2c/requirements.txt
      |          fi
      |          python3 - <<'PY'
      |          import json, os
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
      |          python3 /tmp/d2c/dcc.py "${'$'}{ARGS[@]}" 2>&1 | tee /tmp/work/dex2c_build.log
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
