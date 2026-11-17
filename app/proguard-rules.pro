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

