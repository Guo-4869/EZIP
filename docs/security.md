# 安全与隐私审计（EZip 1.0.7）

> 本文是**可复核**的审计记录：每条结论都注明依据（代码位置 / APK 实测命令 / 测试用例）。
> 最后两节明确写了"没有验证到的部分"——不夸大。

审计对象：`dist\EZip-1.0.7.apk`（versionCode 8，minSdk 26，targetSdk 34，v2+v3 签名）。

---

## 1. 一句话结论

这个 App **不联网、不收集个人信息、没有账号和统计**，所有数据只在本机处理；
归档解析（路径穿越、超大文件、损坏文件）和 UI 线程都有对应防护，并有 112 项自动化测试覆盖核心逻辑。
但**没有任何真机/模拟器运行测试**，所以"永不崩溃"这种承诺我给不了——第 7 节列了剩余风险。

---

## 2. 权限清单（APK 实测）

```powershell
aapt2 dump badging dist\EZip-1.0.7.apk | findstr uses-permission
```

| 权限 | 用途 | 能否不授予 | 拒绝后的行为 |
| --- | --- | --- | --- |
| `MANAGE_EXTERNAL_STORAGE` | 浏览任意目录、读写任意压缩包（Android 11+ 的「所有文件访问权限」） | 能 | 只能在应用私有目录工作，界面给出提示 |
| `READ_EXTERNAL_STORAGE` | 兼容版（targetSdk 29）读取存储 | 能 | 同上 |
| `WRITE_EXTERNAL_STORAGE` | 兼容版写入存储 | 能 | 解压/压缩会报错 |
| `READ_MEDIA_IMAGES` / `VIDEO` / `AUDIO` | 兼容版在 Android 13+ 上读取媒体 | 能 | 仅影响对应类型文件 |

**没有**：`INTERNET`、`ACCESS_NETWORK_STATE`、定位、通讯录、短信、相机、麦克风、电话状态、
`QUERY_ALL_PACKAGES`、`SYSTEM_ALERT_WINDOW`、`REQUEST_INSTALL_PACKAGES`。
→ 从系统层面**不可能**把任何数据上传出去，也不可能读取通讯录/短信/位置。

## 3. 数据流（隐私核心）

```
用户选择的文件/压缩包  →  本机内存/磁盘  →  用户指定的目标目录
        ↑                                          ↓
   没有任何网络出口（无 INTERNET 权限），没有账号体系，没有统计 SDK
```

- **不采集**：设备标识、IMEI、Mac、位置、账号、使用行为、崩溃堆栈。
- **不持久化密码**：解压/压缩密码只在 `String` 里存活于本次操作，不写文件、不写日志、不上传；
  App 无任何日志上报（`android.util.Log` 只在本地可用，且本项目基本未使用）。
- **临时文件**：预览用的解压缓存放在 `getCacheDir()/preview`，系统可随时清理。
- **退出即清**：卸载 App = 删除 `Android/data/com.ezip.app` 下全部内容（`allowBackup=false`，
  不会进云备份）。

## 4. 组件暴露面（APK 实测）

```powershell
aapt2 dump xmltree dist\EZip-1.0.7.apk --file AndroidManifest.xml
```

| 组件 | exported | 说明 |
| --- | --- | --- |
| `MainActivity` | true | 仅 `MAIN/LAUNCHER`，**不消费任何外部 Intent**（没有 VIEW / SEND / 自定义 scheme），外部应用无法通过 Intent 驱动它 |
| `ArchiveActivity` | false | 不可被外部启动 |
| `PreviewActivity` | false | 不可被外部启动 |
| `EzFileProvider` | false | 只能通过本 App 授权的 `content://` URI 访问 |

其它实测项：`allowBackup=false`、**无 `debuggable`**、`grantUriPermissions=true`（按 URI 粒度授权）。

**Provider 加固（1.0.7 新增）**：`isAllowed()` 只允许共享
「公共存储根目录 / 应用私有目录 / 应用缓存目录」下的文件，即使 URI 被泄露也只能读到这几处的文件；
并且只授予 **读** 权限（`FLAG_GRANT_READ_URI_PERMISSION`），Provider 本身也只实现 `openFile`，
`insert/delete/update` 直接抛异常。

## 5. 不可信输入（压缩包）的防护

| 攻击 | 防护 | 验证方式 |
| --- | --- | --- |
| **路径穿越**（`../../etc/passwd`、绝对路径、`C:\`） | `FileX.safeResolve()` 去掉前缀后取 `getCanonicalPath()`，必须落在目标目录内，否则抛「压缩包包含非法路径」 | 引擎测试用 junrar 的真实攻击样本 `mkdir-escape.rar`、`parent-dir.rar`；另有 4 项合成用例 |
| **符号链接**（tar/rar/7z 里的 symlink 条目） | 只按普通文件写入（0 字节），**不创建任何软链接**，避免跳出目标目录 | 代码路径：目录条目走 `ensureDir`，其余一律 `FileOutputStream` |
| **解压炸弹 / 撑爆存储**（1.0.7 新增） | 边写边检查：每写入 64 MB 调 `checkFreeSpace()`，剩余空间 < 32 MB 立刻中止并提示「存储空间不足」 | 代码位置 `ArchiveService.checkFreeSpace()`；zip/7z/tar/rar/单流 5 条写入路径全覆盖 |
| **损坏 / 畸形压缩包** | 所有解析异常统一包装为 `IOException`，由 UI 转成中文提示，不向上抛 | 引擎测试覆盖错误密码、截断、异常头（junrar 的 abnormal 样本） |
| **中文乱码诱导误判** | 多字符集候选（UTF-8 → GBK → Cp437），避免把 GBK 字节按 UTF-8 解成乱码路径 | 3 项测试（AES/GBK/混合场景） |
| **超大文件** | 解压流式写盘（不整读入内存）；预览文本限 512 KB；图片按 2048px 降采样并捕获 `OutOfMemoryError` | 代码位置 `PreviewActivity.decodeSampledBitmap()`、`MAX_TEXT_BYTES` |

## 6. 依赖与已知漏洞（CVE）

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| zip4j | **2.11.6** | 1.0.7 从 2.11.5 升到这个补丁版，零新增依赖 |
| junrar | 8.1.2 | 最新版，RAR4/RAR5 解析 |
| commons-compress | 1.26.2 | 含 CVE-2024-25710（无限循环）与 CVE-2024-26308（Pack200 OOM）修复，两者均在 1.26.0 已修 |
| xz (tukaani) | **1.12** | 1.0.7 从 1.9 升级，零新增依赖 |
| slf4j-api / nop | 2.0.17 | 仅日志门面，nop 实现 |

**为什么没升 commons-compress 1.28.0**：它强制依赖 commons-io 2.20 + commons-lang3 3.18 +
**新增 commons-codec**，等于引入 4 个未在真机验证过的依赖。为了一个"稳定不崩"的目标，
保持已验证组合更合理；升级路径已记录在 `libs/`（jar 版本号可直接替换，跑一遍
`tools\run-engine-test.ps1` 即可回归）。
注意：本项目**没有网络输入**，压缩包都来自用户本机，CVE 的实际暴露面远小于服务端场景。

## 7. 崩溃风险矩阵（哪些已缓解，哪些仍需真机验证）

| 风险 | 缓解 | 状态 |
| --- | --- | --- |
| 布局/资源错误导致启动崩溃 | `aapt2 link` 编译期校验 + `tools\check-ids.ps1`（R.id 与布局一致）+ `check-paths.ps1`（24 条矢量 pathData 合法性） | ✅ 已静态验证 |
| `findViewById` 返回 null | 同上 ID 校验 | ✅ |
| 后台线程异常导致进程崩溃 | `TaskRunner` 与搜索线程都 `catch (Throwable)`，转成提示框 | ✅ 1.0.7 加固 |
| OOM（超大文本/图片） | 文本 512 KB 上限、图片降采样、`OutOfMemoryError` 捕获 | ✅ 已缓解 |
| 存储写满导致系统异常 | 解压时剩余空间监控 | ✅ 1.0.7 新增 |
| 对话框在 Activity 销毁后弹出 | `TaskRunner` 检查 `isFinishing()/isDestroyed()`；三个 Activity 都声明 `configChanges` 避免旋转重建 | ✅ |
| 加密包内预览失败 | 1.0.4 起：探测加密 → 弹密码 → 错误重试 | ✅ 有测试 |
| UI 线程卡顿（大目录列举） | 目录列举在 UI 线程但只做一层；搜索在后台线程 | ⚠️ 超大目录（数万项）仍可能掉帧，未实测 |
| 真机兼容性（厂商 ROM 权限差异） | 权限卡片显示诊断信息、多重设置入口兜底 | ⚠️ 需真机确认 |
| 无自动化 UI 测试、无 fuzz | — | ❌ 未覆盖（见下） |

## 8. 隐私合规对照

| 要求 | 本项目做法 |
| --- | --- |
| 最小必要原则（《个人信息保护法》第 6 条） | 只申请"读写存储"这一类与功能直接相关的权限；不申请网络/定位/通讯录等 |
| 告知同意 | 首次启动显示权限卡片说明用途；「更多 → 隐私说明」随时可查；每次系统权限弹窗即二次确认 |
| 不出售/不共享个人信息 | 无网络权限，无第三方 SDK，技术上无法共享 |
| 数据本地化/可删除 | 全部数据在用户手机上；卸载即删除；无云端副本（`allowBackup=false`） |
| 应用市场敏感权限（Google Play `MANAGE_EXTERNAL_STORAGE`） | 属于"文件管理/压缩"类目允许的用途；上架需在后台填写用途声明并附演示视频。本项目定位为**侧载**，不提交 Play |
| 国内应用市场（华为/小米等） | 需提交隐私政策文本（见 `docs/privacy.md`）+ 权限用途说明；`MANAGE_EXTERNAL_STORAGE` 属敏感权限，审核可能要求人工说明 |

## 9. 没有验证到的部分（必须说清）

1. **没有在真机/模拟器上运行过**：本机没有 Android 设备、没有 adb、也没有可用的模拟器镜像。
   所有 UI 行为（点击、弹窗、权限流程）都是静态核对 + 代码审查，不是实测。
2. **没有 fuzz 测试**：压缩包解析器只用了若干真实样本和构造样本，未做随机变异测试。
3. **没有第三方安全审计**：本文是开发方自查。
4. **未做逆向/加固**：APK 未做混淆与加壳，源码可反编译——作为开源项目这是设计选择，不是缺陷。

如果你的诉求是"上架应用商店"，还需要补：真机回归测试、隐私政策落地页、以及各市场要求的
权限说明材料。需要的话我可以把 `docs/privacy.md` 改成对应市场要求的格式。
