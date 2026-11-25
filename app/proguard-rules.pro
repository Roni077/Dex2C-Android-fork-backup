# ==============================================================================
# Dex2C Studio ProGuard / R8 Optimization & Obfuscation Rules
# ==============================================================================

# --- 汉字混淆字典配置 (Chinese Character Obfuscation Dictionaries) ---
-obfuscationdictionary obfuscation-dictionary.txt
-classobfuscationdictionary obfuscation-dictionary.txt
-packageobfuscationdictionary obfuscation-dictionary.txt

# Preserve line numbers and source file attributes for actionable crash stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# --- AndroidX Activity, ActivityResult & Compose Integration ---
# Ensure R8 never prunes, devirtualizes, or renames ActivityResultRegistryOwner
-keep class * implements androidx.activity.result.ActivityResultRegistryOwner {
    public androidx.activity.result.ActivityResultRegistry getActivityResultRegistry();
}
-keep class androidx.activity.result.ActivityResultRegistry { *; }
-keep class androidx.activity.compose.LocalActivityResultRegistryOwner { *; }

# ViewTree owners for Compose and Lifecycle integration
-keep class androidx.lifecycle.ViewTreeLifecycleOwner { *; }
-keep class androidx.lifecycle.ViewTreeViewModelStoreOwner { *; }
-keep class androidx.savedstate.ViewTreeSavedStateRegistryOwner { *; }

# --- Dex2C JNI & Native Bridge Rules ---
# Preserve native method entrypoints and bridge models
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.dex2c.studio.nativebridge.** { *; }
-keep class com.dex2c.studio.nativeengine.** { *; }

# Preserve Dex2C application models, configurations, data stores and filters
-keep class com.dex2c.studio.apk.** { *; }
-keep class com.dex2c.studio.cloud.** { *; }
-keep class com.dex2c.studio.data.** { *; }
-keep class com.dex2c.studio.dex.** { *; }
-keep class com.dex2c.studio.transpiler.** { *; }

# Preserve standard Enum methods used across transpiler/cloud options
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Sora-Editor Core, TextMate & Syntax Parser Rules ---
-keep class io.github.rosemoe.sora.** { *; }
-dontwarn io.github.rosemoe.sora.**

-keep class io.github.rosemoe.sora.langs.textmate.** { *; }
-dontwarn io.github.rosemoe.sora.langs.textmate.**

-keep class org.eclipse.tm4e.** { *; }
-dontwarn org.eclipse.tm4e.**

-keep class org.jcodings.** { *; }
-keep class org.joni.** { *; }
-dontwarn org.jcodings.**
-dontwarn org.joni.**

-keep class org.snakeyaml.engine.** { *; }
-dontwarn org.snakeyaml.engine.**

-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- Smali / Dexlib2 / ANTLR / JCommander Rules ---
-keep class org.jf.** { *; }
-dontwarn org.jf.**
-keep class com.android.tools.smali.** { *; }
-dontwarn com.android.tools.smali.**
-keep class org.antlr.runtime.** { *; }
-dontwarn org.antlr.**
-dontwarn antlr.**
-dontwarn org.stringtemplate.**
-dontwarn com.beust.jcommander.**

# Suppress desktop JVM classes (AWT / Swing) referenced by ANTLR/StringTemplate tooling
-dontwarn java.awt.**
-dontwarn javax.swing.**

# --- AndroidX App Startup Rules ---
-keep class * implements androidx.startup.Initializer {
    public <init>();
}

# --- AndroidX WorkManager & Embedded Room Database Rules ---
-keep class androidx.work.Configuration$* { *; }
-keep class androidx.work.impl.WorkDatabase_Impl {
    public <init>();
    *;
}
-keep class * extends androidx.room.RoomDatabase {
    public <init>();
}
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class androidx.work.impl.background.systemalarm.RescheduleReceiver { *; }
-keep class androidx.work.impl.diagnostics.DiagnosticsReceiver { *; }
-dontwarn androidx.work.impl.**

# --- OkHttp 3 & Okio Network Rules ---
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Guava / Common Dependencies Warnings ---
-dontwarn com.google.common.**
-dontwarn com.google.errorprone.annotations.**
