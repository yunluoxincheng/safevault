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

# DebugSslProvider 仅用于 Debug 构建，Release 版本应移除
# 这些方法仅在 BuildConfig.DEBUG 为 true 时调用，Release 构建中应完全移除
-assumenosideeffects class com.ttt.safevault.network.DebugSslProvider {
    public static *** getSSLSocketFactory(...);
    public static *** getTrustManager(...);
    public static *** isDomainAllowed(...);
}