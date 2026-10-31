# Python agent-core：Java 质量保障平台比较研究

研究日期：2026-10-31。结论级别：**源码与官方文档核验；未运行 Python Agent、模型、评估器或遥测链路。**

## 1. 版本、范围和证据边界

- 官方仓库：[openJiuwen-ai/agent-core](https://github.com/openJiuwen-ai/agent-core)。
- 查询官方 GitHub `releases/latest` 得到最新发布版 **v0.1.19**；`published_at=2026-09-30T04:00:05Z`，`target_commitish=3c0cfc580eecbb8dbbbf4fce3783c143140e1e09`。[发布页](https://github.com/openJiuwen-ai/agent-core/releases/tag/v0.1.19)
- 本报告所有源码链接固定到完整提交 **3c0cfc580eecbb8dbbbf4fce3783c143140e1e09**；`pyproject.toml` 同时声明包版本 0.1.19、Python >=3.11,<3.14。[包元数据](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/pyproject.toml#L5-L10)
- 当前 API 应从 `openjiuwen.core.single_agent` 导入 `BaseAgent/ReActAgent/ReActAgentConfig/AgentCard`；`legacy` 是明确的兼容路径。不能只根据 README 中的旧 WorkflowAgent 示例推断当前公开接口。[公开导出](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/core/single_agent/__init__.py#L3-L44)

执行限制：按照要求尝试 `git clone --depth 1 --branch v0.1.19` 到指定目录，沙箱内 DNS 失败，提升权限后 GitHub TCP 443 连接仍于约 75 秒超时；**浅克隆未成功**。改用 GitHub 官方连接器取得该提交完整递归树（7,431 个条目，`truncated=false`）和下列有关文件，保存为 `work/research/python-source/` 源码缓存。缓存不是 Git checkout，不据此声称克隆成功。本报告没有安装依赖、创建业务 Agent 或伪造运行结果。

分类用语：

| 分类 | 含义 |
|---|---|
| 原生（native） | 本提交确有接口或实现；仍需配置、安装可选依赖和运行验证 |
| 应用适配（app adapter） | SDK 提供可用接缝，但平台需实现数据映射、策略、计算或运行管理 |
| 未找到（not found） | 在明确列出的检查范围内无实现证据；不代表全仓库绝对不存在 |
| 不支持（unsupported） | 有接口校验或实现明确拒绝该用途；不以“未搜索到”代替 |

## 2. 能力矩阵

以下各行均为 **source-only**；本研究没有 runtime-verified 项。

| 能力 | 分类 | 已核验能力与实际边界 | 固定源码证据 |
|---|---|---|---|
| Agent 与执行入口 | 原生 | BaseAgent 提供异步 invoke/stream，公开 ReActAgent；实例包装器发出全局 Agent 输入输出事件。业务运行状态、SLA、租户隔离不是这些类型本身给出的平台能力。 | [BaseAgent 包装](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/core/single_agent/base.py#L56-L116)、[抽象执行入口](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/core/single_agent/base.py#L251-L286) |
| 模型调用与扩展 | 原生 | Model 统一 invoke/stream；BaseModelClient 子类通过 __client_name__/__client_type__ 注册到客户端注册表。扩展者须正确产生统一消息、usage 和调用事件。 | [Model](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/core/foundation/llm/model.py#L42-L88)、[客户端注册](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/core/foundation/llm/model_clients/base_model_client.py#L45-L81) |
| 函数工具与自定义工具 | 原生 | @tool 转换为 LocalFunction，支持 schema/Card/自动签名提取；Tool 抽象类支持 invoke/stream。工具实例包装器发送开始、结束、异常事件。 | [@tool](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/core/foundation/tool/tool.py#L31-L113)、[Tool 事件](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/core/foundation/tool/base.py#L132-L213) |
| Skill 基础机制 | 原生 + 应用适配 | SkillManager 发现 SKILL.md、解析 YAML description、维护元数据注册表；SkillUtil 把名称/描述/目录注入提示，让模型读取正文。执行文件/代码的工具由外部 SysOperation 提供。**注册 Skill 不等于已执行、已遵守或已通过评估。** | [元数据与解析](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/core/single_agent/skills/skill_manager.py#L11-L21)、[加载](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/core/single_agent/skills/skill_manager.py#L64-L116)、[提示及执行边界](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/core/single_agent/skills/skill_util.py#L58-L113) |
| 生命周期回调与 Rail | 原生 | before/after invoke、模型、工具；模型/工具异常；task/react iteration、用户消息及 steering。支持优先级、同步/异步回调和 Rail；它们可修改上下文，不是天然只读监听器。 | [事件枚举和上下文](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/core/single_agent/rail/base.py#L471-L572)、[注册](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/core/single_agent/agent_callback_manager.py#L21-L62) |
| trace/span、模型与工具 IO、错误 | 原生 + 宿主接入 | OTel 回调可记录模型输入输出、tool arguments/result、错误、会话归属；完整 run root 生命周期归宿主，不能声称调用 init_observability 就自动覆盖任意 Agent 的根链路。 | [回调订阅](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/extensions/observability/runtime.py#L278-L311)、[根 span 契约](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/extensions/observability/callback_handler.py#L851-L922)、[run root 帮助方法](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/harness/observability/run_span.py#L69-L174) |
| token、成本、TTFT | 原生字段/观测 + 应用适配 | 有真实 provider usage 映射和客户端时钟观测；成本不保证返回、默认零不等于免费；首包时间不是 provider 内部首 token 时间。详见第 4 节。 | [usage 类型](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/core/foundation/llm/schema/message.py#L17-L40)、[OpenAI 映射](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/core/foundation/llm/model_clients/openai_model_client.py#L2741-L2764) |
| OTel/OTLP 导出 | 原生 | TraceProvider、父级采样、批处理、附加 SpanProcessor；gRPC/HTTP、Langfuse、console/file exporter。遥测采样会使离线评估样本不完整，平台须显式管理采样与保留。 | [Provider](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/extensions/observability/runtime.py#L126-L183)、[配置](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/extensions/observability/config.py#L61-L78)、[exporter](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/extensions/observability/runtime.py#L397-L431) |
| Prometheus / MeterProvider / SDK metrics endpoint | 未找到；应用适配 | 所检查的 observability config/runtime/setup 与部署 Collector 只有 trace 管线；没有原生 Prometheus exporter/抓取端点的证据。需平台自建聚合指标或配置独立 Collector 管线；本研究未验证该配置。 | [exporter 枚举](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/extensions/observability/config.py#L61-L78)、[Collector traces 管线](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/deploy/observability/otel-collector-config.yaml#L48-L53) |
| 离线标签与模型裁判评估 | 原生 | BaseEvaluator、DefaultEvaluator、MetricEvaluator，以及 ExactMatch/LLMAsJudge；可自定义指标。基础模型裁判异常可能转成 0 分或带失败 reason 的默认结果，应用必须区分 judge 不可用与被测 Agent 失败。 | [评估器](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/agent_evolving/evaluator/evaluator.py#L29-L79)、[判分错误处理](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/agent_evolving/evaluator/metrics/llm_as_judge.py#L40-L66) |
| 能力与 trace 评估 | 原生 | Symphony 有九种内置 evaluator，包含正确性、完整性、能力选择、组合有效性及 latency；消费 capability fingerprint 和调用方提供的 trace case，不负责执行被测能力。 | [内置列表](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/symphony/evaluation/evaluators.py#L671-L680)、[Suite 边界](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/symphony/evaluation/suite.py#L83-L113) |
| 自动准入/发布决策或统一总分 | 不支持作为 Symphony Suite 原生用途；应用适配 | Suite 明确不做 admission decision、能力执行或整体 composite score。平台可消费逐指标结果，自行应用准入阈值和版本策略。 | [Suite 声明](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/symphony/evaluation/suite.py#L83-L96)、[聚合结果 score=None](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/symphony/evaluation/suite.py#L367-L379) |

## 3. 接入观测的具体接缝

存在两层互补机制，Java 保障平台设计时应保留这种区分：

1. **语义 Rail**：`AgentCallbackContext` 提供 agent、session、model context、typed inputs、exception、retry history 和跨回调 extra；`lifecycle` 在 finally 发 after 事件并保留异常。适合业务语义、策略和重试归因。[上下文](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/core/single_agent/rail/base.py#L536-L572)、[finally 行为](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/core/single_agent/rail/base.py#L754-L792)
2. **调用框架事件**：Model/Tool 包装器与 provider 事件驱动共同 OTel handler；适合调用 IO 和技术遥测。实现不能把语义 Rail 与低层事件当作同一次调用的两个独立计数源。[模型包装](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/core/foundation/llm/model.py#L63-L88)、[Tool 包装](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/core/foundation/tool/base.py#L132-L213)

宿主应在 invoke 前建立 run root、在 finally 中关闭；单轮 agent span 和多轮 task iteration span 由 AgentObservabilityRail 区分，缺根时会跳过以避免孤立 span。run root 支持 session/request/run/turn 身份以及失败、取消、in-band 错误；收尾会处理未关闭子 span。[root API](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/harness/observability/run_span.py#L69-L174)、[关闭契约](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/harness/observability/run_span.py#L208-L279)、[agent span 条件](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/harness/observability/rail.py#L614-L649)

模型内容采用 `gen_ai.system_instructions`、`gen_ai.input.messages`、`gen_ai.output.messages`；工具采用 call arguments/result，结果即使不抛异常也可能通过 success=False 被识别为失败。默认 prompt/completion 脱敏开关为 false；平台应把内容采集策略作为真实配置，而非宣称默认已脱敏。[结构字段](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/extensions/observability/gen_ai_semconv.py#L34-L68)、[tool 返回失败](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/extensions/observability/callback_handler.py#L765-L843)、[默认开关](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/extensions/observability/config.py#L61-L78)

## 4. token、成本与时延：哪些真实可得

| 数据 | 源码证实的产生路径 | 保障平台应保留的限制 |
|---|---|---|
| input/output/total tokens | OpenAI 客户端从 response.usage 取值；流式设置 include_usage=True，接收最终 usage-only chunk。 | 是 provider 返回值，不保证所有兼容服务返回。usage 整体缺失与字段默认 0 必须区别；不能自动用估算覆盖后仍标注“真实”。 |
| reasoning/cache tokens | UsageMetadata 区分 cache read/miss/write、reasoning；cache_authoritative 等字段可表达来源。OTel input/output 保留 provider totals，细分不应重复加总。 | Reasoning/cache 是细分；不能无条件加到总 token 上。 |
| 金额 | BaseModelClient 从 cost、usage_cost、cost_details 提取 input/output/total；未见通用价格表计算。 | 不保证有金额或币种，缺失默认 0；需要 app adapter 标记 unknown/provider_reported/price_estimated、币种及费率版本。 |
| 模型首包 | handler 以 monotonic_ns 在第一条 LLM_STREAM_OUTPUT 回调测量，写 gen_ai.response.time_to_first_chunk，单位秒。 | 包括客户端调用链时间；第一 chunk 可能不是第一个可见文本 token。不可包装成服务端 TTFT 或端到端用户首字时延。 |
| 模型总延迟、TPOT | handler 记录 total_latency_ms；有首末 chunk 且 output_tokens>1 才计算 TPOT。 | 回调观测；非逐 token 原生时间戳。非流式调用没有从该路径获得首包观测。 |
| 整个 Agent/run 延迟 | root/agent/tool span 的开始结束时间可计算。 | host 必须正确闭合生命周期；不能将 LLM latency 相加直接当成并发 Agent E2E。 |

证据：[非流式 usage 映射](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/core/foundation/llm/model_clients/openai_model_client.py#L2741-L2764)、[include_usage](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/core/foundation/llm/model_clients/openai_model_client.py#L2035-L2041)、[usage-only chunk](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/core/foundation/llm/model_clients/openai_model_client.py#L2863-L2897)、[成本提取](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/core/foundation/llm/model_clients/base_model_client.py#L349-L386)、[首包测量](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/extensions/observability/callback_handler.py#L528-L548)、[延迟/TPOT/usage](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/extensions/observability/callback_handler.py#L1217-L1287)。

`UsageMetadata` 中虽然存在 `first_token_time/request_start_time/total_latency` 字段，但仅凭 schema 不能证明每个 provider 会填写。真实模型联调仍须核验原始响应、最终 stream chunk 和落库 span；本研究没有执行该验证。[字段定义](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/core/foundation/llm/schema/message.py#L17-L40)

## 5. 已存在的评估能力与自定义方式

### 5.1 agent_evolving：样本、标签、预测

- `BaseEvaluator.evaluate(Case,predict)->EvaluatedCase`，`batch_evaluate` 校验样本数量并用 ThreadPoolExecutor 并行。
- `DefaultEvaluator` 采用 question/expected/model answer 的 LLM 一致性判定，有自定义 metric 文本和 reason；`MetricEvaluator` 执行一个或多个 Metric，支持 mean/first、per_metric。
- `ExactMatchMetric` 默认大小写和空白归一化后做 0/1 匹配；`LLMAsJudgeMetric` 是语义一致性 0/1，不是通用安全检测器。
- 自定义方式：实现 `Metric.name/compute(prediction,label,**kwargs)`，可返回 float 或 dict；或继承 BaseEvaluator 自行生成带解释的结果。独立裁判错误状态需由平台额外规范。

证据：[BaseEvaluator 与 batch](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/agent_evolving/evaluator/evaluator.py#L29-L79)、[DefaultEvaluator](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/agent_evolving/evaluator/evaluator.py#L82-L175)、[MetricEvaluator](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/agent_evolving/evaluator/evaluator.py#L197-L254)、[Metric 扩展契约](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/agent_evolving/evaluator/metrics/base.py#L16-L76)、[ExactMatch](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/agent_evolving/evaluator/metrics/exact_match.py#L12-L45)。

### 5.2 Symphony：能力质量、实际调用轨迹

内置清单是 **structure_conformance、description_quality、classification_consistency、success_rate、latency、accuracy、completeness、capability_selection、composition_effectiveness**。前三者面向 fingerprint 静态元数据；后续 trace evaluator 要求调用方提供证据。选择与组合评估分别要求存在调用或至少两次调用。Latency 只报告观测，score=None，不自行实施 SLA 门槛。[内置实现](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/symphony/evaluation/evaluators.py#L144-L148)、[trace 前置条件](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/symphony/evaluation/evaluators.py#L288-L305)、[latency](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/symphony/evaluation/evaluators.py#L392-L435)、[选择与组合](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/symphony/evaluation/evaluators.py#L621-L680)。

自定义实现 `Evaluator` 协议，提供 metric_id、scope、requires_llm、evaluate(EvaluationContext)，返回同步或 awaitable MetricResult，并通过 EvaluationSuite.register 注册。LLM 判断必须显式 enable_llm 且提供 llm。Suite 会校验指标/能力身份，隔离扩展异常。[协议](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/symphony/evaluation/base.py#L138-L166)、[LLM 启用条件](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/symphony/evaluation/base.py#L249-L273)、[注册与运行校验](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/symphony/evaluation/suite.py#L179-L194)、[扩展错误隔离](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/symphony/evaluation/suite.py#L382-L420)。

结果模型区分 pass/fail/not_applicable/error/observed；后三种不能带分数。EvaluationCase 含 query、inputs、expected_output、OpenAI 标准 message、output、success、latency 和 event_time。**平台需要把遥测转成该模型；不能把存在 span 当成已有符合契约的评估样本。**[case 模型](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/symphony/models/evaluation.py#L118-L163)、[状态与分数契约](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/symphony/models/evaluation.py#L223-L246)。

### 5.3 不应遗漏、也不应泛化的其他层

| 层 | 已核验内容 | 使用边界 |
|---|---|---|
| Skill Evaluation/Evolution Pipeline | Agent/benchmark 注册装饰器；BaseAgentAdapter.setup/run、BaseBenchAdapter.load_tasks/prepare_environment/evaluate/aggregate；内置名 jiuwenswarm、skillsbench；Docker 执行/演化管线。 | 这是基准运行与演化基础设施；接 Java Agent/领域基准需要自定义 adapter。[接口](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/agent_evolving/evaluator/evaluator_pipeline/base.py#L16-L72)、[Agent SPI](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/agent_evolving/evaluator/evaluator_pipeline/base.py#L99-L151)、[Benchmark SPI](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/agent_evolving/evaluator/evaluator_pipeline/base.py#L188-L238) |
| RSI evaluator judger | EvaluationJudger 的 async judge 契约、JudgeResult；ScriptBasedJudger 接 backend 的 JudgeResult 或 reference answer；LlmAsJudgeJudger 显式启用、timeout/retry、证据文件和 infrastructure error。 | ScriptBasedJudger 明确拒绝 reference.rubric；需 rubric-capable judger。[契约](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/rsi/harness_rsi/evaluator/judger/base.py#L17-L46)、[script 限制](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/rsi/harness_rsi/evaluator/judger/script_based.py#L31-L87)、[LLM judge](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/rsi/harness_rsi/evaluator/judger/llm_as_judge.py#L50-L114) |
| dev_tools.SkillEvaluator | 构造 ReActAgent，挂文件/代码/shell 工具和评估 Skill，Runner.run_agent 生成评估报告。含安全裁判 Skill 资产。 | 是 Agent 驱动的评估工作流；不能等同确定性安全证明或所有业务风险已覆盖。[实现](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/dev_tools/skill_evaluator/skill_evaluator.py#L23-L141)、[安全裁判 Skill](https://github.com/openJiuwen-ai/agent-core/blob/3c0cfc580eecbb8dbbbf4fce3783c143140e1e09/openjiuwen/dev_tools/skill_evaluator/skills/skill_safety_judge/SKILL.md#L1-L40) |

## 6. 对 Java 保障平台的可执行启示（设计推论）

本报告未检查 Java 仓库，因此**不作“Python 支持而 Java 不支持”结论**。以下是由 Python 接口边界导出的设计建议，需要与独立 Java 源码证据合并：

1. 定义语言无关的 Run/Span/Event/Artifact/EvaluationResult 契约；保留 session/run/turn/request、父子关系、版本、错误类型、原始 usage 和观测来源。Python 示例说明“全局技术事件”和“Agent 语义回调”都需要适配，必须去重。
2. 把 token、金额、TTFT 分开建模；支持 null/unknown、observed/estimated、观测时钟和单位。基于价格表估算时保存币种和费率版本；不要把 SDK 默认 0 当作实测。
3. 评估插件至少支持确定性、标签对比、LLM judge、trace 规则与基准 adapter。借鉴 Symphony 的 pass/fail/not_applicable/error/observed；judge 基础设施错误不应进入 Agent 业务失败率。
4. 以 trace/artifact 转换生成有版本的样本，绑定被测模型、prompt、工具/Skill 与数据集版本；再运行回归、门槛与准入。阈值、权重、发布策略属于平台层，不能由“有 evaluator 类”推定完成。
5. Prometheus/SLA 仪表盘、告警、租户治理、人工反馈、审计、业务正确性 rubric、企业安全策略应作为独立保障平台能力。Python 本次可证的是接入与部分评估实现，不是以上端到端治理已实现。

待运行核验：根链路与多轮并发完整性；异常/取消/retry span 闭合；provider usage 缺失与流式最终 usage；实际金额与币种；首包包含何种 chunk；OTLP 成功导出与采样；Skill 是否被读取并影响执行；评估器的 bad-output/timeout/不可用行为。所有这些目前仍为未运行，不应在演示中标记“已验证”。

