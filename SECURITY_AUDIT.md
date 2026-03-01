# DMWork IM Android 客户端安全审计（2026-03-01）

## 已修复问题

1. **WebView TLS/加载策略缺陷（高危）**  
   - 发现：BridgeWebViewClient 直接 `sslErrorHandler.proceed()`，WKWebViewActivity 默认把无协议 URL 改为 `http://` 并允许文件访问，易被中间人和本地文件注入利用。  
   - 修复：改为记录并拒绝存在证书问题的页面、默认强制 HTTPS，禁用文件访问且将混合内容模式设为 `NEVER_ALLOW`，避免加载非 HTTPS 资源。  
   - 代码：`wkbase/src/main/java/com/chat/base/jsbrigde/BridgeWebViewClient.java`、`wkbase/src/main/java/com/chat/base/act/WKWebViewActivity.java`。

2. **明文存储账号与 Token（高危）**  
   - 发现：WKSharedPreferencesUtil 使用普通 SharedPreferences，所有 UID/Token、设备 ID 等敏感信息明文落盘。  
   - 修复：切换为 AndroidX `EncryptedSharedPreferences`（AES-256-GCM/SIV），异常时回落至私有 SharedPreferences 防止崩溃。  
   - 代码：`wkbase/src/main/java/com/chat/base/config/WKSharedPreferencesUtil.java`；依赖：`wkbase/build.gradle` 增加 `androidx.security:security-crypto`。

3. **SQL 注入风险（中危）**  
   - 发现：多处 `rawQuery` 直接拼接用户/服务端输入（好友申请、命令表、通讯录、敏感词），可被注入。  
   - 修复：统一改为占位符+`selectionArgs`，动态 IN 列表使用 `?` 占位拼装。  
   - 代码：`ApplyDB.java`、`WKBaseCMDManager.java`、`WKContactsDB.java`、`ProhibitWordDB.kt`。

4. **文件名未校验导致路径穿越（中危）**  
   - 发现：`WKFileUtils.generateFileName` 直接以服务端文件名创建文件，`"../"` 可写出沙箱目录。  
   - 修复：去除路径分隔符和 `..`，校验 canonicalPath 必须落在 `chatDownloadFileDir`，否则拒绝/重命名。  
   - 代码：`wkbase/src/main/java/com/chat/base/utils/WKFileUtils.java`。

5. **允许明文网络流量（中危）**  
   - 发现：主 Manifest 设置 `usesCleartextTraffic="true"`，且登录页允许录入/自动补全 `http://` 基础域名。  
   - 修复：关闭应用级明文流量，并在登录/三方登录自定义域名时强制使用 HTTPS。  
   - 代码：`app/src/main/AndroidManifest.xml`、`WKLoginActivity.java`、`ThirdLoginActivity.kt`。

## 未解决/需关注的风险
- 应用仍请求广泛存储/系统级权限（如 `MANAGE_EXTERNAL_STORAGE`, `READ_LOGS`, `MOUNT_UNMOUNT_FILESYSTEMS` 等）。这些权限可能被审核拒绝并增加攻击面，建议按实际功能最小化。  
- 若后端只提供 HTTP，将无法正常访问；需要服务器侧支持 HTTPS 或为特定域名配置 networkSecurityConfig。  
- 仍需针对其他文件/媒体处理流程做进一步模糊测试，确保无剩余路径遍历或类型混淆问题。

## 编译验证
- 命令：`GRADLE_USER_HOME=/tmp/gradle-cache ./gradlew assembleDebug`  
- 结果：失败。沙箱禁止联网下载 Gradle 8.13 分发包（SocketException: Operation not permitted），无法完成本地构建。需要可联网或已缓存的 Gradle 发行版后再重试。

## 变更摘要
- 引入加密偏好存储，修正 WebView 安全配置与默认协议。  
- 修复 4 处 SQL 注入拼接点。  
- 增强下载文件名校验，阻断目录穿越。  
- 关闭明文流量并强制自定义域名使用 HTTPS。
