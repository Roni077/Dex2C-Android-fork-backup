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
    val ndkVersion = settings.ndkVersion.ifBlank { "r25c-ollvm" }
    val jdkVersion = settings.jdkVersion.ifBlank { "17" }
    val defaultLoader = transpilerConfig.customLoaderClass.ifBlank { "amimo.dcc.DccApplication" }
    val defaultObfuscate = transpilerConfig.stringEncryption || transpilerConfig.controlFlowFlattening
    val defaultDynamicReg = transpilerConfig.jniStyle == JniBindingStyle.REGISTER_NATIVES
    val defaultEnableOllvm = transpilerConfig.ollvm.enabled
    val defaultOllvmFlags = transpilerConfig.ollvm.buildClangFlags()
    val defaultAlign16Kb = transpilerConfig.ollvm.align16KbPages

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
      |      app_name:
      |        description: 'Base name for output APK and artifacts'
      |        required: false
      |        default: 'app'
      |      apk_url:
      |        description: 'Optional direct URL fallback for large APKs'
      |        required: false
      |        default: ''
      |      ndk_version:
      |        description: 'Android NDK version (e.g. r25c-ollvm, r25b, r26d, r27c, r28c)'
      |        required: false
      |        default: '$ndkVersion'
      |      allow_init:
      |        description: 'Transpile <init> constructors'
      |        required: false
      |        default: '${transpilerConfig.allowInit}'
      |      skip_synthetic:
      |        description: 'Skip synthetic bridge, lambda, and coroutine methods (--skip-synthetic)'
      |        required: false
      |        default: '${transpilerConfig.skipSynthetic}'
      |      custom_loader:
      |        description: 'Application loader class (--custom-loader)'
      |        required: false
      |        default: '$defaultLoader'
      |      force_custom_loader:
      |        description: 'Force replace existing Application class with custom loader'
      |        required: false
      |        default: '${transpilerConfig.forceCustomLoader}'
      |      obfuscate:
      |        description: 'Enable codehasan/dex2c string obfuscation passes (-p)'
      |        required: false
      |        default: '$defaultObfuscate'
      |      dynamic_register:
      |        description: 'Use RegisterNatives JNI_OnLoad binding (-d)'
      |        required: false
      |        default: '$defaultDynamicReg'
      |      enable_ollvm:
      |        description: 'Enable native Obfuscator-LLVM compiler passes'
      |        required: false
      |        default: '$defaultEnableOllvm'
      |      ollvm_flags:
      |        description: 'Compiler flags for Obfuscator-LLVM (LOCAL_CPPFLAGS)'
      |        required: false
      |        default: '$defaultOllvmFlags'
      |      align_16kb:
      |        description: 'Android 15 16 KB ELF Page Alignment (-Wl,-z,max-page-size=16384)'
      |        required: false
      |        default: '$defaultAlign16Kb'
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
      |      - name: Set up QEMU for AArch64 Emulation
      |        uses: docker/setup-qemu-action@v3
      |        with:
      |          platforms: arm64
      |
      |      - name: Install Python Dependencies & NDK
      |        run: |
      |          python3 -m pip install --upgrade pip
      |          pip install "lxml>=4.3.0"
      |          BUILD_TOOLS=${'$'}(find "${'$'}ANDROID_HOME/build-tools" -maxdepth 1 -mindepth 1 | sort -V | tail -n 1)
      |          if [ -n "${'$'}BUILD_TOOLS" ]; then
      |            echo "${'$'}BUILD_TOOLS" >> "${'$'}GITHUB_PATH"
      |            sudo ln -sf "${'$'}BUILD_TOOLS/zipalign" /usr/local/bin/zipalign || true
      |            sudo ln -sf "${'$'}BUILD_TOOLS/apksigner" /usr/local/bin/apksigner || true
      |          fi
      |          NDK_SEL="${'$'}{{ github.event.inputs.ndk_version || '$ndkVersion' }}"
      |          if [ "${'$'}NDK_SEL" = "r25c-ollvm" ]; then
      |            echo "Downloading codehasan/dex2c r25c-ollvm NDK release..."
      |            wget -q "https://github.com/codehasan/dex2c/releases/download/ollvm-termux/android-ndk-r25c-ollvm-aarch64.tar.xz" -O /tmp/ndk.tar.xz
      |            mkdir -p /opt/android-ndk-r25c-ollvm
      |            tar -xf /tmp/ndk.tar.xz -C /opt/android-ndk-r25c-ollvm --strip-components=1
      |            rm -f /tmp/ndk.tar.xz
      |            NDK_DIR="/opt/android-ndk-r25c-ollvm"
      |            PY3_DIR=${'$'}(find "${'$'}NDK_DIR/toolchains/llvm/prebuilt" -maxdepth 2 -type d -name "python3" 2>/dev/null | head -n 1)
      |            if [ -n "${'$'}PY3_DIR" ]; then
      |              mkdir -p "${'$'}PY3_DIR/bin"
      |              ln -sf ${'$'}(which python3) "${'$'}PY3_DIR/bin/python3" || true
      |            fi
      |            CLANG_PROBE=${'$'}(find "${'$'}NDK_DIR" -type f -name "clang" 2>/dev/null | head -n 1)
      |            if [ -z "${'$'}CLANG_PROBE" ] || ! "${'$'}CLANG_PROBE" --version >/dev/null 2>&1; then
      |              echo "::warning::codehasan/dex2c r25c-ollvm requires Android Bionic dynamic linker (/system/bin/linker64) unavailable on standard Linux hosts. Falling back to official Google Android NDK r25c (linux-x86_64)..."
      |              rm -rf /opt/android-ndk-r25c-ollvm
      |              wget -q "https://dl.google.com/android/repository/android-ndk-r25c-linux.zip" -O /tmp/ndk.zip
      |              unzip -q /tmp/ndk.zip -d /opt/
      |              rm -f /tmp/ndk.zip
      |              NDK_DIR=${'$'}(find /opt -maxdepth 1 -type d -name "android-ndk-*" | head -n 1)
      |            fi
      |          elif [ "${'$'}NDK_SEL" = "r28c" ]; then
      |            echo "Downloading codehasan/dex2c r28c NDK release..."
      |            wget -q "https://github.com/codehasan/dex2c/releases/download/ollvm-termux/android-ndk-r28c.tar.xz" -O /tmp/ndk.tar.xz
      |            mkdir -p /opt/android-ndk-r28c
      |            tar -xf /tmp/ndk.tar.xz -C /opt/android-ndk-r28c --strip-components=1
      |            rm -f /tmp/ndk.tar.xz
      |            NDK_DIR="/opt/android-ndk-r28c"
      |            PY3_DIR=${'$'}(find "${'$'}NDK_DIR/toolchains/llvm/prebuilt" -maxdepth 2 -type d -name "python3" 2>/dev/null | head -n 1)
      |            if [ -n "${'$'}PY3_DIR" ]; then
      |              mkdir -p "${'$'}PY3_DIR/bin"
      |              ln -sf ${'$'}(which python3) "${'$'}PY3_DIR/bin/python3" || true
      |            fi
      |            CLANG_PROBE=${'$'}(find "${'$'}NDK_DIR" -type f -name "clang" 2>/dev/null | head -n 1)
      |            if [ -z "${'$'}CLANG_PROBE" ] || ! "${'$'}CLANG_PROBE" --version >/dev/null 2>&1; then
      |              echo "::warning::codehasan/dex2c r28c requires Android Bionic dynamic linker (/system/bin/linker64) unavailable on standard Linux hosts. Falling back to official Google Android NDK r26d (linux-x86_64)..."
      |              rm -rf /opt/android-ndk-r28c
      |              wget -q "https://dl.google.com/android/repository/android-ndk-r26d-linux.zip" -O /tmp/ndk.zip
      |              unzip -q /tmp/ndk.zip -d /opt/
      |              rm -f /tmp/ndk.zip
      |              NDK_DIR=${'$'}(find /opt -maxdepth 1 -type d -name "android-ndk-*" | head -n 1)
      |            fi
      |          else
      |            echo "Downloading Google Android NDK $ndkVersion (${'$'}NDK_SEL)..."
      |            GOOGLE_VER="${'$'}{NDK_SEL%%-*}"
      |            wget -q "https://dl.google.com/android/repository/android-ndk-${'$'}{NDK_SEL}-linux.zip" -O /tmp/ndk.zip || \
      |            wget -q "https://dl.google.com/android/repository/android-ndk-${'$'}{GOOGLE_VER}-linux.zip" -O /tmp/ndk.zip || \
      |            wget -q "https://dl.google.com/android/repository/android-ndk-${ndkVersion}-linux.zip" -O /tmp/ndk.zip || \
      |            wget -q "https://dl.google.com/android/repository/android-ndk-r26d-linux.zip" -O /tmp/ndk.zip
      |            unzip -q /tmp/ndk.zip -d /opt/
      |            rm -f /tmp/ndk.zip
      |            NDK_DIR=${'$'}(find /opt -maxdepth 1 -type d -name "android-ndk-*" | head -n 1)
      |          fi
      |          echo "ANDROID_NDK_HOME=${'$'}NDK_DIR" >> "${'$'}GITHUB_ENV"
      |
      |      - name: Clone codehasan/dex2c & Patch dcc.cfg + C++ Sanitizer
      |        run: |
      |          git clone --depth 1 https://github.com/codehasan/dex2c.git /tmp/d2c
      |          mkdir -p /tmp/d2c/tools
      |          wget -q "https://github.com/iBotPeaches/Apktool/releases/download/v2.10.0/apktool_2.10.0.jar" -O /tmp/d2c/tools/apktool.jar || \
      |          wget -q "https://bitbucket.org/iBotPeaches/apktool/downloads/apktool_2.10.0.jar" -O /tmp/d2c/tools/apktool.jar
      |          chmod +r /tmp/d2c/tools/apktool.jar
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
      |              if k != "ndk_dir" and k != "ollvm":
      |                  base_cfg[k] = v
      |          base_cfg["ndk_dir"] = os.environ["ANDROID_NDK_HOME"]
      |          base_cfg["filter"] = "/tmp/d2c/filter.txt"
      |          with open(upstream_cfg_path, "w", encoding="utf-8") as f:
      |              json.dump(base_cfg, f, indent=2)
      |
      |          # Patch template Android.mk to permit D8 register reuse casts in Clang++ and ensure 16KB alignment
      |          android_mk = "/tmp/d2c/project/jni/Android.mk"
      |          if os.path.exists(android_mk):
      |              with open(android_mk, "r", encoding="utf-8") as f:
      |                  mk_src = f.read()
      |              if "-fpermissive" not in mk_src:
      |                  mk_src += "\nLOCAL_CPPFLAGS += -fpermissive -Wno-int-conversion -Wno-error\nLOCAL_CFLAGS += -Wno-int-conversion -Wno-error\n"
      |              if "max-page-size=16384" not in mk_src:
      |                  mk_src += "\nLOCAL_LDFLAGS += \"-Wl,-z,max-page-size=16384\"\n"
      |              with open(android_mk, "w", encoding="utf-8") as f:
      |                  f.write(mk_src)
      |
      |          # Patch dex2c/writer.py to inject safe intptr_t casts into jvalue args[] union initializers
      |          writer_py = "/tmp/d2c/dex2c/writer.py"
      |          if os.path.exists(writer_py):
      |              with open(writer_py, "r", encoding="utf-8") as f:
      |                  w_src = f.read()
      |              w_patches = [
      |                  ("vars.append('{.l = %s}' % (self.get_variable_or_const(arg)))", "vars.append('{.l = (jobject)(intptr_t)(%s)}' % (self.get_variable_or_const(arg)))"),
      |                  ("vars.append('{.z = (jboolean) %s}' % (self.get_variable_or_const(arg)))", "vars.append('{.z = (jboolean)(intptr_t)(%s)}' % (self.get_variable_or_const(arg)))"),
      |                  ("vars.append('{.b = (jbyte) %s}' % (self.get_variable_or_const(arg)))", "vars.append('{.b = (jbyte)(intptr_t)(%s)}' % (self.get_variable_or_const(arg)))"),
      |                  ("vars.append('{.c = (jchar) %s}' % (self.get_variable_or_const(arg)))", "vars.append('{.c = (jchar)(intptr_t)(%s)}' % (self.get_variable_or_const(arg)))"),
      |                  ("vars.append('{.s = (jshort) %s}' % (self.get_variable_or_const(arg)))", "vars.append('{.s = (jshort)(intptr_t)(%s)}' % (self.get_variable_or_const(arg)))"),
      |                  ("vars.append('{.i = %s}' % (self.get_variable_or_const(arg)))", "vars.append('{.i = (jint)(intptr_t)(%s)}' % (self.get_variable_or_const(arg)))"),
      |                  ("vars.append('{.j = (jlong) %s}' % (self.get_variable_or_const(arg)))", "vars.append('{.j = (jlong)(intptr_t)(%s)}' % (self.get_variable_or_const(arg)))"),
      |                  ("vars.append('{.f = %s}' % (self.get_variable_or_const(arg)))", "vars.append('{.f = (jfloat)(%s)}' % (self.get_variable_or_const(arg)))"),
      |                  ("vars.append('{.d = %s}' % (self.get_variable_or_const(arg)))", "vars.append('{.d = (jdouble)(%s)}' % (self.get_variable_or_const(arg)))"),
      |              ]
      |              for orig, repl in w_patches:
      |                  w_src = w_src.replace(orig, repl)
      |              with open(writer_py, "w", encoding="utf-8") as f:
      |                  f.write(w_src)
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
      |                      arg_re = re.compile(r"(\\{\\s*\\.([a-z])\\s*=\\s*)([^}]+)(\\s*\\})")
      |                      cast_map = {
      |                          "l": "(jobject)(intptr_t)",
      |                          "i": "(jint)(intptr_t)",
      |                          "z": "(jboolean)(intptr_t)",
      |                          "b": "(jbyte)(intptr_t)",
      |                          "c": "(jchar)(intptr_t)",
      |                          "s": "(jshort)(intptr_t)",
      |                          "j": "(jlong)(intptr_t)",
      |                          "f": "(jfloat)",
      |                          "d": "(jdouble)",
      |                      }
      |                      def _fix_args_line(m):
      |                          def _fix_elem(em):
      |                              pfx, fld, val, sfx = em.group(1), em.group(2), em.group(3).strip(), em.group(4)
      |                              if "(intptr_t)" in val or fld not in cast_map:
      |                                  return em.group(0)
      |                              clean_val = re.sub(r"^\\((?:jboolean|jbyte|jchar|jshort|jint|jlong|jfloat|jdouble|jobject)\\)\\s*", "", val)
      |                              return f"{pfx}{cast_map[fld]}({clean_val}){sfx}"
      |                          return arg_re.sub(_fix_elem, m.group(0))
      |
      |                      for cpp_path in glob.glob(os.path.join(nc_dir, "*.cpp")):
      |                          try:
      |                              with open(cpp_path, "r", encoding="utf-8", errors="ignore") as cf:
      |                                  code = cf.read()
      |                              orig = code
      |                              obj_regs = {m.group(2): m.group(1) for m in decl_re.finditer(code)}
      |                              if obj_regs:
      |                                  def _fix_assign(m):
      |                                      indent, reg, lit, tail = m.group(1), m.group(2), m.group(3), m.group(4)
      |                                      if reg in obj_regs and lit not in ("0", "0x0"):
      |                                          jtype = obj_regs[reg]
      |                                          return f"{indent}{reg} = reinterpret_cast<{jtype}>(static_cast<intptr_t>({lit}));{tail}"
      |                                      return m.group(0)
      |                                  code = assign_re.sub(_fix_assign, code)
      |                              code = re.sub(r"jvalue\\s+args\\[\\]\\s*=\\s*\\{[^\\;]*\\}\\s*;", _fix_args_line, code)
      |                              if code != orig:
      |                                  with open(cpp_path, "w", encoding="utf-8") as cf:
      |                                      cf.write(code)
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
      |              if 'print(f"{str(e)}")' in dcc_src and "sys.exit(1)" not in dcc_src:
      |                  dcc_src = dcc_src.replace('print(f"{str(e)}")', 'print(f"{str(e)}"); import sys; sys.exit(1)')
      |              with open(dcc_py, "w", encoding="utf-8") as f:
      |                  f.write(dcc_src)
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
      |      - name: Run codehasan/dex2c Compilation
      |        working-directory: /tmp/d2c
      |        run: |
      |          set -o pipefail
      |          BUILD_TOOLS=${'$'}(find "${'$'}ANDROID_HOME/build-tools" -maxdepth 1 -mindepth 1 | sort -V | tail -n 1)
      |          export PATH="${'$'}BUILD_TOOLS:${'$'}PATH"
      |          ARGS=(
      |            -a /tmp/work/input.apk
      |            -o /tmp/work/unsigned_dex2c.apk
      |            --filter /tmp/d2c/filter.txt
      |            --disable-signing
      |            --allow-global
      |          )
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
      |            ARGS+=(--custom-loader "${'$'}{{ github.event.inputs.custom_loader }}")
      |          fi
      |          if [ "${'$'}{{ github.event.inputs.enable_ollvm }}" = "true" ] && [ -n "${'$'}{{ github.event.inputs.ollvm_flags }}" ]; then
      |            python3 - <<'PY'
      |          import json, os, subprocess
      |          cfg_path = "/tmp/d2c/dcc.cfg"
      |          ndk_home = os.environ.get("ANDROID_NDK_HOME", "")
      |          clang_candidates = []
      |          for root, dirs, files in os.walk(ndk_home):
      |              if "clang" in files and "bin" in root:
      |                  clang_candidates.append(os.path.join(root, "clang"))
      |          clang_bin = clang_candidates[0] if clang_candidates else "clang"
      |          probe_cmd = [clang_bin, "-mllvm", "-fla", "-x", "c", "-c", "/dev/null", "-o", "/dev/null"]
      |          can_ollvm = False
      |          try:
      |              res = subprocess.run(probe_cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=10)
      |              if res.returncode == 0:
      |                  can_ollvm = True
      |              else:
      |                  err = res.stderr.decode("utf-8", errors="ignore")
      |                  if "Unknown command line argument" not in err:
      |                      can_ollvm = True
      |          except Exception:
      |              pass
      |
      |          if not can_ollvm:
      |              print("::warning::Active NDK compiler does not support OLLVM flags (-fla, -sub, -bcf). Falling back to standard Clang flags to ensure successful compilation.")
      |              if os.path.exists(cfg_path):
      |                  with open(cfg_path, "r", encoding="utf-8") as f:
      |                      cfg = json.load(f)
      |                  if "ollvm" in cfg:
      |                      cfg["ollvm"]["enable"] = False
      |                  with open(cfg_path, "w", encoding="utf-8") as f:
      |                      json.dump(cfg, f, indent=2)
      |          elif os.path.exists(cfg_path):
      |              with open(cfg_path, "r", encoding="utf-8") as f:
      |                  cfg = json.load(f)
      |              cfg["ollvm"] = {
      |                  "enable": True,
      |                  "flags": "${'$'}{{ github.event.inputs.ollvm_flags }}"
      |              }
      |              with open(cfg_path, "w", encoding="utf-8") as f:
      |                  json.dump(cfg, f, indent=2)
      |              print("OLLVM compiler flags successfully injected into dcc.cfg")
      |          PY
      |          else
      |            python3 - <<'PY'
      |          import json, os
      |          cfg_path = "/tmp/d2c/dcc.cfg"
      |          if os.path.exists(cfg_path):
      |              with open(cfg_path, "r", encoding="utf-8") as f:
      |                  cfg = json.load(f)
      |              if "ollvm" in cfg:
      |                  cfg["ollvm"]["enable"] = False
      |              with open(cfg_path, "w", encoding="utf-8") as f:
      |                  json.dump(cfg, f, indent=2)
      |          PY
      |          fi
      |          MAX_ATTEMPTS=3
      |          for ATTEMPT in ${'$'}(seq 1 ${'$'}MAX_ATTEMPTS); do
      |            echo "=== codehasan/dex2c compilation attempt ${'$'}ATTEMPT/${'$'}MAX_ATTEMPTS ==="
      |            rm -f /tmp/work/unsigned_dex2c.apk
      |            python3 /tmp/d2c/dcc.py "${'$'}{ARGS[@]}" 2>&1 | tee /tmp/work/dex2c_build.log
      |            if [ -f /tmp/work/unsigned_dex2c.apk ]; then
      |              echo "codehasan/dex2c successfully compiled unsigned_dex2c.apk on attempt ${'$'}ATTEMPT"
      |              break
      |            fi
      |            echo "Attempt ${'$'}ATTEMPT failed or unsigned_dex2c.apk not found"
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
      |          APP_NAME="${'$'}{{ github.event.inputs.app_name }}"
      |          if [ -z "${'$'}APP_NAME" ] || [ "${'$'}APP_NAME" = "app" ]; then
      |            RAW_APK="${'$'}{{ github.event.inputs.apk_path }}"
      |            APP_NAME=${'$'}(basename "${'$'}RAW_APK" .apk)
      |          fi
      |          APP_NAME=${'$'}(echo "${'$'}APP_NAME" | sed 's/[^A-Za-z0-9._-]//g')
      |          if [ -z "${'$'}APP_NAME" ]; then APP_NAME="dex2c_output"; fi
      |          OUTPUT_APK="/tmp/work/${'$'}{APP_NAME}_dex2c_signed.apk"
      |          OUTPUT_LOG="/tmp/work/${'$'}{APP_NAME}_dex2c_build.log"
      |          cp /tmp/work/dex2c_build.log "${'$'}OUTPUT_LOG" || true
      |          if [ "${'$'}{{ github.event.inputs.signing_mode }}" = "REPO_KEYSTORE_SECRETS" ] && [ -n "${'$'}KEYSTORE_B64" ]; then
      |            echo "${'$'}KEYSTORE_B64" | base64 --decode > /tmp/work/release.jks
      |            "${'$'}BUILD_TOOLS/apksigner" sign \
      |              --ks /tmp/work/release.jks \
      |              --ks-pass "env:KEYSTORE_PASS" \
      |              --ks-key-alias "${'$'}KEY_ALIAS" \
      |              --key-pass "env:KEY_PASS" \
      |              --out "${'$'}OUTPUT_APK" \
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
      |              --out "${'$'}OUTPUT_APK" \
      |              /tmp/work/aligned_dex2c.apk
      |          fi
      |          echo "ARTIFACT_APK_NAME=${'$'}{APP_NAME}-signed-apk" >> "${'$'}GITHUB_ENV"
          echo "ARTIFACT_LOG_NAME=${'$'}{APP_NAME}-build-log" >> "${'$'}GITHUB_ENV"
          cp "${'$'}OUTPUT_APK" /tmp/work/dex2c-signed-output.apk || true
      |
      |      - name: Upload Signed Dex2C APK Artifact
      |        if: success()
      |        uses: actions/upload-artifact@v4
      |        with:
      |          name: ${'$'}{{ env.ARTIFACT_APK_NAME || 'dex2c-signed-apk' }}
      |          path: |
      |            /tmp/work/*_dex2c_signed.apk
      |            /tmp/work/dex2c-signed-output.apk
      |          retention-days: 14
      |
      |      - name: Upload Dex2C Build Log
      |        if: always()
      |        uses: actions/upload-artifact@v4
      |        with:
      |          name: ${'$'}{{ env.ARTIFACT_LOG_NAME || 'dex2c-build-log' }}
      |          path: |
      |            /tmp/work/*_dex2c_build.log
      |            /tmp/work/dex2c_build.log
      |          if-no-files-found: ignore
      |          retention-days: 14
      """.trimMargin() + "\n"
  }
}
