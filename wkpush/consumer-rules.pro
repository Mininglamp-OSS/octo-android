# 华为 HMS Push
-ignorewarnings
-keepattributes *Annotation*
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-keep class com.huawei.hianalytics.**{*;}
-keep class com.huawei.updatesdk.**{*;}
-keep class com.huawei.hms.**{*;}
-keep class com.huawei.agconnect.**{*;}

# OPPO HeytapPush
-keep public class * extends android.app.Service
-keep class com.heytap.msp.** { *;}

# 小米 MiPush
-keep class com.xiaomi.** { *; }
-dontwarn com.xiaomi.push.**
