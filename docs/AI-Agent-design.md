# 琉璃浏览器 AI Agent 设计方案

版本：v0.1（只做设计，不实现）
目标：让 AI 在用户明确授权下“看懂当前网页并代为操作”，同时保持浏览器级隐私与可控性。

## 1. 产品定位

- 不是聊天侧边栏，而是**可接管浏览器的 Agent**：能读页面、点链接、填表单、切换标签、总结与对比。
- 默认只读；任何有副作用的操作必须经过风险策略与用户确认。
- 隐私优先：默认不把网页内容上传云端；可选择本地模型或按任务授权的云端模型。

### 非目标（首版）

- 不自动登录账号、不读取密码、不读取剪贴板。
- 不自动支付、不自动提交订单、不自动下载可执行文件。
- 不做后台常驻自动任务；任务结束即停止。
- 不跨设备同步浏览数据。

## 2. 用户体验

### 入口

- 地址栏右侧的液态玻璃 Agent 球；点击后从球体一镜到底展开为 Agent 面板。
- 手机：底部玻璃面板，占屏幕约 45%，可上拉全屏。
- 平板/折叠屏：右侧玻璃侧栏，与网页并排；横屏可调整宽度。
- 长按 Agent 球进入“只读问答”，单击进入“协助操作”。

### 三种模式

| 模式 | 能力 | 默认策略 |
| --- | --- | --- |
| 问答 | 总结、翻译、解释、查找 | 只读，可不上传正文 |
| 协助 | 点击、滚动、填表 | 每步确认，可对低风险步骤“本次允许” |
| 托管 | 多步任务，如“对比三个商品价格” | 先确认计划，再按域策略逐步执行 |

### 面板内容

- 计划卡：目标、步骤、当前步骤、状态（等待确认/执行中/完成/失败）。
- “接管”按钮：用户触摸网页时 Agent 自动暂停，面板显示“你已接管”。
- 审计记录：本机保存，退出时随隐私护盾清除。
- 停止开关：一键终止任务并撤销待执行动作。

## 3. 总体架构

```
┌──────────────────────────────────────────┐
│ Compose Agent UI（玻璃面板 / 计划卡 / 授权） │
└──────────────────┬───────────────────────┘
                   │ AgentEvent / AgentCommand
┌──────────────────▼───────────────────────┐
│ AgentOrchestrator（前台，Activity 作用域）   │
│  · 任务状态机 · 步数/超时/取消 · 重试        │
│  · Plan → Validate → Act → Verify → Report │
└───────┬───────────────┬──────────────────┘
        │               │
┌───────▼──────┐  ┌─────▼──────────────────┐
│ PolicyEngine │  │ ToolExecutor            │
│ 域策略/风险   │  │ WebViewTools / Browser  │
│ 注入防护/批准 │  │ Tabs / Bookmarks / Share │
└───────┬──────┘  └─────┬──────────────────┘
        │               │
┌───────▼──────┐  ┌─────▼──────────────────┐
│ LlmGateway   │  │ PageBridge（JS + A11y）  │
│ 云端/本地模型 │  │ 只读快照 · 元素引用 · 动作 │
└──────────────┘  └────────────────────────┘
```

### 3.1 PageBridge：网页感知与操作

- 仅在 Agent 会话期间向当前 WebView 注入版本化的 JS SDK，会话结束立即移除监听。
- 感知产物是**结构化快照**，不是整页 HTML：
  - 页面标题、URL、语言、可见文本摘要。
  - 可交互元素列表：`ref`（会话内随机 ID）、角色、可见文本、是否可点击/可输入、是否在视口。
  - 表单字段：仅字段名与类型；值默认脱敏，密码字段永不读取。
  - 视口与滚动位置；可选截图仅用于本地多模态模型。
- 元素定位使用 `data-liuli-agent-ref` 临时属性，不把 CSS 选择器或 XPath 暴露给模型，防止页面构造恶意选择器。
- 动作通过 `evaluateJavascript` 执行，所有参数由 Kotlin 侧校验；不使用可被页面伪造的全局回调。
- 页面导航、DOM 变更、下载、弹窗等事件通过 WebViewClient/ChromeClient 回传 Orchestrator，用于验证动作结果。

### 3.2 感知快照示例（发给模型的裁剪版）

```json
{
  "url": "https://example.com/product/123",
  "title": "商品详情",
  "viewport": { "y": 0, "height": 2200 },
  "text": "……已脱敏的可见正文……",
  "elements": [
    { "ref": "e12", "role": "link", "name": "加入购物车", "visible": true },
    { "ref": "e13", "role": "textbox", "name": "收货地址", "value": "[REDACTED]" }
  ]
}
```

### 3.3 Agent 循环

1. **Observe**：抓取快照（必要时滚动/切换标签后再抓取）。
2. **Plan**：模型输出结构化计划（步骤 + 工具 + 参数），不能输出任意脚本。
3. **Validate**：PolicyEngine 校验域名、动作风险、参数类型、步数/超时、是否需要批准。
4. **Act**：ToolExecutor 执行单个动作；执行前再次校验元素 ref 仍存在且可见。
5. **Verify**：等待页面稳定（DOM 变更/导航完成），比对预期结果；失败则重试或请求用户接管。
6. **Report**：输出结果与证据（页面片段/链接），不输出敏感字段。

## 4. 工具与风险等级

| 工具 | 说明 | 风险 | 默认批准 |
| --- | --- | --- | --- |
| `read_page` | 读取当前页快照 | L0 | 自动 |
| `find_in_page` | 页内查找 | L0 | 自动 |
| `summarize` | 总结/翻译 | L0 | 自动 |
| `scroll` | 滚动到元素/位置 | L1 | 自动 |
| `click` | 点击普通链接/按钮 | L1 | 自动；外域/下载链接需确认 |
| `type` | 在非敏感输入框输入 | L2 | 每次确认，密码框拒绝 |
| `select` | 下拉选择 | L2 | 每次确认 |
| `submit_form` | 提交表单 | L3 | 每次确认 |
| `open_tab` / `switch_tab` | 标签操作 | L1 | 自动 |
| `download` | 下载文件 | L3 | 每次确认 + 文件类型策略 |
| `share` | 分享链接 | L2 | 每次确认 |
| `bookmark` | 收藏 | L1 | 自动 |
| `login` | 登录/账号操作 | 禁止 | 只能用户接管 |
| `payment` | 支付/下单 | 禁止 | 只能用户接管 |

## 5. 安全与隐私

### 5.1 提示注入防护

- 页面文本一律标记为“不可信内容”，系统策略不允许页面文本改变工具白名单。
- 模型只能输出 JSON 计划；Kotlin 侧只执行白名单工具，拒绝脚本、URL 拼接、隐式跳转。
- 外域跳转、下载、表单提交必须触发批准；跨域多步任务需重新确认。
- 关键动作前校验当前 URL 与计划中的域一致；页面跳转到新域立即暂停。
- 维护一组提示注入回归样例（隐藏文本、伪系统提示、诱导点击等）。

### 5.2 数据最小化

- 默认只读模式使用本地摘要：正文不出设备；需要云端时按任务明确授权。
- 上传前脱敏：邮箱、手机号、身份证、银行卡、地址、Cookie、Header 一律不发送。
- 不读取密码字段、剪贴板、通讯录、定位、相机、麦克风。
- 不向模型发送 Cookie、Authorization Header、完整 DOM、下载文件内容。
- 审计日志仅存本机；与“退出即清除”策略一致，退出时删除。
- 模型供应商不用于训练；请求不携带设备标识。

### 5.3 用户控制

- 首次使用展示 Agent 权限说明与隐私影响，单独同意，不影响浏览器基础功能。
- 每个任务显示计划与风险；L2/L3 动作逐次确认。
- 用户触摸网页即暂停 Agent；提供“停止并撤销”按钮。
- 可按网站设置：禁止 Agent / 只读 / 允许协助。
- 平板与手机均支持完整的接管与停止操作。

## 6. 与现有浏览器架构的接入点

- `BrowserViewModel` 增加 `AgentSession` 状态，不把 Agent 逻辑塞进 WebViewClient。
- `PageBridge` 复用现有 WebView 实例；会话结束移除 JS 监听并清理临时 ref 属性。
- Agent UI 使用现有 Backdrop 玻璃组件；Agent 球到面板使用 `SharedTransitionLayout` 一镜到底。
- 平板侧栏复用响应式断点（≥600dp），与标签页/书签侧栏一致的布局语义。
- 隐私护盾继续生效：跟踪器拦截、权限拒绝、退出清除；Agent 额外遵守域策略。

## 7. 分阶段落地

- **P0（本次）**：设计、风险模型、UI 线框、工具协议。
- **P1 只读助手**：总结、翻译、页内问答、查找；本地/云端可选；不执行动作。
- **P2 协助操作**：点击、滚动、输入、选择；逐步确认；表单提交需确认。
- **P3 托管任务**：计划审批 + 多步执行 + 域策略 + 失败恢复；多标签对比。
- **P4 系统级体验**：金标一镜到底 Agent 面板、分享/碰一碰、跨应用任务（仍需系统与联盟 SDK 支持）。

## 8. 关键接口（示意）

```kotlin
interface AgentTool {
    val name: String
    val risk: RiskLevel
    suspend fun execute(args: JsonObject, context: AgentContext): ActionResult
}

data class AgentTask(val goal: String, val mode: AgentMode, val tabId: Long)
data class AgentStep(val index: Int, val tool: String, val args: JsonObject, val status: StepStatus)
data class AgentAction(val ref: String, val tool: String, val args: JsonObject)
data class ActionResult(val ok: Boolean, val message: String, val evidence: String?)
```

模型适配层：

```kotlin
interface LlmGateway {
    suspend fun plan(snapshot: PageSnapshot, goal: String, history: List<AgentStep>): AgentPlan
    suspend fun verify(snapshot: PageSnapshot, step: AgentStep): StepVerification
}
```

## 9. 验收指标

- 只读任务成功率 ≥ 90%（10 个常见站点样例）。
- L2/L3 动作 100% 有确认记录；0 次未授权提交。
- 提示注入样例 0 次越权工具调用。
- 退出应用后审计日志与页面快照为 0 残留。
- Agent 面板打开/关闭使用共享元素动画，手机与平板均无布局跳动。

## 10. 待决策

- 默认模型：本地小模型（隐私最强）还是用户自选云端（效果更好）。
- 是否允许在任务中跨域跳转（建议默认禁止，需再次确认）。
- 审计日志保留时长（建议仅当前会话）。
- 金标联盟一镜到底 SDK 的接入方式与可用性。
