# Luna v1.0.5 UI / 动效解包分析

对上传的 `Luna_v1.0.5_release.apk`（`com.mt.luna`）做的一次解包逆向。目的是找出**可以迁到本项目**的 UI 与动效做法。

分析手段：APK 解包 → jadx 反编译 → 内嵌 AGSL shader 提取 → `resources.arsc` 结构扫描 → 第三方库（Backdrop / kyant shapes）反编译对照。

---

## 0. 一句话结论

它和我们是**同一个玻璃设计语言的两代实现**：同样基于 Kyant 的 **Backdrop** 库，但它的玻璃材质体系比本项目完整一个量级——**它把「玻璃」拆成了 20 个可组合的参数对象**（材质/模糊/折射/描边/高光/阴影/内阴影/反射/混合…），并给每个控件配了具名的预设；而本项目是「一个 GlassSurface + 几个固定数值」。

另外，它在**手势上做得比本项目宽**：工具栏横滑切标签、下拉刷新、长按 logo 开全局菜单、长按标签按钮、网页双击、长按菜单按钮——本项目只有「地址栏收起 + 边缘滑动前进后退」两项。

---

## 1. 技术栈对照

| | Luna v1.0.5 | 本项目 |
|---|---|---|
| 包名 | `com.mt.luna` | `com.liuli.browser` |
| 形态 | Compose Multiplatform（KMP，含 `top.yukonga.miuix.kmp` 依赖） | 纯 Android + Compose |
| 玻璃库 | **Backdrop**（`io.github.kyant0:backdrop`）+ **Miuix** 玻璃扩展 | Backdrop 2.0.1 |
| UI 组件库 | Miuix（`miuix-ui`） | 自建 `ui/liquid/` |
| 架构 | Hilt + Room + WorkManager + DataStore | 单 Activity + 手写 State |
| 功能面 | adblock / autofill / 阅读器 / 用户脚本 / 下载 / 播放 / 翻译 / 标签组 / Agent | Agent + 护盾 + 书签 |

> 它把 **Miuix** 拉进来当基础组件库，同时又用 Backdrop 做玻璃——Miuix 自带的 `MiuixGlassColorBlend` 与 Backdrop 是两套玻璃实现并存的痕迹。

---

## 2. 最有价值的发现：玻璃材质参数体系

它把「液态玻璃」拆成了一组**可组合的参数对象**，每个都有具名默认值。这是本项目最该借鉴的部分。

从 DEX 字符串表里提取出的完整默认参数：

```
GlassMaterial(blurRadius=…)                    // 材质总入口
GlassBlur(small=36.0, big=500.0)               // 双层模糊：小模糊 + 大模糊
GlassRefract(ior=3.0)                          // 折射率
GlassEdge(width=60.0, pow=4.0, thickness=80.0, reflectOffset=600.0)   // 边缘光带
GlassLight(directionX=-0.4, directionY=0.6, directionZ=-0.8,
           intensity=1.4, oppositeIntensity=0.8, angleRange=1.4)         // 双光源
GlassReflect(lighten=1.0, strength=0.8)         // 反射提亮
GlassInner(bottom=0.04, tint=…)                 // 内阴影（只压底边）
GlassShadow(color=…)                            // 外阴影
GlassStroke(width=…) / GlassStrokeLight(x=…)    // 描边 + 描边高光
GlassBlend(curveA=0.5, curveB=1.0, curveC=0.0, curveD=1.0,
           amount=0.2, saturation=2.0, brightness=0.2,
           darker=0.0, darkerStart=0.6, darkerEnd=0.8)                  // 色彩混合曲线
GlassBackground(saturation=0.0, brightness=0.0, burn=0.0, unShade=0.0) // 背景调制
GlassShape(topStart=…)                          // 每角独立圆角
GlassStyle(blend=…)
GlassColorLayer(color=…)
```

**它还把这套东西做成了 popup 的专用变体**——这正是本项目最缺的部分：

```
GlassPopupAnchorElement(anchor=…)   // 锚定到触发元素
GlassPopupFrame(rect=…)              // 弹窗框体
GlassPopupSizing(minWidth=…)         // 尺寸策略
GlassPopupVisuals(style=…)           // 视觉样式
GlassAnchorSurface(backdrop=…)       // 锚定表面
GlassTopAppBarContext(backdrop=…)    // 顶栏玻璃上下文
```

配套的具名状态（都能在 DEX 里查到）：

```
glassPopupAnchorValue       // 弹窗锚点
glassPopupItemPress         // 弹窗项按压
glassPopupScrollIndicator   // 弹窗滚动指示器
glassSecondaryPopup         // 次级弹窗
glassTransformPopup         // 变换型弹窗
glassTransformMask          // 变换遮罩
glassTransformStacked       // 堆叠变换
glassCascadeVisibility      // 层叠显隐
glassSegmentedTabContent    // 分段控件内容
glassSmallTopBarFloat       // 小顶栏浮动
glassTopBarFloat            // 顶栏浮动
glassTopBarButtonFloat      // 顶栏按钮浮动
glassTopBarButtonPress      // 顶栏按钮按压
```

### 对照本项目现状

本项目 `components/Glass.kt` 的 `GlassSurface` 是**单一入口 + 固定数值**：

```kotlin
GlassSurface(
    backdrop, shape, tint, blurRadius = 18.dp, lensHeight = 16.dp,
    lensAmount = 24.dp, shadow = true, highlight = true,
    edgeStroke = true, innerShadow = false, …
)
```

只有 `blur / lens / vibrancy` 三个效果，**没有描边高光、没有边缘光带、没有反射、没有色彩混合曲线**。而它的 `GlassEdge(width, pow, thickness, reflectOffset)` 与 `GlassStrokeLight` 恰恰是「玻璃看起来像玻璃」的关键——本项目是靠 `glassBorder` 一圈等宽描边，边缘是平的。

**可迁移的三件事**：

1. **双光源边缘光**（`GlassLight` 的两盏对向灯 + `GlassEdge` 的光带宽度）取代等宽描边，玻璃才有「厚度」。
2. **描边高光**（`GlassStrokeLight`）——描边不是一圈同色，而是一段被光源打亮的高光。
3. **`GlassBlend` 的色彩混合曲线**（`curveA/B/C/D` + `darker/darkerStart/darkerEnd`）——玻璃压暗时不是线性乘，是分段曲线。

### 内嵌 shader 是完整的（可直接抄）

APK 里带了 **21 个 AGSL shader 源码**（在 `classes.dex` 的字符串表里，是 Backdrop 库内置的）。其中三个直接对应上面三个参数：

- `shader_00` — 边缘光带：`in_rimEdge` / `in_lightDir` / `in_lightAmt` / `in_surface`
- `shader_02` — 描边 + 双光源描边高光：`in_strokeBand` / `in_strokeColor` / `in_light1` / `in_light2`
- `shader_03` — 阴影：`in_shadowOffset` / `in_shadowShape` / `in_shadowColor`

`shader_00` 里有一段值得注意的实现细节（**本项目的 `drawShapeOutline` 完全没有这个**）：

```glsl
// 形状的 SDF：圆角矩形 + 超椭圆（连续圆角）插值
float sdfShape(float2 p, float2 b, float r) {
    float box = roundedBoxSdf(p, b, r);
    float minHalf = min(b.x, b.y);
    if (in_smoothing <= 0.001 || r < 0.5 || (minHalf - r) <= 1.0) return box;
    float tile = min(1.528665 * r, minHalf);
    return mix(box, supercircleSdf(p - b, tile), in_smoothing);
}
```

`in_smoothing` 是**连续圆角**（Apple 风格「squircle」）的插值系数，0 = 正圆角，1 = 超椭圆。Backdrop 的 `lens` 用的是 `getCornerRadii`，只支持正圆角与绝对圆角——但**kyant 的 `shapes` 库本身就内置了连续圆角**（`RoundedCornerStyle.Continuous`），不必自己写 SDF。

**高光 shader 与本项目完全一致**（逐字节相同），确认两边同源：

```glsl
uniform float2 size;  layout(color) uniform half4 color;
uniform float radius; uniform float2 position;
half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}
```

---

## 3. 手势体系：它比本项目宽得多

它的设置项文案直接暴露了完整的手势矩阵（这些是「关键词 + 描述 + 同义词」的搜索索引格式，能看出它给每个功能做了关键词覆盖）：

| 设置项 | 关键词 |
|---|---|
| 快捷手势 | 手势 长按 滑动 下滑 |
| 滑动工具栏切换标签页 | 滑动 工具栏 地址栏 左右 切换 标签页 |
| 网页下拉刷新 | 下拉刷新 刷新 网页 |
| 长按菜单按钮打开页面菜单 | 长按 菜单 按钮 页面 功能菜单 仅在网页内 |
| 长按标签按钮 | 标签按钮 标签页 新建 隐身 退出 菜单 |
| 长按 logo | 标志 图标 操作 动作 打开/关闭/新建对话/搜索/书签/下载/历史/离线页面/播放记录/AI 产物 |
| 长按网页 / 双击网页元素 | 长按 / 双击 |
| 地址栏位置 | 顶部 底部 分离 导航栏 |
| 顶栏 | 紧凑模式 单顶栏 标签栏 工具栏 |
| 退出 | 确认 返回 再按一次 退出前确认 |
| 标签页 | 标签页行为 新标签 关闭 反馈 |

**「长按 logo」那一项特别值得学**：长按浏览器图标弹出一个全局动作面板（新建对话 / 上次对话 / 搜索 / 书签 / 下载 / 历史 / 离线页面 / 播放记录 / AI 产物）。这是把「所有功能塞进一个入口」的做法——本项目的首页有一排固定入口，图标位有限，这个方案不占常驻空间。

**「地址栏位置：顶部/底部/分离/导航栏」** 说明它把浏览器最个人化的那条轴做成了可选项，并且有「分离」这一档（本项目只有顶部一条）。

### 本项目的手势现状

| 手势 | 本项目 | 它 |
|---|---|---|
| 边缘滑动前进/后退 | ✓ | 未见（它有工具栏横滑切标签） |
| 工具栏横滑切标签 | ✗ | ✓ |
| 网页下拉刷新 | ✗ | ✓ |
| 长按 logo 全局菜单 | ✗ | ✓ |
| 长按标签按钮 | ✗ | ✓ |
| 网页长按 / 双击 | ✗ | ✓ |
| 菜单下拉甩掉 | ✓ | — |
| 地址栏收起 | ✓ | — |

---

## 4. 界面结构上的差异

### 起始页有四种样式

```
起始页 主页 新标签页 快捷主页 经典主页 主页样式 主页风格 布局 空白页 自定义
```

- **空白页**（blank）：只留地址栏
- **快捷主页** / **经典主页**：两套预设布局
- **自定义**：用户自己排

本项目只有一种：品牌球 + 搜索框 + 常用网站 + 继续浏览 + 隐私卡片。

### 顶栏有三种形态

```
顶栏 紧凑模式 单顶栏 标签栏 工具栏
```

「单顶栏 / 标签栏 / 工具栏」三种形态切换——本项目是固定「工具栏 + 底部 Dock」。

### 主题系统支持多套并存

```
主题名称 / 新建主题 / 关闭主题？ / 标签页与主题 / 允许延伸网站颜色
```

「允许延伸网站颜色（地址与工具栏颜色跟随网页主题色）」这一项是**网页注入**的方向——本项目的护盾已经拦了跟踪参数，但没做「跟随站点配色」。

### 标签组

```
标签组 / 在你的标签组之间移动标签页 / 给标签页组重新命名 / 文件夹长按菜单
```

本项目没有分组概念。

---

## 5. Agent 部分的差异（可参考）

它的 Agent 面板文案密度远高于本项目（1309 条中文串里相当大一部分是 Agent 相关）：

- **上下文预算管理**：`上下文：约 N` / `会话累计缓存读：` / `会话累计：输入 N` / `压缩：已完成` / `压缩：正在压缩中…` — 有**自动压缩**机制
- **子 Agent**：`个子 Agent` / `只读子Agent：搜索并阅读工作区，不修改任何文件。`
- **MCP 接入**：`MCP 连接：` / `无路由到任何服务商或模型。` / `的路由不完整。`
- **技能系统**：`技能：` / `Skills`（assets 里也有 `skills/` 目录）
- **后台任务**：`后台任务：` / `个定时任务：`
- **模型能力矩阵**：`思考等级：` / `计划模式：` / `权限模式：` / `模型：` / `状态：`
- **分支与失败**：`· 分叉` / `· 执行失败` / `· 缓存读` / `· 至` / `· 输出`

**「思考等级」「计划模式」「权限模式」三个正交维度**是本项目完全没有的概念——本项目只有 `AgentMode.Local/Cloud` 一个二元维度。

**「上下文压缩」**尤其值得注意：它有自动压缩（`压缩：已完成`），本项目完全没有——长对话会一直涨。

---

## 6. 建议迁移的清单（按性价比排序）

### 高：玻璃边缘光（`GlassEdge` + `GlassLight` + `GlassStrokeLight`）
本项目现在是「一圈等宽描边」，玻璃边缘是平的。加上对向双光源的光带后，边缘才有厚度和方向感。三个 shader 源码就在 APK 里，可以直接对照实现。

**已落地**：`ui/liquid/GlassEdge.kt`。实现走 Backdrop 的 `HighlightStyle`（公开接口，带 `createShader()` 钩子），`Highlight` 本来就沿玻璃轮廓描边并按形状裁剪，把着色器换掉即可，不必自建裁剪与混合管线。着色器自绘圆角矩形的 SDF，用梯度当表面法线，分别按主光/副光方向加权，边缘因此有方向感。

### 高：弹窗玻璃体系（`GlassPopup*` 六件套）
本项目的菜单/对话框是统一 `LiquidDialog` + 纯色遮罩，没有「锚定到触发元素」的玻璃浮层。它的 `GlassPopupAnchorElement` / `GlassPopupFrame` / `GlassPopupVisuals` 是一套完整的浮层玻璃方案——菜单从按钮处「长出来」比从屏幕中央升起更贴手。

**已落地**：`ui/components/GlassPopup.kt`。**不能用 `androidx.compose.ui.window.Popup`**——它开独立窗口，Backdrop 的折射采样同一 Activity 窗口里录制的 `LayerBackdrop`，独立窗口采不到任何东西，玻璃会变成一块死板的半透明色块。浮层改为同窗口按窗口绝对坐标定位。

### 中：工具栏横滑切标签
本项目只做了边缘滑动前进/后退。横滑切标签是浏览器的第二高频手势，两者的手势区域可以共存（左右边缘 vs 中部）。

**已落地**：地址栏玻璃件上横滑切标签。**地址栏上的横滑优先于边缘手势**——地址栏横跨整个宽度，两端与边缘手势区重叠，而用户在意地址栏上横滑时意图明确是切标签，边缘手势在那一带本来就容易被误触；边缘手势那条路径改为把落在地址栏里的起手让开，地址栏以外照常可用。

跟手位移**直接写状态**，不用 `Animatable.snapTo()`（挂起函数，一次横滑上百个指针事件、上百个协程互相打断，地址栏会明显滞后于手指）；松手后的归位才交给弹簧。

### 中：Agent 的三个正交维度（思考等级 / 计划模式 / 权限模式）
把本项目的 `AgentMode.Local/Cloud` 扩成三维，用户能更精确地控制 Agent 行为粒度。

**已落地**：见 `docs/AI-Agent-design.md` 的「三个正交维度」一节。权限模式里有一条不可退让的红线——**表单提交与页面写入永远不能被自动执行**，任何权限模式下都必须用户显式确认。

### 中：长按 logo 全局菜单
把首页那一排固定入口收进一个长按面板，换取首屏更干净，且不限制入口数量。

**已落地**：长按首页品牌球弹出锚定型玻璃浮层（标签页 / 书签与历史 / 设置）。轻按不做任何事——球只是标记，抢走它的单击会让人误以为它能点。

### 中：地址栏位置可配置（顶部/底部/分离）
最个人化的一条轴。本项目地址栏恒在顶部。

**已落地**：三档都用「位置比例量 + 绝对定位」实现，换挡时位置连续，不会瞬移。

### 低：起始页四种样式
「空白页」这一档对本项目尤其有价值——纯地址栏起步，配合长按 logo 菜单，功能都藏在手势后面。

**已落地**：四种样式共用一套布局，按「留什么」拆成四个布尔，而不是抄四遍布局：
- 品牌（全都要）/ 经典（去隐私承诺卡）/ 快捷（只剩常用网站，网格更密）/ 空白（只剩地址栏）。

### 低：连续圆角（squircle）
`in_smoothing` 支持的。

**已落地**：结论与当初的判断相反——**不必自己写 SDF**。kyant 的 `shapes` 1.2.1 已内置连续圆角：`RoundedCornerStyle.Continuous` + `RoundedRectangle(radius, style)`，四角同半径，且实现了 Backdrop 优先识别的 `RoundedRectangularShape` 接口，所以折射效果照常工作。封装在 `ui/liquid/GlassShape.kt`：

- `GlassShape.squircle(radius)`：连续圆角矩形。
- `GlassShape.capsule()`：胶囊（正圆角）。
- `GlassShape.uneven(...)`：每角独立圆角（聊天气泡那种"尾巴收小一档"的形状）。

胶囊保持正圆不改：它的两端本来就是半圆——已经是曲率最连续的样子了，再套连续圆角只会把端头压扁，让它既不像胶囊也不像圆。

全项目已替换完毕：`RoundedCornerShape` 在 `app/src/main/java` 下只剩 `GlassShape.kt` 的注释里出现过一次。

---

## 6.1 落地过程中修正的三个判断

解包时基于常量池得出的结论，有三条在拿到真实库之后需要修正：

**一、Backdrop 2.0.1 没有独立的描边/边缘光效果。**
`GlassEdge` / `GlassLight` / `GlassStrokeLight` 属于 Luna 自己的 Miuix 玻璃层。但 Backdrop 的 `HighlightStyle` 是公开接口，带 `createShader(shape, runtimeShaderCache)` 钩子，而 `Highlight` 本来就沿形状描边并按形状裁剪——把着色器换掉就能得到边缘光，不需要自建管线。`GlassEdge.kt` 走的就是这条路。

**二、Backdrop 自带的取角半径只认 Compose 的 `CornerBasedShape`。**
碰上 kyant 连续圆角矩形会一律当成胶囊。本项目另写了 `Shape.edgeCornerRadii()`：先认 `RoundedRectangularShape`，再认 `CornerBasedShape`，最后按胶囊处理。

**三、`getCornerRadii` 不支持超椭圆，但连续圆角不需要它支持。**
`squircle` 的四角半径仍然是四个标量，折射着色器照样能用；「曲率连续」这件事由形状自身（`RoundedRectangle` 的路径）承担，折射只关心半径。

---

## 6.2 自查中修掉的十个缺陷

落地之后又逐处过了一遍，动的地方都能编译、但**行为是错的**。这一节记下来，因为它们的共同点是：都不影响编译，只在真机上才看得出来。

**一、`GlassPopup` 的展开动画永远不会开始（死锁）。**
面板要先量出尺寸才知道落在哪儿，而原实现是「尺寸算出来才组合面板」——面板量不到自己，尺寸永远算不出来，展开动画也就永远不启动，长按菜单完全打不开。改成：**锚点一登记上就先组合面板**（量尺寸那一帧是透明的，用户看不见），落位则先用临时位置，尺寸到手后再算真位置。

**二、边缘手势让开地址栏的位置算错了。**
原先用 `(容器宽 - 地址栏宽) / 2` 推算地址栏的横向范围，但地址栏那一列是居中摆放且两侧有内边距，平板上还会被 1000dp 的上限截断再居中，推出来的左边距与真实位置对不上，边缘手势在错的地方让开。改成**实测窗口坐标相减**：根容器记自己的窗口原点，地址栏记自己的窗口矩形，相减即得容器内偏移。

**三、收起后的小把手落到了导航栏里。**
`handlePillBottom` 是把手**底边**的绝对纵坐标，却被当成了「距容器底的距离」用来取负——底部档时它会落到屏幕下缘之外。统一口径：把手用 `align(BottomCenter)` 贴底，再用绝对纵坐标减去容器高，得到该往上挪的距离。

**四、能力条会把权限悄悄升到「低风险自动」。**
三个维度都是「点一下换到下一档」，但权限是三者里唯一带安全含义的一档：一次随手的点击就把 Agent 的自主度调高了。现在**放宽权限要用户点头**——点「低风险自动」先在能力条下方弹一条确认条，确认后才生效；收紧（切回只读 / 逐次确认）不需要确认，把 Agent 关回去不可能出事。

**五、思考档与计划档的高亮挂错了维度。**
「思考」那一格的高亮原本依赖**权限模式**，改权限会让思考档看起来也被改过。现在每格只高亮自己偏离默认档的状态（思考 ≠ 中、计划 ≠ 自动、权限 ≠ 逐次确认）。

**六、「空白」起始页名不副实。**
那一档的卖点是「首屏只剩地址栏，功能全藏在手势后面」，但右上角还杵着书签与设置两个按钮，而长按品牌球弹出的全局菜单根本无从发现。现在空白档收起右上角按钮，并在品牌球**下方**补一句「长按打开菜单」。

提示放在球的下方，但这一列的宽度是定死的：「长按打开菜单」比品牌球宽得多，列宽若由文字决定，球会被推到中间去，与下方搜索框的左缘错开。

提示只出现在空白档——那一档的入口在屏幕上完全看不见，长按球能唤出菜单就得说出来。其余三档的入口本来就摆在明处（右上角的按钮、底栏、侧栏），菜单只是快捷方式，为它常年挂一行提示是 clutter。

附带修掉的一处层级冲突：底栏画在 `BrowserRoot` 里、比首页内容更上层，全局菜单的遮罩盖住了整屏却盖不住底栏，点底栏还会直接跳走。菜单展开时底栏现在会退场。

浮层也因此从 `HomeScreen` 移到了 `BrowserRoot` 的根层：画在首页里的话，遮罩只盖得住内容列，平板上的侧栏仍亮着还能点，一点就切了屏。移上去之后遮罩铺满整屏，底栏、提示与侧栏一并被接管。

**七、浮层的缩放原点会在展开时跳一下。**
缩放原点原先写死在落位结果里，而落位要等面板量到尺寸才算得出来——量尺寸那一帧用的是临时落位，原点也是临时值，等真落位算好，原点一跳，面板看着像是"弹"出来的。改成**绘制时按实际量到的尺寸实时算**：锚点与面板相接的那一点，始终是缩放的原点。

**八、退场动画期间返回键会穿透到下面的页面。**
浮层关闭时 `visible` 立刻变 false，返回键的处理随之交还给页面——可面板还在淡出，用户明明只想关掉菜单，却把整页也退掉了。改成与 `LiquidDialog` 同一套守卫：退场期间继续吞掉返回键。

**九、确认条出现后再点「权限」格毫无变化。**
下一档是从生效中的那一档算起的，所以待确认时再点一下，算出来的还是同一档，看着像这格坏了。改成**再点一下就是撤销这次请求**——用户想撤销时，不该被塞给他一个自己没点的档位。

**十、确认条把面板挤变形了。**
说明文字与两个按钮挤在同一行，窄屏上文字被压成三四行，整条确认条比消息本身还高。改成文字一行、按钮另起一行靠右；文字也改用次要正文——10sp 的角标字号读一整句话太吃力，而这句话是安全边界说明。

---

## 7. 本次分析的边界

- **看不到源码结构**：jadx 只能给出反编译后的 Java，函数组织、状态管理、重组策略一概不知；R8 把绝大多数类名混淆成 `a`/`b`/`c`，能确认存在但看不到实现。
- 所有数值来自 `toString()` 常量池，是**默认值**；运行时是否被覆盖无从得知。
- 只有 `com.mt.luna.*` 顶层类与 `SettingsRoute` 保留了原名，说明**该项目的 Kotlin 源码本身大量使用单字母类名**（`ui/settings/a.kt`…`e.kt`），这本身也是一个信号：它的 UI 层被有意拆碎以压缩体积。
- 本文 §6 描述的落地结果以**本项目**的实现为准，与 Luna 的实际效果可能有出入——它的玻璃层是 Miuix 的，我们用的是 Backdrop，两者的边缘光与连续圆角实现路径并不相同。
