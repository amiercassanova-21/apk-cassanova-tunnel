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
# ---------------------------------------------------------------------------------------------
# Cassanova: R8 runs only for slim builds (Amier VPN Lite, -PcassanovaSlim=true). It removes the
# unused parts of the libraries (Compose, Material3, CameraX, ...). The app's own classes are kept
# whole and nothing is renamed, because Gson, MMKV and JNI reach them by name.
-dontobfuscate
-keep class com.v2ray.ang.** { *; }
-keep class go.** { *; }
-keep class libv2ray.** { *; }
-keep class com.jcraft.jsch.** { *; }
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
# Optional dependencies of JSch, OkHttp and friends that are not on Android.
-dontwarn com.sun.jna.**
-dontwarn org.ietf.jgss.**
-dontwarn org.newsclub.**
-dontwarn org.slf4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
