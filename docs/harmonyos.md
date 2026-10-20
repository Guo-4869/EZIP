# HarmonyOS 适配说明

结论先说：

- **HarmonyOS 2.x / 3.x / 4.x（安卓兼容版）**：不需要"专用版本"，现有 APK **就是** HarmonyOS 版本，直接装。
- **HarmonyOS NEXT（5.x，纯鸿蒙）**：APK 完全无法运行，必须用 ArkTS 重写一个 HAP 应用，属于"另做一个 App"。

---

## 1. 三类 HarmonyOS 与对应安装包

| 代际 | 底层 | 能否装 APK | 该装哪个 |
| --- | --- | --- | --- |
| HarmonyOS 2.0 / 2.1 | Android 10（API 29） | ✅ | **兼容版** `EZip-*-compat.apk`（targetSdk 29 + 旧版存储） |
| HarmonyOS 3.0 / 3.1 / 4.0 | Android 12（API 31）+ | ✅ | **正常版** `EZip-*.apk`，首次打开按提示开启「所有文件访问权限」 |
| HarmonyOS NEXT / 5.0 | 纯鸿蒙内核，无 Android 运行时 | ❌ | 需要重新开发的 HAP，见第 3 节 |

### 怎么判断自己是哪一类（10 秒）

1. 设置 → 关于手机 → 版本号：
   - `HarmonyOS 2.0.0` / `3.0.0` / `4.0.0` → 安卓兼容版，装 APK。
   - `HarmonyOS NEXT` 或 `HarmonyOS 5.x` → 纯鸿蒙，APK 装不上。
2. 或者更直接：把 `EZip-1.0.5.apk` 传到手机上点一下。
   - 能进安装流程 → 兼容版；
   - 提示"不兼容 / 无法解析安装包" → 纯鸿蒙。

---

## 2. 在 HarmonyOS 2/3/4 上的注意事项

- **权限入口不一样**：设置 → 应用和服务 → 应用管理 → EZip → 权限 →
  「所有文件访问权限」（部分机型在「特殊访问权限 / 更多权限」里）。
  找不到时，用 App 权限卡片上的「打开系统设置」按钮直接跳到本应用详情页。
- **App 内诊断行**会显示识别到的系统版本、应用目标 SDK、以及存储根目录是否可读，排查时截图即可。
- 兼容版 / 正常版**包名相同**，可以互相覆盖安装；同一个版本号不会重复占空间。
- HarmonyOS 的"纯净模式"会拦截部分侧载安装：安装时若被拦截，需在
  设置 → 系统和更新 → 纯净模式 中临时关闭，或在"应用管控"里放行。

---

## 3. HarmonyOS NEXT 为什么要重写

| 方面 | 现状（Android 版） | 纯鸿蒙（NEXT） |
| --- | --- | --- |
| 打包格式 | APK（aapt2 + d8 + apksigner） | HAP（DevEco Studio + hvigor） |
| 语言/UI | Java + 平台控件 | ArkTS + ArkUI（声明式） |
| 运行时 | Android Runtime | 方舟运行时（不兼容 APK/Dex） |
| 全盘文件访问 | `MANAGE_EXTERNAL_STORAGE` | **没有对应权限**，只能走系统文件选择器（DocumentViewPicker）+ 应用沙箱 |
| 压缩库 | zip4j / junrar / commons-compress | 自带 `@ohos.zlib`（仅 zip）；RAR/7z 需自行移植 C 库 |
| 签名/安装 | 自签名 APK 直接装 | 需华为开发者账号签名；真机调试要开开发者模式 |

具体到压缩能力：

- `@ohos.zlib`：SDK 内置，支持 **zip 的压缩/解压（含目录）**，但不支持 AES 加密。
- **RAR / RAR5 / 7z / AES zip**：SDK 都没有，必须把 unrar、7-Zip SDK 等 C 代码
  通过 Native API（napi）编译进 HAP。这是移植的主要成本与风险来源。
- 预览（图片/文本/PDF/视频）在 NEXT 上都有对应原生组件，工作量中等。

---

## 4. 如果决定移植：分阶段建议

| 阶段 | 内容 | 预估 |
| --- | --- | --- |
| 一 | 文件浏览（选择器 + 沙箱）、zip 压缩/解压、图片/文本预览 | 1–2 周 |
| 二 | RAR / RAR5 / 7z 解压（native 移植）、AES zip | 2–4 周，稳定性风险较高 |
| 三 | PDF / 视频 / Office 文本预览、批量选择、进度与取消 | 1–2 周 |

> 如果需求只是"解压别人发来的压缩包"，HarmonyOS NEXT 的「文件管理」自带 zip 解压，
> 日常够用；专门做 App 的价值主要在于 RAR/7z 和批量操作。

---

## 5. 能力边界（必须提前说清）

- ✅ 可以：产出完整的 ArkTS 工程源码（页面、状态管理、zlib 封装、native 接口定义与调用代码）。
- ❌ 不可以：本机没有 DevEco Studio / HarmonyOS SDK / 华为开发者账号，
  **无法编译 HAP、无法签名、无法装机验证**。和 iOS 的情况一样，只能"给源码 + 你在本地构建"。

如果你有 DevEco Studio 环境，把构建报错贴回来，我可以按报错迭代；没有环境的话，
建议先用现有 APK（若是 2/3/4）或系统文件管理器（若是 NEXT）。
