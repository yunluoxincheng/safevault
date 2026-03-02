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

# ========================================
# 安全加固规则
# ========================================

# DebugSslProvider 仅用于 Debug 构建，Release 版本应移除
# 这些方法仅在 BuildConfig.DEBUG 为 true 时调用，Release 构建中应完全移除
-assumenosideeffects class com.ttt.safevault.network.DebugSslProvider {
    public static *** getSSLSocketFactory(...);
    public static *** getTrustManager(...);
    public static *** isDomainAllowed(...);
}

# 日志安全改进 - 在Release版本移除调试日志
# 注意：使用 -assumenosideeffects 仅优化调用，不会移除 Log 类本身
# Log.d, Log.v, Log.i 调用会被优化掉，但 Log.e 保留用于生产环境错误追踪
-assumenosideeffects class android.util.Log {
    public static boolean d(...);
    public static boolean v(...);
    public static boolean i(...);
}

# ========================================
# 模型和数据类保留规则（JSON序列化需要）
# ========================================

# 保留R8混淆所需的模型和DTO类
-keep class com.ttt.safevault.model.** { *; }
-keep class com.ttt.safevault.dto.** { *; }

# 保留ShareDataPacket和EncryptedSharePacket的字段（分享功能使用）
-keepclassmembers class com.ttt.safevault.crypto.ShareDataPacket { *; }
-keepclassmembers class com.ttt.safevault.crypto.EncryptedSharePacket { *; }

# ========================================
# Retrofit 2.x 规则
# ========================================

-keepattributes Signature
-keepattributes Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Don't warn about missing signature attributes
-dontwarn retrofit2.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# ========================================
# Gson 规则
# ========================================

# Gson specific classes
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class com.google.gson.internal.bind.** { *; }

# Application classes that will be serialized/deserialized over Gson
-keep class com.google.gson.examples.android.model.** { <fields>; }
-keepclassmembers class com.ttt.safevault.model.** { *; }
-keepclassmembers class com.ttt.safevault.dto.** { *; }

# ========================================
# OkHttp 规则
# ========================================

-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ========================================
# RxJava 3.x 规则
# ========================================

-dontwarn rx.**
-dontwarn io.reactivex.**
-keepclassmembers class io.reactivex.** { *; }

# ========================================
# Lifecycle ViewModel 和 LiveData 规则
# ========================================

-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }

-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>();
}

# LiveData
-keep class * extends androidx.lifecycle.LiveData { *; }
-keepclassmembers class * extends androidx.lifecycle.LiveData {
    <init>();
}

# ========================================
# Room 数据库规则
# ========================================

-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ========================================
# Glide 图片加载规则
# ========================================

-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

# ========================================
# ZXing QR码规则
# ========================================

-dontwarn com.google.zxing.**
-keep class com.google.zxing.** { *; }

# ========================================
# Biometric 生物识别规则
# ========================================

-keep class androidx.biometric.BiometricPrompt { *; }
-keep class androidx.biometric.BiometricPrompt$* { *; }

# ========================================
# Navigation 规则
# ========================================

-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends androidx.navigation.NavDestination

# ========================================
# WorkManager 规则
# ========================================

-keep class * extends androidx.work.Worker { *; }
-keepclassmembers class * extends androidx.work.Worker {
    <init>();
}

# ========================================
# Lombok 规则（如果使用 Lombok）
# ========================================

-dontwarn lombok.**
-keep class lombok.** { *; }

# ========================================
# Google Play Services 规则
# ========================================

-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ========================================
# 其他一般规则
# ========================================

# 保留 Parcelable 序列化类
-keep class * implements android.os.Parcelable { *; }
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# 保留 Serializable 序列化类
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# 保留 View 构造方法（布局使用）
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# 保留 R 类和资源
-keepclassmembers class **.R$* {
    public static <fields>;
}

# 保留枚举
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留 Native 方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# ========================================
# Bouncy Castle 加密库规则 (X25519/Ed25519 支持)
# ========================================

# 保留 X25519/Ed25519 相关的核心类
-keep class org.bouncycastle.crypto.ec.** { *; }
-keep class org.bouncycastle.crypto.params.** { *; }
-keep class org.bouncycastle.crypto.signers.** { *; }
-keep class org.bouncycastle.math.ec.** { *; }
-keep class org.bouncycastle.util.BigIntegers { *; }

# 保留 Curve25519 相关类
-keep class org.bouncycastle.crypto.digests.XofUtils { *; }
-keep class org.bouncycastle.crypto.digests.SHAKEDigest { *; }

# 避免警告
-dontwarn org.bouncycastle.**

# 精简 Bouncy Castle：移除未使用的算法
# 仅保留 X25519/Ed25519 所需的最小集合
-keep class org.bouncycastle.crypto.params.X25519Parameters { *; }
-keep class org.bouncycastle.crypto.params.Ed25519Parameters { *; }
-keep class org.bouncycastle.crypto.params.X25519PublicKeyParameters { *; }
-keep class org.bouncycastle.crypto.params.X25519PrivateKeyParameters { *; }
-keep class org.bouncycastle.crypto.params.Ed25519PublicKeyParameters { *; }
-keep class org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters { *; }
-keep class org.bouncycastle.crypto.signers.X25519Signer { *; }
-keep class org.bouncycastle.crypto.signers.Ed25519Signer { *; }

# ========================================
# Argon2 密码哈希规则
# ========================================

# 保留 Argon2 相关的 JNI 方法
-keepclassmembers class com.lambdapioneer.argon2kt.** {
    native <methods>;
}
-dontwarn com.lambdapioneer.argon2kt.**