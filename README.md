# Luna Browser / 琉璃浏览器

一款以液态玻璃（Liquid Glass）为视觉语言、使用系统 Chromium WebView 的 Android 浏览器。

![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF)
![Compose](https://img.shields.io/badge/Compose-1.12-4285F4)
![minSdk](https://img.shields.io/badge/minSdk-26-3DDC84)
![targetSdk](https://img.shields.io/badge/targetSdk-37-3DDC84)

## 特性

- 液态玻璃界面：基于 [AndroidLiquidGlass / Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) 的 `drawBackdrop`、模糊、折射与色散效果。
- 仓库同款交互组件：`LiquidButton`、`InteractiveHighlight`、`DampedDragAnimation`、`LiquidToggle`、`LiquidSlider`、`LiquidBottomTabs`。
- Chromium 内核：使用 Android 系统 WebView，不打包 Chromium 运行时，安装包约 2.3 MB。
- 一镜到底：`SharedTransitionLayout` + `AnimatedContent`，搜索框与标签卡片使用共享元素/共享容器过渡。
- 统一动效规格层：弹簧曲线、时长档位与缓动收敛到一份契约（`ui/theme/Motion.kt`），所有页面共用；「减少动态效果」开关全局生效，任何动效都能降级。
- 设计 token 体系：圆角、间距、字号各自收敛为固定档位（`ui/theme/LiuliTheme.kt` 的 `Radius` / `Space` / `Type`），全 App 视觉语言一致。
- 按压反馈全覆盖：玻璃卡片、列表行、按钮、标签页都获得液态按压回弹，拖动类控件（开关、滑块、底部 Dock）1:1 跟手并带液态过冲。
- 触感反馈：按下的那一刻就有震动确认（与按压形变同步），开关、滑块跨档、标签切换、关闭标签页与结果提示都有反馈。
- 手势：地址栏收起为一行、左右边缘滑动前进/后退（带速度判定），底部菜单可下拉甩掉。
- 共享组件：空状态、破坏性确认框、页头、玻璃表面各有唯一实现，三个页面不会再各写一套长得不一样的东西。
- 多标签浏览：新建/关闭/切换标签、标签缩略图、书签与历史、下载、桌面版网站、全屏视频。
- 平板与折叠屏：响应式侧栏、多列标签、可调整窗口、横竖屏。
- 隐私护盾：本机拦截追踪器、去除跟踪参数、阻止第三方 Cookie、通用 User-Agent、拒绝敏感网页权限、退出即清除数据。
- 滚动时自动收起顶栏/底栏；可在设置中关闭。
- AI Agent（1.0.9）：页面速览、页内查找、关键词问答；单步滚动/点击/填写逐条确认；多步任务先生成计划，批准后按顺序执行，支持暂停、继续、重试与取消；只读多页调研并生成带引用报告，可导出 Markdown、HTML、PDF 与来源 CSV；创作与表达：基于当前页素材做写作、改写、大纲与分享卡片（可导出 Markdown/HTML/纯文本、文本分享与本机渲染的 PNG 卡片）；页面关注（只存正文指纹与价格片段）、宏录制回放（仅滚动与点击）、本机浏览日报与历史检索；只读页面审计；分享此页与语音朗读回复。等待时面板会显示当前阶段（读取页面 / 云端对话 / 生成计划 / 页面交互等）与已等待秒数，并可随时「停止本次请求」而不必清空整段对话。关注、宏、审计与历史均在本机前台完成，无后台任务、无通知、不上传音频；密码与文件字段拒绝，云端推理需手动开启并在发送前脱敏。设计文档：[docs/AI-Agent-design.md](docs/AI-Agent-design.md)。

## 技术栈

- Kotlin 2.4 / Jetpack Compose 1.12
- AGP 9.3.2 / Gradle 9.7.1
- `io.github.kyant0:backdrop` 2.0.1、`io.github.kyant0:shapes` 1.2.1
- Android System WebView（Chromium）

## 构建

环境要求：

- JDK 17
- Android SDK Platform 37、Build-Tools 37.0.0
- Gradle 9.7.1

```bash
./gradlew :app:assembleDebug
```

Release 构建需要在项目根目录创建 `keystore.properties`（不要提交到仓库）：

```properties
storeFile=your-release.jks
storePassword=******
keyAlias=your-alias
keyPassword=******
```

然后：

```bash
./gradlew :app:assembleRelease
```

产物位于 `app/build/outputs/apk/release/app-release.apk`。

## 项目结构

```
app/src/main/java/com/liuli/browser
├── data/          SettingsStore、LibraryStore
├── ui/
│   ├── BrowserRoot.kt       响应式根布局、共享元素转场、Toast 队列
│   ├── agent/               AgentPanel（AI 助手面板）、AgentShareCard（分享卡渲染）
│   ├── liquid/              LiquidButton/Toggle/Slider/Dock、InteractiveHighlight、
│   │                        DampedDragAnimation、pressScale、Visibility
│   ├── components/          Glass（玻璃表面）、Glyph（图标）、Navigation（侧栏）、
│   │                        EmptyState、DangerConfirmDialog、PageHeader、
│   │                        AuroraBackground、PrivacyPolicy、LiquidDialog
│   ├── screens/             Home/Browser/TabSwitcher/Library/Settings/PrivacyGate
│   └── theme/               配色、字体、尺寸、Motion 动效规格层、Radius/Space/Type token
└── web/                     BrowserViewModel、WebView 配置、隐私护盾、快照
    └── agent/               AgentController、AgentResearch、AgentCreation、
                             AgentAutomation、AgentPageBridge、AgentPolicyStore
```

`tools/sandbox-build.sh` 是沙箱环境的构建入口：它负责装好 Gradle 发行包与 Android SDK、写入代理与镜像配置，再以适配沙箱内存上限的参数调用 Gradle（项目 `gradle.properties` 里的 `-Xmx4096m` 会超过沙箱的 cgroup 上限，daemon 会被 OOM killer 干掉）。

## 隐私说明

- 仅申请 `INTERNET` 权限，不申请存储、定位、相机、麦克风、通讯录等权限。
- 网页的定位/相机/麦克风请求默认拒绝。
- 默认阻止第三方 Cookie，禁用自动填充与文件访问。
- 隐私护盾开启时声明 `no-referrer`，避免把当前站点与完整 URL 通过 Referer 头泄露给第三方。
- 退出应用时默认清除 Cookie、缓存、历史、站点存储与标签会话（书签保留）。
- 所有拦截与过滤在本机完成，不上传访问记录或设备标识。

## 说明

- 本项目使用系统 WebView 作为 Chromium 内核，内核版本由系统/WebView 应用决定。
- 界面设计参考 AndroidLiquidGlass（Apache-2.0）与 Backdrop（Apache-2.0）。
- 开源许可证待定。
