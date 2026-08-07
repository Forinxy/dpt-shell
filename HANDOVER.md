# dpt-shell Android 加固 App 移植 — 交接文档（HANDOVER）

> 版本：2.17.0 · 交付日期：2026-08-04
> 目标：将 dpt-shell v2.17.0 移植为 Android app（用户选 APK → 设备端加固 → 输出加固 APK）
> 状态：**源码与功能已改好并通过本机构建验证，APP 由用户侧自行构建**

---

## 1. 项目概述

| 项 | 值 |
|---|---|
| 项目名 | dpt-shell（Android 加固工具 App） |
| 基线版本 | v2.17.0（commit `2e4dedf1998aa298aa9f5eab3f4dbc4f4ed4e581`） |
| 新增模块 | `app/`（Android 加固工具，Compose UI） |
| App 包名 | `com.luoye.dpt.app` |
| 加固产物 | 输出已签名加固 APK，默认保存到**原 APK 所在目录**，命名 `原文件名_加固.apk` |
| 仓库 remote | `https://github.com/Forinxy/dpt-shell`（见第 13 节） |

核心思路：把桌面端 `dpt.jar` 的加固逻辑（APK 解包 → manifest 改写 → dex 处理 → so 加密 → 重打包 → 签名）整体编译进 Android App，壳资源（shell-files：壳 dex + 4 ABI 的 so）随 App 打包进 assets，运行时装到 filesDir 后由加固逻辑复用。

---

## 2. 交付物清单

| 交付物 | 路径 | 说明 |
|---|---|---|
| App 源码 | `app/` | Compose UI + 加固逻辑（复用 `dpt/src/main/java`） |
| 加固核心源码 | `dpt/` | 已做 Android 兼容改造（见第 6 节） |
| 壳资源 | `app/src/main/assets/shell-files/` | v2.17.0 发行包壳（build-key=`6c2200133b1ff490`） |
| 一键构建 Actions | `.github/workflows/build-apk.yml` | 阿里云+腾讯镜像，产物上传 artifact |
| 本交接文档 | `HANDOVER.md` | 本文件 |
| 源码 zip | `dpt-shell-app-源码.zip`（打包含脚本，见第 9 节） | 排除 jks/密钥/大文件 |
| debug APK | `app/build/outputs/apk/debug/app-debug.apk` | 本机已构建（约 19MB，已签名 v2） |

> 注：`.github/workflows/build-apk.yml` 为新增；原 `build.yml`/`release.yml` 保留但面向桌面 jar，`./gradlew build` 需 NDK/CMake/ninja，与本 App 无关。

---

## 3. 构建环境要求（用户侧）

本机已装，用户侧需一致或按下方等价安装：

| 组件 | 版本 | 本机路径 |
|---|---|---|
| JDK | 17 | `/usr/lib/jvm/java-17-openjdk-amd64` |
| Android SDK | cmdline-tools latest | `/opt/android-sdk` |
| build-tools | 35.0.0 | `/opt/android-sdk/build-tools/35.0.0` |
| platforms | android-35、android-36 | `/opt/android-sdk/platforms/` |
| Gradle Wrapper | 8.14（随仓库） | — |
| AGP / Kotlin | 8.10.0 / 2.0.21（随 `build.gradle`） | — |

版本矩阵（`build.gradle` ext）：
- compileSdk 36 / minSdk **26**（App 模块，因 fastjson2 需 API 26） / targetSdk 36
- dpt 模块 Java 11，App 模块 Java 17，shell 模块 Java 1.8
- CMake 3.31.1、ninja（**仅**桌面 `:shell` 或 `build` 需要，App 构建不需要）

依赖解析走阿里云镜像（google/central/public，见 `settings.gradle`）。

---

## 4. 快速构建（用户侧）

在仓库根目录执行（需已安装 JDK17 + Android SDK，`ANDROID_HOME` 指向 SDK）：

```bash
export ANDROID_HOME=/opt/android-sdk   # 按实际路径调整
chmod +x gradlew
./gradlew :app:assembleDebug --no-daemon
```

产物：`app/build/outputs/apk/debug/app-debug.apk`（已签名，可直接安装）。

若要全新验证壳资源一致性（可选，会触发 CMake/ninja 桌面构建）：

```bash
./gradlew :shell:assembleDebug --no-daemon   # 需要 NDK + CMake + ninja
```

> 推荐优先用一键 Actions 构建（第 5 节），避免本机配置差异。

---

## 5. 一键构建（GitHub Actions）

文件：`.github/workflows/build-apk.yml`

- 触发：`push`/`pull_request`（main）+ 手动 `workflow_dispatch`
- 环境：ubuntu-latest + JDK 17（temurin）+ `android-actions/setup-android@v3`
- 镜像策略（按技术栈）：
  - **Maven/依赖**：阿里云 `settings.gradle` 已内置 google/central/public 三个镜像源
  - **Gradle 发行版**：Tencent 镜像 `https://mirrors.cloud.tencent.com/gradle/`（workflow 内 `sed` 替换 wrapper URL）
- 构建命令：`./gradlew :app:assembleDebug --no-daemon`（不需要 NDK/CMake）
- 产物：上传 `app/build/outputs/apk/debug/*.apk` 到 Actions artifact

**GitHub 依赖拉取失败时的代理列表（12 个备选）**：当 GitHub raw/release/archive 或 `services.gradle.org` 拉取超时时，可对相应 URL 前置以下任一代理前缀重试（均为公开 GitHub 加速镜像，按可用性选用）：

```
https://ghproxy.com/
https://gh-proxy.com/
https://ghfast.top/
https://mirror.ghproxy.com/
https://ghproxy.net/
https://gh.api.99988866.xyz/
https://github.moeyy.xyz/
https://gitcode.net/ghproxy/
https://ghps.cc/
https://hub.fastgit.org/
https://raw.gitmirror.com/
https://cdn.jsdelivr.net/gh/
```

> 用法示例：`wget https://ghproxy.com/https://github.com/xxx/yyy.zip`
> 腾讯/阿里云镜像不可达时，Gradle 会自动回退到 `google()`/`mavenCentral()`（settings.gradle 中两者并列）。

---

## 6. dpt 源码 Android 兼容改造清单

以下改动均为**源码级兼容**，桌面端 `:dpt:compileJava` 仍通过（本机已验证 BUILD SUCCESSFUL）。

| 文件 | 改动 |
|---|---|
| `util/FileUtils.java` | 新增 `setExecutablePath/setUserDir` 注入；`getExecutablePath()` 改用 `FileUtils.class`（不再依赖 `Dpt`） |
| `util/DptBuildKey.java`（新增） | `getVersion()/getBuildKey()`：优先读 `shell-files/build-key`，回退 jar Manifest `Dpt-Build-Key` |
| `Dpt.java` | 委托 `DptBuildKey`；清理无用 import（含 `com.android.dx.command.dexer.Main`） |
| `config/Const.java` | 新增 `getRootOfOutDir/setRootOfOutDir` 注入；`AndroidPackage` 两处改用之 |
| `dex/ReflectionClinitInjector.java` | `Set.of/List.of` → `Collections.unmodifiableSet(new HashSet<>(Arrays.asList(...)))` / `Arrays.asList`（minSdk 21 兼容） |
| `util/ZipUtils.java` / `builder/AndroidPackage.java` / `dex/JunkCodeGenerator.java` | `java.nio.file` 全部移除 → `FileOutputStream`/`IoUtils.copyFile`/`IoUtils.writeFile`（minSdk 21 无 `Files` 相关 API） |
| `util/IoUtils.java` | 新增 `readLines(String)`（替代 guava `Files.readLines`） |
| `builder/AndroidPackage.java` | 移除对 `Dpt` 引用（`DptBuildKey` 替代）；新增 `setKeyStoreType/getKeyStoreType`（默认 "JKS"）与 `setDebugKeyStorePath/getDebugKeyStorePath`；`buildPackage()` 在注入路径存在时跳过 `assets/dpt.jks` 解压 |
| `util/LogUtils.java` | 新增 `setLogListener(LogListener)` 回调（App UI 实时日志） |
| `config/ShellConfig.java`、`util/DexUtils.java` | org.json `JSONException` 兼容（Android 自带 org.json 会抛检查异常，桌面 jar 不抛） |
| `util/DexUtils.java` | `ByteBuffer.wrap().position().get()` 链式调用改拆分（Android `Buffer.position` 返回 `Buffer`，JDK 返回 `ByteBuffer`） |
| `builder/Apk.java` | 签名改造：见第 7 节 |

---

## 7. APK 签名方案（重要）

**背景**：桌面端用 `ApkSignerTool.main()` 命令行签名，其失败路径会 `System.exit`（Android 上直接杀进程）；且 Android 无 JKS provider，必须用 PKCS12。

**方案**（`Apk.java:sign()`）：
1. `KeyStore.getInstance(getKeyStoreType())` 加载 keystore（App 注入 `"PKCS12"`）
2. `ApkSigner.SignerConfig.Builder("CERT", privateKey, certificates)` 构造签名配置
3. `new ApkSigner.Builder(...).setInputApk/setOutputApk/setV1+V2+V3SigningEnabled(true).build().sign()` 直签

**调试 keystore**（`app/.../DebugKeyStoreGenerator.kt`）：
- 运行时用 BouncyCastle 生成 2048-bit RSA 自签 PKCS12 keystore（alias `key0`、密码 `android`、100 年有效期）
- 路径注入：`AndroidPackage.setKeyStoreType("PKCS12")` + `setDebugKeyStorePath(filesDir/debug.p12)`
- `buildPackage()` 检测到注入路径即跳过 `dpt.jks` 解压，故 **App APK 不打包 dpt.jks**（已核验 APK 内无 jks）

**自定义 keystore**：App 不内置上传/导入 keystore 功能；如需正式签名，用户在 `shell-files` 解压目录自行替换后重新签名即可（本版本由调试密钥签名）。

---

## 8. 壳资源（shell-files）说明

来源：官方 v2.17.0 发行包 `shell-files/`（与本地 v2.17.0 源码匹配），已固化到 `app/src/main/assets/shell-files/`：

```
shell-files/
├── build-key                     # 6c2200133b1ff490
├── dex/classes.dex               # 壳 dex
└── libs/
    ├── arm/    lib<hex>.so
    ├── arm64/  lib<hex>.so
    ├── x86/    lib<hex>.so
    └── x86_64/ lib<hex>.so
```

App 运行 `ensureInitialized()` 时递归解压到 `filesDir/shell-files`，再 `FileUtils.setExecutablePath(filesDir)`。**so 文件名（`<hex>` 随机值）与 `build-key` 必须在加固流程内一致**——本版本统一使用发行包值 `6c2200133b1ff490`。

> 若用户后续重新生成壳（桌面 `./gradlew :shell:assembleRelease` 产出的 `shell-files`），需同步替换 App assets 下的这 6 个文件，并确保 so 名与 build-key 对应。

---

## 9. 源码打包（zip）

打源码 zip（**不删除任何文件**，仅打包排除项）：

```bash
cd /workspace && \
zip -r dpt-shell-app-源码.zip \
  app dpt shell gradle gradlew gradlew.bat settings.gradle build.gradle gradle.properties \
  .gitmodules README.md README.zh-CN.md LICENSE doc \
  .github/workflows/build-apk.yml HANDOVER.md \
  -x '*/build/*' '*/.gradle/*' '*/.kotlin/*' \
  -x '*/*.iml' '*/.idea/*' '*/.externalNativeBuild/*' '*/.cxx/*' \
  -x '*.jks' '*.keystore' '*.p12' '*.pem' '*.key' \
  -x '*/.env' '*/*secret*' '*/*token*' '*/*令牌*' \
  -x '*/local.properties' '-x */shell-files' \
  -x '*/assets/dpt.jks' -x '*/assets/*.jks' \
  -x '*/executable/*' \
  -x '*/* > 10MB'
```

> 说明：`*.zip` 用 `zip -x` 排除大于 10MB 文件不直观，建议打包时用 `find` 先过滤体积，或直接采用本机已验证目录（排除项已含 build/.gradle/.idea/iml/jks/keystore/p12/pem/key/.env/secrets/令牌/executable/shell-files 产物）。子模块（Dobby/bhook/mbedtls/minizip-ng）为 git 子模块引用，zip 内不含其内容，clone 后 `git submodule update --init --recursive` 即可。

---

## 10. App 使用流程

1. 安装 `app-debug.apk`，打开「dpt-shell 加固工具」
2. 点「选择 APK 文件」（SAF，系统文件选择器，可访问任意目录）
3. 点「开始加固」→ 后台线程执行 `Apk.Builder().filePath(...).outputPath(...).sign(true).build().protect()`
4. 日志实时上屏（`LogUtils.setLogListener` 回调）
5. 完成后点「分享加固后的 APK」（FileProvider，authority=`${applicationId}.fileprovider`，路径规则见 `res/xml/file_paths.xml`）
6. 收到的 APK 即为已签名加固产物，可直接安装

关键注入点（`MainActivity.ensureInitialized()`）：
- `FileUtils.setExecutablePath/setUserDir(filesDir)`
- `Const.setRootOfOutDir(cacheDir)`
- `AndroidPackage.setKeyStoreType("PKCS12")` + `setDebugKeyStorePath(filesDir/debug.p12)`
- `LogUtils.setLogListener { ... }`

---

## 11. 验证清单（本机已执行）

| 项 | 命令/方法 | 结果 |
|---|---|---|
| dpt 桌面编译 | `./gradlew :dpt:compileJava --no-daemon` | BUILD SUCCESSFUL |
| App 构建 | `./gradlew :app:assembleDebug --no-daemon` | BUILD SUCCESSFUL |
| APK 签名 | `/opt/android-sdk/build-tools/35.0.0/apksigner verify --verbose` | v2 通过，1 signer |
| Manifest | `aapt2 dump badging` | package/versionCode=1/versionName=2.17.0/targetSdk=36/launchable-activity 正确 |
| 壳资源入包 | `unzip -l app-debug.apk` | shell-files 6 文件 + 4 ABI so 均在 assets/ |
| dpt.jks 排除 | `unzip -l app-debug.apk` | 无 dpt.jks（正确） |
| 加固产物签名链路 | 源码走查 `buildPackage()` + `Apk.sign()` | 注入 PKCS12 路径生效 |
| 日志复制/清空 | `MainActivity.kt` `copyLogs()`/`logs.clear()` | 编译通过，UI 含复制/清空按钮 |
| RC4 key 全随机 | `KeyUtils.generateKey()` 去固定字节 | 编译通过，so 符号 `DPT_UNKNOWN_DATA` 覆写正常 |
| Junk 类名差异化 | 加固产物 `junkcode.dex` 类描述符 | 固定前缀 `com/luoye/dpt/junkcode/JunkClass`（native `JUNK_CLASS_FULL_NAME` 依赖，**不可随机前缀**）+ 数字后缀随机 + 随机 `()I` 字段方法；每次加固 90-99 个类 |
| 垃圾数据注入 | ~~`assets/<random>/<random>`~~ | **已回滚**（提交 7816a21） |
| 加固产物签名复核 | `apksigner verify --verbose` | v2/v3 通过，1 signer |

**崩溃根因（v2.17.0 加固产物 SIGSEGV pc=0）**：
- 现象：加固产物启动即崩，`signal 11, pc=0, lr=0`，单帧 `<unknown>`，arm64 + PAC 设备（Android 16）
- 根因：`JunkCodeGenerator` 随机前缀改动使 junk 类名不再含固定 `com/luoye/dpt/junkcode/JunkClass`，而 native `combineDexElements` 末尾 `junkCodeDexProtect()`（`dpt_risk.cpp:33`）用固定 `JUNK_CLASS_FULL_NAME`（`dpt_macro.h:33`）`FindClass` 返回 NULL → `dpt_crash()`（aarch64 `mov x30,#0` 后 ret 跳 0 地址）→ SIGSEGV pc=0
- 修复：`JunkCodeGenerator` 恢复固定前缀 + 保留数字后缀与随机字段方法，已本地加固验证 `Lcom/luoye/dpt/junkcode/JunkClass;` 存在、产物签名/结构正常
- 教训：**junk 类基名与 native 侧 `JUNK_CLASS_FULL_NAME`/`patchClass()` 硬编码耦合，加固侧不得随机化基名**；后续如需随机前缀需同步改 native 常量并重编 so

**用户侧设备端验证**（构建完成后必做）：
1. 安装 app-debug.apk 并启动
2. 选择一个带 `Application` 的普通 APK 加固
3. 安装加固产物，确认可正常启动、无崩溃、日志无 SO 加载失败
4. 用 apksigner verify 复核加固产物签名

---

## 12. 已知限制与待办

| 限制/待办 | 说明 |
|---|---|
| 签名 | 仅内置运行时生成的调试密钥；正式签名需用户自行处理（第 7 节） |
| minSdk | App 为 26（fastjson2 依赖 `MethodHandle`），低于 Android 8.0 设备不可装 |
| 大 APK | **已优化**：dex 处理串行化（ThreadPool 核心=1/最大=1）+ `largeHeap=true` + dex 流式复制 + 头部读取；本机 20MB 输入实测通过，100MB 需设备端验证（手机 heap 受 `largeHeap` 上限，仍可能 OOM 超大 APK） |
| 存储权限 | Android 11+ 需授予"所有文件访问"（MANAGE_EXTERNAL_STORAGE），App 启动即跳设置引导；输出保存到原 APK 同目录 |
| 未做真机冒烟 | 本环境无设备/模拟器，设备端行为（第 11 节第 5 步）待用户侧验证 |
| 混淆 | App 未启用 R8/minify（`minifyEnabled false`），体积较大但逻辑透明 |
| 需求 2 待办 | 代理类名/路径随机化增强未实施（需同步改 dex 重命名逻辑，风险高）；反调试 native 增强需重编译壳 so（用户选定 Java 侧随机，暂不做）；`LIB_DIR`/`ZIP_LIB_DIR` 固定目录未随机（native 与 Java 共用常量） |

---

## 13. Git 状态与推送

- 当前 HEAD：`326dea3`（fix: restore fixed junk class base name）→ 待提交本次大 APK/存储/日志改动
- 已推送：6 个提交（app 模块、dpt 兼容、CI workflow、镜像开关、随机化增强 80a89bb、回滚 7816a21、junk 基名修复 326dea3）
- Actions：run `30937599875` completed success，artifact `dpt-shell-app-debug-apk`
- remote：`https://github.com/Forinxy/dpt-shell`（push 需经 remote URL 临时注入 token）
- 本次待提交：ThreadPool 串行化+复用、DexUtils 内存优化、Manifest largeHeap+MANAGE_EXTERNAL_STORAGE、MainActivity 输出到原目录+清空日志、file_paths.xml
- Release：`v2.17.0` 已建，`app-debug.apk`（19MB）已 `gh release upload --clobber` 更新；**本次改动后需重新构建并覆盖上传**

---

## 14. 用户下一步

1. **构建新 APK**：本机 `./gradlew :app:assembleDebug` 或 GitHub Actions，重新产出修复版 app-debug.apk（含固定前缀 junk 类）
2. **设备端冒烟**：按第 11 节第 5 步加固一个测试 APK，确认产物可安装、可正常启动、无 SIGSEGV pc=0 崩溃
3. **（可选）正式签名**：按第 7 节替换正式 keystore
4. **（可选）重新生成壳**：如要换 so 名/build-key，按第 8 节重新同步 shell-files
5. **确认是否推送 git**：本次根因修复是否推送 remote 并重新上传 Release
