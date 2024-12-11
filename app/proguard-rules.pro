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


-optimizationpasses 5

#包明不混合大小写
-dontusemixedcaseclassnames

#不去忽略非公共的库类
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers

# keep 泛型
-keepattributes Signature
-keep public class * extends androidx.appcompat.app.AppCompatActivity
-keep public class * extends androidx.multidex.MultiDexApplication
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep interface android.database.Cursor { *; }

-keep class androidx.databinding.** { *; }
-keep public class * extends androidx.databinding.DataBinderMapper

# 保持自定义控件类不被混淆
-keepclassmembers class * extends androidx.appcompat.app.AppCompatActivity{
    public void *(android.view.View);
}

# 自定义view
-keep public class * extends android.view.View{
        *** get*();
        void set*(***);
        public <init>(android.content.Context);
        public <init>(android.content.Context,android.util.AttributeSet);
        public <init>(android.content.Context,android.util.AttributeSet,int);
}

-keep class androidx.appcompat.widget.** { *; }

# 保持native方法不被混淆
-keepclasseswithmembernames class * {
    native <methods>;
}

#枚举不被混淆
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

 # 保持Parcelable不被混淆
-keep class * implements android.os.Parcel {
    public static final android.os.Parcelable$Creator *;
}
-keepclassmembers class * implements android.os.Parcelable{*;}

#xpopu
-dontwarn com.lxj.xpopup.widget.**
-keep class com.lxj.xpopup.widget.**{*;}

#okhttp
-dontwarn okhttp3.**
-keep class okhttp3.**{*;}
#okio
-dontwarn okio.**
-keep class okio.**{*;}

-keep class * implements java.io.Serializable { *; }
-keepattributes *Annotation
-keep class * implements java.lang.annotation.Annotation { *; }
-keep class com.alibaba.fastjson.* { *; }
-keep class com.ling.fast.bean** { *; }
#PictureSelector 2.0
-keep class com.luck.picture.lib.** { *; }
#Ucrop
-dontwarn com.yalantis.ucrop**
-keep class com.yalantis.ucrop** { *; }
-keep interface com.yalantis.ucrop** { *; }

#华为
-ignorewarnings
-keepattributes *Annotation*
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-keep class com.huawei.updatesdk.**{*;}
-keep class com.huawei.hms.**{*;}
-keep class com.huawei.android.hms.agent.**{*;}
-keep class com.huawei.hianalytics.**{*;}

-keepattributes Signature
-keepattributes Exceptions

#高德地图
-keep class com.amap.api.maps.**{*;}
-keep class com.autonavi.**{*;}
-keep class com.amap.api.trace.**{*;}
-keep class com.amap.api.location.**{*;}
-keep class com.amap.api.fence.**{*;}
-keep class com.loc.**{*;}
-keep class com.autonavi.aps.amapapi.model.**{*;}
-keep class com.amap.api.services.**{*;}
-keep class com.chat.map.ui.DatasKey { *; }


#实体类不需要混淆[需要和网络交换的实体]
#----------悟空 sdk 数据如果不需要sdk的实体参与网络数据交互就不需要混淆
-dontwarn com.xinbida.wukongim.**
-keep class com.xinbida.wukongim.**{*;}

#数据库加密
-keep,includedescriptorclasses class net.sqlcipher.** { *; }
-keep,includedescriptorclasses interface net.sqlcipher.** { *; }

#--------- 混淆dh curve25519-------
-keep class org.whispersystems.curve25519.**{*;}
-keep class org.whispersystems.** { *; }
-keep class org.thoughtcrime.securesms.** { *; }

-dontwarn org.xsocket.**
-keep class org.xsocket.** {*;}

#----------UI-------------------
-keep class com.chat.base.entity.** { *; }
-keep class com.chat.base.base.** { *; }
-keep class com.chat.base.net.entity.** { *; }
#----------登录模块---------------
-keep class com.chat.login.entity.** { *; }
#----------uikit模块--------------
-keep class com.chat.uikit.contacts.FriendUIEntity { *; }
-keep class com.chat.uikit.chat.msgmodel.** { *; }
-keep class com.chat.uikit.enity.** { *; }
-keep class com.chat.uikit.group.service.entity.** { *; }
-keep class com.chat.uikit.group.GroupEntity { *; }
-keep class com.chat.uikit.group.GroupMemberEntity { *; }
-keep class com.chat.uikit.message.Ipentity { *; }
-keep class com.chat.uikit.message.SyncMsg { *; }
-keep class com.chat.uikit.message.SyncMsgHeader { *; }
-keep class com.chat.uikit.search.SearchUserEntity { *; }
-keep class com.chat.uikit.robot.entity.** { *; }
#----------群管理-----------------
-keep class com.chat.groupmanage.entity.** { *; }
#----------文件模块----------------
-keep class com.chat.file.msgitem.FileContent { *; }
#----------收藏模块----------------
-keep class com.chat.favorite.entity.**{ *; }
#----------扫一扫模块----------------
-keep class com.chat.scan.entity.** { *; }
#----------朋友圈模块----------------
-keep class com.chat.moments.entity.** { *; }
#----------标签模块------------------
-keep class com.chat.label.entity.** { *; }
#----------表情模块------------------
-keep class com.chat.sticker.entity.** { *; }
#----------客服------------------
-keep class com.chat.customerservice.entity.** { *; }
#----------隐私安全------------------
-keep class com.chat.security.entity.** { *; }
#----------旗舰模块------------------
-keep class com.chat.advanced.entity.** { *; }
#----------音视频------------------
-keep class com.chat.rtc.entity.** { *; }
-keep class owt.**{*;}
-keep class org.webrtc.**{*;}
#----------社区------------------
-keep class com.community.entity.** { *; }
#----------用户名登录------------------
-keep class com.chat.wkusernamelogin.entity.**{*;}
#----------web3------------------
-keep class com.chat.wkweb3.entity.**{*;}
#----------工作台------------------
-keep class com.chat.workplace.entity.**{*;}
#----------组织架构------------------
-keep class com.chat.organization.entity.**{*;}
#----------消息置顶------------------
-keep class com.chat.pinned.message.entity.**{*;}
#---------注册邀请模块-------------
-keep class com.chat.invite.entity.**{*;}

-keep class org.web3j.**{*;}
-dontwarn org.web3j.**

#---------rxjava retrofit 混淆-----------------
-dontnote retrofit2.Platform
-keep class retrofit2.**{}
-keep class io.reactivex.rxjava3.**{}

# RxJava RxAndroid
-dontwarn sun.misc.**
-keepclassmembers class rx.internal.util.unsafe.*ArrayQueue*Field* {
    long producerIndex;
    long consumerIndex;
}


#-------------gsy混淆-----------------
-keep class com.shuyu.gsyvideoplayer.** { *; }
-keep class com.shuyu.gsyvideoplayer.video.** { *; }
-dontwarn com.shuyu.gsyvideoplayer.video.**
-keep class com.shuyu.gsyvideoplayer.video.base.** { *; }
-dontwarn com.shuyu.gsyvideoplayer.video.base.**
-keep class com.shuyu.gsyvideoplayer.utils.** { *; }
-dontwarn com.shuyu.gsyvideoplayer.utils.**
-keep class tv.danmaku.ijk.** { *; }
-dontwarn tv.danmaku.ijk.**
-keep class androidx.media3.** {*;}
-keep interface androidx.media3.**

-keep class com.shuyu.alipay.** {*;}
-keep interface com.shuyu.alipay.**

-keep public class * extends android.view.View{
    *** get*();
    void set*(***);
    public <init>(android.content.Context);
    public <init>(android.content.Context, java.lang.Boolean);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
#--------角标---------
-keep class me.leolin.shortcutbadger.**{*;}
#-------------x5webview------------
-dontwarn dalvik.**
-dontwarn com.tencent.smtt.**

-keep class com.tencent.smtt.** {
    *;
}
-keep class com.tencent.tbs.** {
    *;
}
-dontwarn com.tencent.bugly.**
-keep public class com.tencent.bugly.**{*;}

-keep class com.tbruyelle.rxpermissions3.**{*;}

#-------------小米推送-----------
#这里com.xiaomi.mipushdemo.DemoMessageRreceiver改成app中定义的完整类名
-keep class com.chat.push.push.XiaoMiMessageReceiver {*;}
#可以防止一个误报的 warning 导致无法成功编译，如果编译使用的 Android 版本是 23。
-dontwarn com.xiaomi.push.**

#-----------oppo----------------
-keep public class * extends android.app.Service
-keep class com.heytap.msp.** { *;}

#------------FMC---------------
# 保留Firebase的类和成员不被混淆
-keep class com.google.firebase.** { *; }
-keep class org.apache.** { *; }
-keep class javax.** { *; }
-keep class uk.** { *; }

# 如果你使用Firebase动态链接或通知等，还需要添加以下规则
-keep class com.google.firebase.dynamiclinks.** { *; }
-keep class com.google.firebase.messaging.** { *; }

# 如果你使用Firebase Remote Config
-keep class com.google.firebase.remoteconfig.** { *; }

# 如果你使用Firebase Crashlytics
-keep class com.google.firebase.crash.** { *; }
-keep class com.crashlytics.** { *; }
-dontwarn com.crashlytics.**

#---------音视频------------------
-dontwarn org.json.**
-keep class org.json.** {*;}
-dontwarn com.google.gson.**
-keep class com.google.gson.** {*;}
-dontwarn aidl.**
-keep class aidl.** { *; }
-keep class io.socket.** {*;}
-dontwarn io.socket.**

-keep class com.xinbida.rtc.WKRTCApplication {*;}
-keep class com.xinbida.rtc.WKRTCCallType {*;}
-keep class com.xinbida.rtc.utils.WKRTCManager {*;}
-keep class com.xinbida.rtc.inters.** {*;}
-keep class owt.** {*;}
-keep class org.webrtc.** {*;}

-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public enum com.bumptech.glide.load.resource.bitmap.ImageHeaderParser$** {
  **[] $VALUES;  public *;
}
