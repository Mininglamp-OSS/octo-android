# OctoIM

![](https://img.shields.io/badge/platform-android-blue.svg)  ![](https://img.shields.io/badge/compileSdkVersion-33-blue.svg) ![](https://img.shields.io/badge/minSdkVersion-23-blue.svg) ![](https://img.shields.io/hexpm/l/plug.svg)

### OctoIM — 开源即时通讯客户端

## 📱 下载测试 APK

每次 master 有新提交时，CI 会自动构建最新的 dev APK 并发布到：

- **最新版（latest symlink）**: https://your-server.example.com/apk/octoim-dev-latest.apk
- **特定 commit**: https://your-server.example.com/apk/octoim-dev-{short-sha}.apk（8 位 git sha）
- **Build 历史**: GitHub Actions → Android Build workflow

> dev flavor 连接 测试环境。
> 如果之前装过不同签名的 APK，先 `adb uninstall com.octoim.app`。

OctoIM基于底层通讯框架`OctoIM`实现聊天功能。已实现`文本`，`图片`，`语音`，`名片`，`emoji`，群聊`@某人`，消息`链接`，`手机号`，`邮箱`识别等功能。聊天设置支持`名称修改`，`头像修改`，`公告编辑`，`消息免打扰`，`置顶`，`保存到通讯录`，`聊天内昵称`，`群内成员昵称显示`等丰富的设置功能。由于项目是模块化开发，开发者可完全按自己的开发习惯进行二次开发。

### OctoIM特点

- #### **永久保存消息** 卸载OctoIM后下次安装登录后，可查看以前的聊天记录
- #### **超大群** OctoIM群聊人数无限制，万人群进入聊天完全不卡，消息正常收发
- #### **实时性** 所有操作实时同步，app已读消息，web/pc可实时更改状态
- #### **扩展性强** OctoIM现有框架可轻松支持消息的已读未读回执，消息点赞，消息回复功能
- #### **开源** OctoIM 100%开源，商业开发无需授权可直接使用

### 项目模块
OctoIM是模块化开发，不限制开发者编码习惯。以下是对各个模块的说明

**`wkbase`**

基础模块 里面包含了`WKBaseApplication`文件，该文件主要是对一些通用工具做些初始化功能，如：网络库初始化，本地db文件初始化等。`WKChatBaseProvider`聊天中重要的基础消息item提供者，所有消息item均继承于此类，里面处理消息气泡样式，头像显示样式，消息间距等很多统一且重要的功能。更多功能请查看源码

**`wkuikit`**

聊天模块 包含了聊天页面`ChatActivity`，该文件处理了聊天信息的展示，离线获取，刷新消息状态等聊天中遇到的各个场景。`ChatFragment` 最近会话列表，新消息红点，聊天最后一条消息展示等。此模块还包括app首页信息，联系人信息，我的页面等

**`wklogin`**

登录模块 包含登陆注册，第三方授权登录，修改账号密码，授权pc/web登录等功能，实现其他方式登录可在此模块进行二次开发

**`wkpush`**

推送模块  OctoIM集成了`华为`,`小米`,`vivo`,`oppo`,`FCM`厂商推送功能。所有推送 key 均通过 `local.properties` 配置，参考 `local.properties.example`：

- **华为** 配置 `HUAWEI_APP_ID`，并下载 `agconnect-services.json` 放到 `app/` 目录
- **小米** 配置 `XIAOMI_APP_ID` 和 `XIAOMI_APP_KEY`
- **OPPO** 配置 `OPPO_APP_KEY` 和 `OPPO_APP_SECRET`
- **VIVO** 配置 `VIVO_API_KEY` 和 `VIVO_APP_ID`
- **FCM** 从 [Firebase Console](https://firebase.google.com/) 下载 `google-services.json` 放到 `app/` 目录

不配置推送 key 时，推送功能自动跳过，不影响 APP 正常使用。

 **`wkscan`**

扫一扫模块 包含扫描二维码进行加好友，加入群聊等

### 自定义消息Item

**注意这里只是介绍如何将自定义的消息item展示在消息列表中，消息model的实现需要去查看[OctoIM](https://github.com/WuKongIM/WuKongIM "文档")文档**

OctoIM实现自定义消息Item也十分简单。只需要实现两步即可

1、 编写消息item provider。继承`WKChatBaseProvider`文件，重写`getChatViewItem`方法如下
```kotlin
override fun getChatViewItem(parentView: ViewGroup, from: WKChatIteMsgFromType): View? {
        return LayoutInflater.from(context).inflate(R.layout.chat_item_card, parentView, false)
    }
```
 > 布局中不需要考虑头像，名称字段

 重写`setData`方法 获取控件并将控件填充数据。如下
 ```kotlin
override fun setData(
    adapterPosition: Int,
    parentView: View,
    uiChatMsgItemEntity: WKUIChatMsgItemEntity,
    from: WKChatIteMsgFromType
) {
    val cardNameTv = parentView.findViewById<TextView>(R.id.userNameTv)
    val cardContent = uiChatMsgItemEntity.wkMsg.baseContentMsgModel as WKCardContent
    cardNameTv.text = cardContent.name
    // todo ...
}
 ```
> 这里的`WKCardContent`消息对象是基于`OctoIM`sdk的实现，所有自定义消息model必须基于`OctoIM`。关于`OctoIM`自定义消息可查看[Android文档](https://github.com/YOUR-ORG/octo-android "文档")中的自定义消息

 设置item的消息类型
 ```kotlin
override val itemViewType: Int
    get() = WKContentType.WK_CARD
 ```
2、完成消息item提供者的编写后需将该item注册到消息提供管理中。
```kotlin
WKMsgItemViewManager.getInstance().addChatItemViewProvider(WKContentType.WK_LOCATION, WKCardProvider())
```
对此自定义消息Item已经完成，在收到此类型的消息时就会展示到聊天列表中了
详细实现步骤可以查看代码`wkuikit`模块中`provider`包中的`WKImageProvider`文件
## 效果图

|对方正在输入|语音消息|合并转发|
|:---:|:---:|:--:|
|![](imgs/typing.webp)|![](imgs/voice.webp)|![](imgs/forward.webp)|


|快速回复|群内操作|其他功能|
|:---:|:---:|:-------------------:|
|![](imgs/reply.webp)|![](imgs/group.webp)| ![](imgs/other.webp) |

由于GIF被压缩，演示效果很模糊。真机预览效果更佳

### 第三方 SDK 说明

本项目集成了以下厂商推送 SDK，它们由各厂商提供，使用时请遵循各厂商的许可协议：

- **华为 Push SDK** — [华为开发者联盟](https://developer.huawei.com/)
- **小米 Push SDK** — [小米开放平台](https://dev.mi.com/)
- **OPPO Push SDK** — [OPPO 开放平台](https://open.oppomobile.com/)
- **Vivo Push SDK** — [Vivo 开放平台](https://dev.vivo.com.cn/)
- **Firebase Cloud Messaging** — [Firebase](https://firebase.google.com/)

这些 SDK 为可选组件，不配置对应的 key 时推送功能自动跳过，不影响 APP 正常使用。

### 许可证
OctoIM 使用 Apache 2.0 许可证。有关详情，请参阅 LICENSE 文件。

第三方代码归属详见 NOTICE 文件。
