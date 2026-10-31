# Java / Python Agent Core 能力比较

日期 2026-10-31。以下由两份固定源码报告合并；全为源码核验，运行效果未验证。原生表示接口/实现存在，不代表用户无需适配。未找到仅指各报告明确的搜索范围，不当作绝对不支持。

基线：Java v0.1.16 / `308d7bbfc2b2f8365e4dd47d8694c8c34fb18c31`；Python v0.1.19 / `3c0cfc580eecbb8dbbbf4fce3783c143140e1e09`。两者发布节奏不同，不能通过 Python 接口推断 Java 具备同名能力。

| 能力 | Java 固定版本 | Python 固定版本 | 本平台处理 |
|---|---|---|---|
| Agent / 模型 / 工具 | 原生 ReAct、模型接口、工具及 invoke/stream/Mono/Flux | 原生异步 invoke/stream、模型与工具包装 | 仅实现 Java 业务 Agent |
| Skill | 原生注册/发现/提示目录；读文档工具与业务工具需显式提供 | 原生 SkillManager/SkillUtil；文件/执行能力由 SysOperation 提供 | Core 注册 + 受限 reader；真实读取与工具执行分开记 |
| 生命周期 | 原生 Agent/模型/工具 before/after 与模型/工具异常；Agent 外层异常兜底需适配 | 原生 Rail、Agent/model/tool 事件；语义与低层事件不同 | 应用去重埋点，不按 after 事件假设成功 |
| Trace / Span / IO / errors | 原生 tracer 与显式 OtelRail/handler，需配置启用 | 原生 OTel 扩展；完整 root 生命周期有宿主契约 | 源头脱敏、root/child 关联、实际导出读取确认 |
| Token | 原生 provider usage 映射；缺失可能默认零；未见自动 include_usage | 原生 usage 映射，OpenAI 流式主动 include_usage | 无真实来源就 unknown；本轮不放伪零 Token 面板 |
| 成本 | 搜索范围内未找到内置计价 | provider 金额提取存在，缺失可能默认零 | 本轮不默认提供成本面板 |
| 首包/首字 | 有 schema/常量；检查实现未见实际首字计时写入 | handler 实测第一 stream chunk 的时间 | 不把 Python 首包或 Java 空字段称为已实现首字延迟 |
| OTel / OTLP | 原生 opt-in HTTP/gRPC exporter | 原生 OTLP/多 exporter | Langfuse 只用已支持的 HTTP/protobuf，做 v4 适配 |
| Prometheus 端点 | 限定搜索未找到原生 HTTP endpoint | 限定搜索未找到原生 exporter/MeterProvider endpoint | 应用 Micrometer/Actuator，指标来源标注应用 |
| 基础评测 | 原生 BaseEvaluator/Metric/MetricEvaluator、ExactMatch、LLMAsJudge、SkillEvaluator | 原生相似评测层；另有 Symphony、RSI 与 evaluator pipeline | 复用 Java BaseEvaluator，薄业务规则适配 |
| 评测额外层 | 本次不推断与 Python Symphony 的等价性 | 已查到 Symphony 九类能力/trace evaluator 与可扩展协议 | 不引入 Python Agent 或额外 Python 评测服务 |
| 完整平台能力 | 运行管理、快照、数据集、原生服务关联、持久化与告警仍需应用集成 | 同样不能由 SDK 类存在推断平台已具备 | 本轮由 Java/Compose 实现，逐项运行验收 |

明确不支持/不覆盖的边界：注册 Skill 不保证执行或遵循；Python Symphony Suite 的原生用途明确不包括自动准入、执行被测能力或总分决策；结构化规则评分不构成任意自然语言安全证明。本轮也不实现自动发布或真实信贷审批。

逐项官方不可变源码链接、精确方法与限制见 [Java 源码报告](java-core.md) 和 [Python 源码报告](python-core.md)。外部运行平台版本与实验 API 见 [集成报告](integrations.md)。
