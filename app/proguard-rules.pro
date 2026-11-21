# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# --- Sora-Editor Core & TextMate R8/ProGuard Rules ---
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

# --- Dex2C JNI & Native Bridge Rules ---
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.dex2c.studio.nativebridge.** { *; }
-keep class com.dex2c.studio.nativeengine.** { *; }
-keep class com.dex2c.studio.transpiler.** { *; }
-keep class com.dex2c.studio.cloud.** { *; }
-keep class com.dex2c.studio.model.** { *; }

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
-keep class androidx.work.impl.background.systemalarm.RescheduleReceiver { *; }
-keep class androidx.work.impl.diagnostics.DiagnosticsReceiver { *; }
-dontwarn androidx.work.impl.**

# --- Smali / Dexlib2 Rules ---
-keep class org.jf.** { *; }
-dontwarn org.jf.**
-keep class com.android.tools.smali.** { *; }
-dontwarn com.android.tools.smali.**

# --- Network & Serialization Warnings ---
-dontwarn okhttp3.**
-dontwarn okio.**

