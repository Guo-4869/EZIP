# EZip — Android 上的免费压缩/解压工具

一个**免费、无广告、无需联网、源码开放的（非商业许可）** Android 压缩解压 App，界面参考了 [morluto/rea](https://github.com/morluto/rea) 与 [storytold/photocraft](https://github.com/storytold/photocraft)
那种「干净、单一职责、可读源码」的做法：全部源码可读、可改，像电脑上的 7-Zip / WinRAR 一样直接浏览手机文件、
压缩、解压、预览。

- 源码：本仓库根目录（Windows 上可一键构建，见第 4 节）
- HarmonyOS 适配说明：[docs/harmonyos.md](docs/harmonyos.md)（HarmonyOS 2/3/4 直接用现有 APK；HarmonyOS NEXT 需重写）
- 安全审计：[docs/security.md](docs/security.md)　隐私政策：[docs/privacy.md](docs/privacy.md)
- 历史版本：`dist\releases\<版本号>\` + `CHANGELOG.md`（每个版本的 SHA256 记录在 `dist\releases\manifest.csv`）
- 可直接安装的 APK：`dist\EZip-1.0.10.apk`（Android 11+）/ `dist\EZip-1.0.10-compat.apk`（Android 10 及以下），已签名，各约 1.1 MB
- 许可：[LICENSE](LICENSE) — 个人/学习/非商业可自由使用；**商用须经作者书面同意**

---

## 1. 功能

**解压**：ZIP（含密码 / AES-256 / 分卷 .z01）、RAR（RAR4 + RAR5，含密码、固实压缩包）、7Z（含密码）、
TAR、TAR.GZ / TGZ、TAR.BZ2 / TBZ2、TAR.XZ / TXZ、GZ、BZ2、XZ。

**压缩**：ZIP（可选 AES-256 加密、3 档压缩级别）、7Z（可选 AES-256 加密、3 档压缩级别）、TAR / TAR.GZ / TAR.BZ2 / TAR.XZ（TAR 本身不支持加密）。
RAR 只能解压不能创建 —— 这是 RAR 授权条款决定的，所有免费工具（含 7-Zip）都如此。

**预览**（压缩包内文件也能直接点开看）：

| 类型 | 支持 | 说明 |
| --- | --- | --- |
| 图片 | JPG / PNG / GIF / BMP / WebP / HEIC 等 | 双指缩放、双击放大、拖动 |
| 文本 | TXT / MD / JSON / XML / CSV / LOG / 代码 | 自动识别 UTF-8 / GBK，可选中复制 |
| PDF | 全部 | 逐页渲染、翻页、缩放 |
| 视频 | MP4 / MKV / AVI / MOV / 3GP … | 内置播放器 + 进度条 |
| 音频 | MP3 / WAV / FLAC / AAC / M4A … | 内置播放 + 拖动进度 |
| Word / PPT / Excel | docx / pptx / xlsx | 提取文字与逐页备注预览（不渲染排版） |
| 压缩包 | 全部支持格式 | 打开查看内部文件列表 |

**文件管理**：多选、全选、新建文件夹、重命名、删除、排序（名称 / 大小 / 时间）、**按名称递归搜索**、**返回时保持滚动位置**、显示隐藏文件、
「用其他应用打开」（通过内置 ContentProvider 输出 `content://`，不会触发 Android 的 FileUri 限制）。

---

## 2. 安装（Windows → 手机）

> 本仓库**不提交编译产物**（`dist/`、`keystore/` 均在 `.gitignore` 中）。
> 想直接安装请在 GitHub **Releases** 下载 APK，或用第 4 节的方法自行构建。

### 2.1 两个 APK 选哪个

| 文件 | targetSdk | 存储模型 | 适用 |
| --- | --- | --- | --- |
| `dist\EZip-1.0.10.apk` | 34 | 「所有文件访问权限」（Android 11+ 手动开启） | **Android 11 / 12 / 13 / 14 首选** |
| `dist\EZip-1.0.10-compat.apk` | 29 | 旧版存储（运行时授予「存储」权限） | **Android 10 及以下必装**；11+ 上装不了/开不了「所有文件访问权限」的机型也用它 |

> 为什么需要兼容版：`MANAGE_EXTERNAL_STORAGE`（所有文件访问权限）是 **Android 11（API 30）才引入**的。
> 在 Android 10 上，targetSdk ≥ 30 的 App 会被强制「分区存储」，除了自己私有目录
> （`Android/data/com.ezip.app`）之外什么都读不到——这正是「只能看到 app 私有目录」的原因。
> 兼容版把 targetSdk 降到 29 并启用 `requestLegacyExternalStorage`，配合运行时「存储」权限，
> 在 Android 10 / 11 / 12 / 13 上都走旧版全盘访问。两个 APK 包名相同，可互相覆盖安装。

### 2.2 步骤

1. 按上表把对应 APK 传到手机（数据线 / 微信文件传输 / 网盘均可）。
2. 点开安装，允许「安装未知来源应用」。
3. 打开 App：
   - 正常版：点「去开启「所有文件访问权限」」→ 在系统设置里打开开关 → 返回 App 自动生效。
     部分国产 ROM 的路径是「设置 → 应用 → 特殊权限 → 所有文件访问」；打不开时用第二个按钮「打开系统设置」。
   - 兼容版：弹出系统权限框时选「允许」（存储）。
4. 授予成功后会自动跳到「下载」目录，此时能浏览 `内部存储` 下所有文件夹。

权限卡片里会显示诊断信息（系统版本 / 应用目标 SDK / 存储根目录是否可读），
排查时把它截图即可。未授权时 App 仍可用，但只能在私有目录里操作。

也可以在有 adb 的电脑上直接装：

```powershell
adb install -r dist\EZip-1.0.10.apk
# 或
adb install -r dist\EZip-1.0.10-compat.apk
```

系统要求：**Android 8.0（API 26）及以上**，支持 arm64 / armv7 / x86（纯 Java + Dex，无 native 库）。

---

## 3. 目录结构

```
EZip/
├─ app/src/main/
│  ├─ AndroidManifest.xml
│  ├─ java/com/ezip/app/
│  │  ├─ MainActivity.java          文件浏览 / 多选 / 压缩 / 解压 / 删除 / 重命名
│  │  ├─ ArchiveActivity.java       查看压缩包内容、单文件解压预览、全部解压
│  │  ├─ PreviewActivity.java       图片 / 文本 / PDF / 视频 / 音频 / Office 预览
│  │  ├─ archive/                   压缩解压引擎（纯 Java，无 android.* 依赖）
│  │  │  ├─ ArchiveService.java     解压 + 列表 + 单条目读取（zip/rar/7z/tar/gz/bz2/xz）
│  │  │  ├─ Compressor.java         创建 ZIP / 7Z（AES-256、压缩级别、进度、可取消）
│  │  │  ├─ ArchiveKind.java        扩展名 → 格式识别
│  │  │  ├─ ProgressSink.java       字节级进度回调
│  │  │  ├─ CancelToken.java        协作式取消
│  │  │  ├─ OperationCancelledException.java
│  │  │  └─ ArchiveEntryInfo.java
│  │  ├─ provider/EzFileProvider.java  自己实现的 FileProvider（不依赖 AndroidX）
│  │  ├─ ui/                       适配器、对话框、进度对话框、缩放图片控件、工具类
│  │  └─ util/                        文件工具与纯 Java 逻辑（可在 PC 上测）
│  │     ├─ FileX.java                文件工具、编码识别、安全路径
│  │     ├─ FileSearch.java           按名称递归搜索（无 Android 依赖）
│  │     └─ Ooxml.java                docx/pptx/xlsx 文本提取
│  └─ res/                         布局 / 矢量图标 / 颜色 / 中文文案
├─ libs/                           第三方 jar（随仓库自带，离线可编）
├─ build.ps1                       一键构建 APK（不依赖 Gradle）
├─ CHANGELOG.md                    版本记录（1.0.2 起每个版本都归档）
├─ tools/                          构建辅助 + 引擎测试
│  ├─ fetch.js                     下载缺失的 Android SDK 组件 / jar（用 Node，便于国内网络）
│  ├─ gen-res.ps1                  生成资源 XML 的一次性脚手架
│  ├─ run-engine-test.ps1          在 PC 上跑压缩引擎测试（真档案往返校验）
│  ├─ archive-release.ps1          把当前 dist 的 APK 归档到 dist\releases\<版本>
│  ├─ BenchDir.java                目录加载新旧实现对比基准
│  ├─ check-ids.ps1                校验 Java 里用到的 R.id 都在对应布局中存在
│  ├─ check-paths.ps1              校验矢量图 pathData 语法（避免运行时 InflateException）
│  ├─ check-adapters.ps1           校验 ArrayAdapter/布局根节点类型（避免运行时 ClassCastException）
│  ├─ EngineTest.java              引擎测试用例（123 项断言）
│  └─ testdata/                    RAR4/RAR5/固实/加密 测试样本（来自 junrar 的测试集）
├─ dist/                          构建产物（EZip-<版本>.apk / -compat）+ releases/ 历史版本归档
```

---

## 4. 构建（Windows）

### 4.1 需要什么

| 依赖 | 说明 |
| --- | --- |
| JDK 17+ | 用于 `javac`、`d8`、`apksigner`。本机用的是 PyCharm 自带的 JBR 21，脚本会自动找到 |
| Android SDK build-tools **≥ 35** | 34.0.0 里的 d8（8.2.2）处理 junrar / commons-lang3 时会内部报错，35+ 正常 |
| Android platform android-34 | 编译用 `android.jar` |
| Node.js（仅首次） | 用于下载缺失的 SDK 组件 |

如果本机没有 SDK，执行一次：

```powershell
node tools\fetch.js
```

它会把 `build-tools 36.1.0` + `platform android-34` + 全部 jar 下载到
`%USERPROFILE%\.ezip-toolchain`（约 110 MB），之后完全离线。

### 4.2 一条命令出包

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1
```

脚本会依次执行（每步失败即停机并打印原因）：

```
aapt2 compile  →  aapt2 link(生成 R.java)  →  javac  →  d8  →  打包 classes.dex
→  zipalign -p 4  →  apksigner sign(v2+v3)  →  apksigner verify
```

产物：`dist\EZip-<version>.apk`，并打印 SHA256。
签名密钥由脚本首次运行时用 `keytool` 生成在 `keystore\ezip-release.jks`
（口令 `ezip2024`，别名 `ezip`）。**正式发布请换成你自己的密钥并妥善保管。**

可选参数：

```powershell
-File build.ps1 -VersionName 1.1.0 -VersionCode 3 -MinSdk 26 -TargetSdk 34 `
                -Jdk "C:\jdk-21" -Sdk "C:\Android\Sdk"
```

**同时出两个 APK**（正常版 + 兼容版）：

```powershell
# 正常版：targetSdk 34，用「所有文件访问权限」
powershell -ExecutionPolicy Bypass -File build.ps1
# 兼容版：targetSdk 29，用旧版存储 + 运行时「存储」权限
powershell -ExecutionPolicy Bypass -File build.ps1 -TargetSdk 29 -OutSuffix "-compat"
```

两次构建产物分别是 `dist\EZip-1.0.10.apk` 与 `dist\EZip-1.0.10-compat.apk`。
代码里没有编译期开关：App 运行时读自己的 `targetSdkVersion` 自动切换权限模型
（`<=29` 走运行时存储权限，`>=30` 走 `MANAGE_EXTERNAL_STORAGE`），所以同一套源码编出两个版本即可。

### 4.3 验证

```powershell
# 压缩引擎（解压/压缩往返、AES、RAR5、固实 RAR、中文 GBK 文件名、路径穿越防护、取消）
powershell -ExecutionPolicy Bypass -File tools\run-engine-test.ps1
# 期望最后一行：ENGINE TEST OK（123 项全过）

# 布局 ID 静态校验（防止 findViewById 拿到 null）
powershell -ExecutionPolicy Bypass -File tools\check-ids.ps1
# 期望：ID CHECK OK

# 矢量图标 pathData 语法校验（防止启动时 InflateException）
powershell -ExecutionPolicy Bypass -File tools\check-paths.ps1
# 期望：PATH CHECK OK

# 适配器/布局类型校验（ArrayAdapter 与自定义布局根节点不匹配等运行时崩溃）
powershell -ExecutionPolicy Bypass -File tools\check-adapters.ps1
# 期望：ADAPTER CHECK OK
```

---

## 5. 关键实现说明

### 5.1 中文文件名（GBK）

Windows 上的 WinRAR / 360压缩 打的 zip 往往**不写 UTF-8 标志位**，里面是 GBK 字节。
zip4j 会把这种名字按 CP437 解出 `╓╨╬─` 这类制表符。EZip 的做法：
先按默认编码读文件头，若发现替换字符 / CP437 制表符 / C1 控制符，
就用 `setCharset(GBK)` 重新打开一次，中文名即恢复。这样既兼容新版 zip，也兼容老压缩包。

### 5.2 路径穿越防护

解压时每个条目都经过 `FileX.safeResolve()`：去掉绝对路径前缀、盘符，再取 `getCanonicalPath()`
校验必须落在目标目录内，否则报「压缩包包含非法路径」。
测试里用 junrar 的 `mkdir-escape.rar` / `parent-dir.rar` 做了真实攻击样本验证。

### 5.3 进度与取消

- 解压：自己控制读写循环，按字节上报进度，每 64 KB 检查一次取消标志。
- 压缩 ZIP：zip4j 单文件压缩期间 UI 线程无法感知进度，因此开了一个看门狗线程轮询
  `ProgressMonitor`（150 ms），并用 `setCancelAllTasks(true)` 实现「单个大文件也能取消」。
- 压缩 7Z：写循环里逐块检查。
- 取消后自动删除写了一半的输出文件，不留垃圾。

### 5.4 为什么不用 Gradle / AndroidX

`aapt2 + javac + d8 + apksigner` 足以产出完整 APK，构建链短、离线可复现、体积小（1.1 MB）。
代价是不能用 RecyclerView / Compose / Material 组件，因此界面用平台控件 + 自绘矢量图标实现，
交互全部手写（多选、底部操作栏、圆角对话框、Sheet 菜单、缩放图片控件）。

### 5.5 权限

`MANAGE_EXTERNAL_STORAGE` 在 Google Play 上属于敏感权限（文件管理器 / 压缩软件类目允许申请，
但上架需说明）。本项目面向侧载（自己装、发朋友），所以直接使用；
若不授予，App 会退化为只能在 `Android/data/com.ezip.app/files` 下工作，不会崩溃。

该权限是 **Android 11（API 30）才有的**。Android 10 及更低版本上，targetSdk ≥ 30 的 App
会被强制分区存储，所以必须用 targetSdk 29 的兼容版（`requestLegacyExternalStorage` + 运行时「存储」权限）。
两版共用一套源码：运行时的 `targetSdkVersion` 决定用哪种权限模型，见 `MainActivity.hasStorageAccess()`。

---

## 6. 已知限制

1. **不能创建 RAR**（RAR 规范未开放创建），只能解压。
2. 加密 RAR / 7Z 的**文件名加密**（-hp 头加密）：RAR5 头加密可读（junrar 支持），
   但部分 7z「加密文件名」档案需要密码才能列出列表，EZip 会提示输入密码。
3. 多卷 RAR（`.part1.rar`）需要同目录下所有分卷齐全。
4. docx / pptx / xlsx 只做**纯文本提取**，不渲染表格、图片与排版。
5. 视频/音频预览依赖系统解码器：不支持的编码（如手机不支持的 MKV 编码）会提示
   「无法预览此文件」，可用「用其他应用打开」交给第三方播放器。
6. 单文件超大（如 > 4 GB 的视频）时，解压进度按字节推进，界面刷新有节流，不影响正确性。
7. **Android 10 及以下必须装兼容版 APK**；正常版在这些系统上只能访问私有目录。
   兼容版依赖旧版存储模型，未来 Android 版本若彻底移除该模型，仅正常版可用。

---

## 7. 二次开发提示

- 新增一种压缩格式：在 `ArchiveKind` 加枚举与扩展名，在 `ArchiveService.extract/list` 加分支，
  在 `tools/EngineTest.java` 加一条往返用例（引擎层没有 android 依赖，PC 上就能测）。
- 改界面：布局在 `res/layout`，颜色 / 圆角在 `res/values/colors.xml` 与 `res/drawable`。
- 改完跑一次 `build.ps1` 即可，不需要 Gradle 同步。

---

## 8. 第三方组件

| 组件 | 用途 | 许可 |
| --- | --- | --- |
| [zip4j](https://github.com/srikanth-lingala/zip4j) 2.11.5 | ZIP 读写 / AES-256 | Apache-2.0 |
| [junrar](https://github.com/junrar/junrar) 8.1.2 | RAR4 / RAR5 解压 | Apache-2.0 |
| [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/) 1.26.2 | 7Z / TAR / GZ / BZ2 | Apache-2.0 |
| [XZ for Java](https://tukaani.org/xz/java.html) 1.9 | XZ 解压 | Public Domain |
| [SLF4J](https://www.slf4j.org/) 2.0.17 | junrar 日志门面（nop 实现） | MIT |

`tools/testdata` 中的 RAR 样本取自 junrar 测试集（Apache-2.0），仅用于 PC 端引擎测试，不会打进 APK。

---

## 9. 常见问题

**打开后只能看到 `Android/data/com.ezip.app`，其他文件夹都进不去？**
说明存储权限没生效，按下面顺序排查：

1. 看权限卡片上的诊断文字：
   - 「模式：所有文件访问」→ 去系统设置打开「所有文件访问权限」（正常版）；
   - 「模式：兼容版（旧版存储）」→ 在弹窗里允许「存储」权限；
   - 「模式：系统版本过低」→ 你的系统是 Android 10 或更低，**必须装 `EZip-1.0.10-compat.apk`**。
2. 国产 ROM 找不到「所有文件访问」开关时，用卡片上的「打开系统设置」直接跳到本应用详情页，
   或手动走：设置 → 应用 → 应用管理 → EZip → 权限 → 所有文件访问权限。
3. 授予完一定要**返回 App**（会自动重新检测并跳到「下载」目录）。

**第一次打开只看到「需要文件访问权限」的卡片？**
点卡片上的主按钮，按系统提示授予即可；授权后本页会自动刷新。

**解压到当前目录时提示「目标文件夹非空，同名文件将被覆盖」？**
这是保护性提示。想保留原文件就先取消，改用「解压到…」选一个新文件夹（默认会给出压缩包同名的新目录）。

**某个 RAR 打不开？**
RAR4 / RAR5 都支持；多卷 `.part1.rar` 需要全部分卷在同一目录；「-hp 头加密」档案要先输对密码才能列出文件。
个别使用 RAR 新滤镜的档案仍可能失败，可用电脑解压。

**闪退了怎么排查？**
手机开启 USB 调试，连电脑后抓日志：

```
adb logcat -s AndroidRuntime:E
```

把 `AndroidRuntime` 那段堆栈发出来即可定位。

**想换图标 / 改名 / 改包名？**
图标改 `res/mipmap-anydpi-v26/ic_launcher.xml` 与 `res/drawable/ic_launcher_foreground.xml`；
应用名改 `res/values/strings.xml` 的 `app_name`，重新执行 `build.ps1` 即可。
---

## 10. 界面规范（参考 android-native-dev skill）

界面按 \skill/skills-main/skills/android-native-dev\ 的建议核对过，落地的部分：

| 项目 | 处理 |
| --- | --- |
| 深色模式 | \es/values-night/colors.xml\ 覆盖 bg/surface/text/divider/primary_pale/field_bg/row_pressed；Java 里不再写死白色 |
| 触控目标 | 工具栏、返回上级、翻页、播放按钮统一 48dp（skill 要求 ≥48dp） |
| 无障碍 | 交互图标都有 \contentDescription\；装饰性类型角标 \importantForAccessibility="no"\，避免读屏念「PDF、文件名…」 |
| 对比度 | 正文用 \#101828\ on \#FFFFFF\（约 15:1），次要文字 \#667085\（约 4.6:1） |
| 间距 | 主要间距 16/12/8dp，卡片圆角 14–18dp |

未采纳：Gradle / Compose / AndroidX / 测试框架章节——本工程是刻意的手工构建链
（\apt2 + javac + d8\），不引入 Gradle 与 AndroidX；换来的好处是离线可构建、APK 1.07 MB。

---

## 11. 许可

本项目**不是** OSI 意义上的开源许可，而是「源码可见 + 非商业」：

| 用途 | 是否允许 |
| --- | --- |
| 个人学习、研究、自用 | ✅ 无需授权 |
| 非商业项目使用 / 修改 / 分发（保留版权与许可声明） | ✅ 无需授权 |
| 教学、技术分享中引用代码片段 | ✅ 无需授权 |
| 任何**商业用途**（销售、内购、订阅、随硬件捆绑、商业组织内部经营使用、收费服务等） | ❌ **须经作者书面同意** |

- 完整条款见 [LICENSE](LICENSE)（中英双语，以中文为准）
- 商业授权：通过本仓库 Issues 联系作者
- 第三方库不受本协议约束：`libs/` 下的 zip4j / junrar / commons-compress / xz / slf4j
  仍遵循各自许可证（Apache-2.0 / MIT / Public Domain），分发时须保留其声明