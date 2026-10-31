# 智能体质量保障演示平台：架构与产品设计

状态：用户已于 2026-10-31 确认继续；不是实现或验收报告。

## 1. 目标与边界

为单个银行信贷智能体构建真实、可复现的质量保障演示。Portal 展示完整生命周期，但本轮仅点亮测试评估和运行观测（含告警）。业务操作在独立工作台；追踪、实验、仪表盘、规则与通知管理使用开源组件原生界面。

需求依据：[用户完整需求](../../requirements.md)。不复用旧源码或旧交接，不建设部署管理、A/B、版本路由、自动回滚、自动风险处置、真实银行接入或审批放款。Python 仅用于框架调研，产品信贷 Agent 必须是 Java Core。

## 2. 方案与选型

需求已经限定 Java Core、两个站点、原生 Langfuse/Grafana 与本地 Compose，故采用**单 Java 应用 + 两个静态前端 + 原生组件服务栈**。它满足范围且减少额外基础设施。下面是工程取舍，由开发者提出，尚待设计确认。

| 方案 | 适合程度与代价 | 决定 |
|---|---|---|
| Java 单应用 + 原生开源组件 + Compose | 单仓、少边界、能独立启动 Agent；评测与业务共用执行路径 | 推荐 |
| Java 应用拆成业务、评测、观测多个微服务 | 增加部署与跨进程状态；单 Agent 演示没有对应收益 | 不采用 |
| Sites 云托管或 Python 主 Agent | 前者不匹配 Java/Compose，后者违反 Java 业务实现要求 | 不采用 |

框架基线与逐项证据见 [Java](../../research/java-core.md)、[Python](../../research/python-core.md) 和 [外部集成](../../research/integrations.md)。源码核查不等于兼容性验证；所有版本先作为候选，运行探针通过后才写入依赖锁和镜像 digest。

技术方向：React + TypeScript + Vite，成熟组件库优先 Ant Design；沿用 Cloudscape 的导航、表单与步骤反馈原则。后端 Spring Boot + Micrometer + Actuator + OTel；JDK 与 Spring Boot 精确版本在 Java Core 依赖收敛后锁定。不要未经构建验证就选择框架最新主版本。

已核实发布身份的候选：Java Core v0.1.16 / `308d7bbfc2b2f8365e4dd47d8694c8c34fb18c31`；Python 比较研究 v0.1.19 / `3c0cfc580eecbb8dbbbf4fce3783c143140e1e09`；Langfuse v4.72.0；Grafana OSS v13.3.0；Prometheus v3.15.1。Java Core 源码要求 target 17，应用首先用 JDK 17 容器验证；没有将这些版本宣称为已验证的组合。

## 3. 系统与部署

[可浏览架构图](../../design/system-architecture.html) 表示拟建关系，不代表系统已运行。Mock 银行为同一 Java 应用内的银行工具适配层；评测 Runner 与 API 共用 AgentExecutionService，不再拆新业务服务。

| 部件 | 对外默认地址 | 责任 |
|---|---|---|
| Portal | `http://localhost:8080/` | 旅程、保障入口、非敏感链接设置 |
| 信贷工作台 | `http://localhost:8081/` | 对话、Skills、模型服务、Mock 银行 |
| Java Agent API | `http://localhost:8082/` | 框架执行、快照、运行状态、业务规则、实际埋点 |
| Langfuse | `http://localhost:3000/` | 原生追踪、数据集、实验、评分 |
| Grafana | `http://localhost:3001/` | 原生指标、告警、Contact points、通知策略 |
| Prometheus | `http://localhost:9090/` | 指标抓取与 PromQL |

两前端为不同构建产物和容器，根路径直接打开对应应用。工作台反代 `/api` 到 Agent，减少浏览器跨域；Agent 独立启动模式仅允许明确配置的本地源。Portal 不承接银行业务请求。所有公开端口默认绑定 loopback，不自动暴露到局域网。

Langfuse 必需的数据库、事件存储、缓存与对象存储按其官方 Compose 拓扑纳入系统，不能只启动一个 web 容器就声称完整。容器内部端口与主机端口分开：例如对象存储不得占用主机的 Prometheus 9090。SMTP 是用户可选外部服务，不加 Mailpit。具体依赖见集成调研。

启动脚本拟为 `./scripts/demo up`：预检 Docker 与端口 → 生成本地随机配置 → 构建启动 → 等待实际就绪 → 幂等初始化 → 报告服务入口与未验证项。后续同时提供 `./mvnw …` 的 Agent 独立启动路径。这里是接口设计，脚本尚未实现。

## 4. Agent、Skill 与业务事实

选择 Java Core v0.1.16 的 ReAct 路径，通过 `BaseAgent.registerSkill` 注册可信 Skill 目录，再挂应用实现的受限 `readFile` 工具，让模型按 Core 注入的目录读取正文。不启用 DeepAgent 的默认 shell/任意文件工具。本选择已有源码依据，但真实模型是否按提示读取 Skill 还需纵向探针验证；不把普通工具循环另命名为 Skill。

六项 Skill 文档与业务工具映射：材料核验、征信查询、还款能力、政策校验、建议汇总、人工转交。Skill 文档通过 Core 实际注册、发现及文档读取路径参与推理；应用 manifest 只记录内容 hash 与允许工具映射，不能假冒 SDK 自带绑定。文档读取限定在固定 Skill 根目录，规范化路径后验证 realpath 包含关系，拒绝路径穿越、符号链接逃逸及任意文件访问。只有成功读取实际 SKILL.md 才记一次应用侧激活事件；缺少必要读取证据时该次 Skill 使用验收失败，不以模型自述补证。

银行适配器提供六个合成案例：材料齐全、缺收入证明、高负债、征信超时、政策冲突、备注提示注入。所有工具输出含 `mock=true`、固定 `caseId` 与来源。提示注入备注始终当作不可信数据，不能覆盖系统指令或解锁工具。

模型输出转换为结构化 CreditAdvice：`recommendation`（补材料/人工复核/可进入人工审批流程）、`evidence[]`、`missingMaterials[]`、`repaymentCalculation`、`policyReferences[]`、`handoffReasons[]`、`humanApprovalRequired=true`。不提供 approve/disburse 工具。无法解析或业务事实不完整时返回明确错误或保守人工处理依据，不能用固定答案假装模型完成。

计算采用十进制定点规则，统一数据和规则版本，给出单位与舍入策略。业务具体阈值属于合成演示政策而非真实银行政策，随案例数据与期望结果入库。模型生成的自然语言不作为计算正确性的唯一证据。具体候选数据与评分适用性见 [合成案例契约](../../design/credit-fixtures.md)。

## 5. 模型配置与密钥边界

仅支持经实际 Java Core 探针验证的 OpenAI-compatible 协议和认证形式。用户配置 Base URL、modelId、API Key、Temperature、最大输出、超时；协议端点格式按 SDK 实际行为校验，不盲目重复拼接 `/v1`。

- 密钥仅从密码输入框提交到后端，GET 返回 `hasApiKey`；不回显尾号。前端不得持久化密钥，不写 URL、日志、异常原文、Trace 或 localStorage。
- 模型配置采用不可变版本。每个会话固定 `modelConfigVersion` 和凭据版本引用；新会话使用最新版本。旧凭据只以加密形式保留给仍引用它的会话，明确的撤销操作将让旧会话需要新建配置，不能悄悄换模型。
- 本地应用数据卷存配置与运行元数据；凭据用 AES-GCM 加密，主密钥由启动脚本在本地 secrets 目录随机生成、最小文件权限、容器只读挂载，不入库。密钥与加密配置一起删除会不可恢复；普通 down 不删卷。
- 实际测试分别验证文本输出、工具参数及执行回合、多 chunk 流式与终止标记。UI 说明可能产生费用；自动化默认只用显式测试专用模型接口，无生产兜底。
- 用户主动配置模型地址代表调用该服务；请求使用 TLS 正常校验。拒绝 URL 内嵌凭据，拦截无效协议，不能通过 `verify=false` 绕过证书。

## 6. API 与运行状态设计

| API（计划契约） | 关键行为 |
|---|---|
| `GET /api/skills`、`GET /api/skills/{id}` | 返回真实加载状态、原始文档、hash、应用工具映射 |
| `GET/PUT /api/model-config` | GET 脱敏，PUT 原子创建新不可变版本 |
| `POST /api/model-tests` | 基于固定配置版本执行三项真实能力探针 |
| `POST /api/conversations` | 固定模型、Skill 与政策快照 |
| `POST /api/conversations/{id}/runs` | 必须幂等键；同键同 payload 返回原 runId，不同 payload 409 |
| `GET /api/runs/{id}` | 服务端真实执行状态、结果、错误、traceId、导出确认状态 |
| `GET /api/runs/{id}/events` | SSE；带单调事件序号，重连支持已确认游标 |
| `GET /api/mock/cases` | 固定合成案例与接口事实 |
| `POST /api/demo/faults` | 显式演示模式中设置/恢复真实工具故障，不改告警状态 |
| `POST /api/evaluations`、`GET /api/evaluations/{id}` | 冻结快照、启动整批、返回进度与原生实验关联 |

执行状态：accepted → running → completed / failed。连接状态是浏览器独立维度：connected / disconnected / reconciling / unknown。应用重启时未记录终态的运行转为 `unknown`，不自动重新执行，以免重复扣费。未知结果不算完成或业务零分。

运行元数据与事件落本地事务型存储（候选 SQLite，单服务单写者）；模型及框架对话上下文首轮仅在内存，重启后旧对话只读，页面提示新建会话。持久化历史可查看但不暗示断点续跑。事件仅保存脱敏的案例、调用摘要和错误类别，敏感原始 HTTP 请求头不落库。

单会话同一时刻只允许一个活动运行；幂等映射事务提交后才执行。接收 SSE 失败不取消后台任务，也不再次发起模型请求。后端并发量、工具超时和总执行预算有明确上限。

## 7. 追踪、指标与告警

Core 原生事件/Span 和应用补充 Span 分别标注来源。一个运行对应一个真实 traceId；记录模型、Skill 文档读取、银行工具以及结构化输出。应用导出层执行 allowlist 脱敏，禁用可能把完整配置或 API Key 放入 attribute 的默认路径；日志同样过滤。Token 只有 provider 实际报告且能确认来源才展示；Core 缺 usage 时默认零不得导出为“测得零”。本轮默认不提供成本与首字延迟面板。

根据固定 Langfuse 版本实现 OTel 与实验关联，不使用已移除的 legacy endpoint。导出失败不伪造追踪成功；业务运行可完成但另显示观测失败。评测场景必须等待关联数据可读取才算集成验证通过。

完整面板契约、标签、PromQL、低流量与 NoData 语义见 [指标与告警设计](../../design/metrics-and-alerts.md)。首次初始化使用稳定 UID 和只创建缺失资源的策略；全部成功后写入持久化 seed-complete 标记，此后重启不重复协调资源，保留用户删除、编辑与暂停状态。初次部分失败可恢复，但只有确定 404 才创建，认证/查询失败中止。显式重建资源是单独操作。Grafana 默认规则暂停；邮箱和通知策略由用户在原生界面配置，初始化不写默认通知树。

## 8. 评测与真实回归

Java Runner 读取 Langfuse 中已实际创建的六例数据集，执行同一 Agent 路径，生成预测字段；规则评分器逐项评价类别、材料、计算、政策引用、征信缺失的保守处理及人工边界。

源码已经确认 Java `BaseEvaluator.evaluate(Case, Map<String,Object>)` 与 `Metric.compute(prediction, label, kwargs)` 扩展；首轮实现基于 `BaseEvaluator` 的 CreditRuleEvaluator，保留逐规则结果并映射到 Langfuse。应用负责数据集、冻结与状态封装，不重建评测编辑器。先用最小编译和非空样本验证扩展，再接 Java 实际执行。规则检查只覆盖结构化字段，不宣称验证任意自然语言语义安全。LLM Judge 默认不可用，只有配置并实际验证裁判后才可启用。

每批冻结 `modelConfigVersion`、Skill 内容 hash、dataset item snapshot/hash、policyVersion、ruleVersion；评测启动后更新配置不影响该批。Skill 文档按内容 hash 复制到只读版本目录，每次读取校验字节，不能只冻结 hash 却继续读取可变文件。批次冻结 Mock 场景与故障策略；交互演示的故障开关不修改正在运行的评测。每项结果包含 `executionStatus`、`judgementStatus`、可空 `score` 与原因。执行失败、评判异常、业务 score=0、尚未评测保持不同状态；汇总同时给出覆盖率和失败数，不只展示成功子集均分。

回归比较在原生实验界面执行：A/B 两批均真实调用、使用同数据同规则，并记录唯一可解释配置差异（例如两版 Skill 文档的具体政策提醒）。不得预写预测或分数以保证出现退化；如果真实输出没有变化就报告无观察到差异。测试专用模型可以验证比较协议，但不得称真实模型质量已验收。

需求 §10.8 的“回归变化演示”另设完成门槛：必须由两批实际执行产生可追溯的输出/分数差异，并指出对应案例、配置变化与判分理由。两个真实实验输出相同时，只能标记比较链路已验证，回归变化演示仍未完成。显式测试模型产生已知差异可以验证协议，但需单独标注，不能替代真实模型的质量结论。

## 9. 配置、初始化与本地支持

统一公开端口配置生成前端链接与服务器设置；内部使用 Compose 服务名。`NO_PROXY` 覆盖 localhost、127.0.0.1、内部服务名及必要容器网络；不继承旧私有代理或关闭 TLS/校验。项目内可以保留公共 `.env.example`，实际 `.env`、secrets、数据卷和认证会话全部忽略。

| 资源 | 保存位置 / 重启行为 |
|---|---|
| 模型配置、加密凭据、运行/评测元数据 | 应用持久卷；普通重启保留 |
| 框架活动对话上下文 | 首轮内存；重启旧会话只读，未完成运行 unknown |
| 数据集、实验、追踪、分数 | Langfuse 官方各持久卷；API 初始化不覆写同 ID 数据 |
| 规则、暂停状态、Contact points、通知策略 | Grafana 持久卷；初始化不覆写 |
| 运行指标 | Prometheus 持久卷，明确保留期 |
| Mock 持续故障开关 | 默认不跨重启保留；页面明确说明 |

拟支持 macOS Apple Silicon / Intel、Linux x86_64/arm64、Windows WSL2 + Docker Desktop；每种平台只有实测后才能标记“验证支持”。Langfuse 官方 Compose 指南建议至少 4 核、16 GiB 内存、示例 100 GiB 存储；完整系统还增加 Java、Grafana 与 Prometheus，因此以至少该配置开始实测，不把它宣称为整套系统的已测最低需求。容器镜像须有对应架构 manifest；缺失则报告限制，不能静默依赖本地镜像别名。详见外部集成调研来源。

本机已读到 Docker Desktop Engine 29.6.1、Compose v5.3.0，arm64；Java 8，不满足现代 Java Core 的候选运行环境；Maven 未在 PATH 中。容器构建验证与适当 JDK 准备均尚未进行，不自动升级本机全局工具。

## 10. 阶段交付与验收门槛

| 阶段 | 交付 | 必须具有的证据 |
|---|---|---|
| 0 调研与设计 | 能力矩阵、架构、UCD、接口及持久化边界 | 固定官方来源；设计确认；未验证项明确 |
| 1 可运行基础 | 仓库、锁定依赖、两个前端、Java skeleton、Compose | 两根路径；无模型可启动；密钥不回显；实际构建通过 |
| 2 Agent 纵向链路 | 六 Skill、Mock 工具、真实模型、快照、SSE | 真实读取/调用事件；正常、超时、重复提交、断流测试 |
| 3 观测与原生初始化 | OTel、Langfuse、Prometheus、Grafana | 同次运行 Trace 可读；实际 scrape；原生仪表盘有样本 |
| 4 评测与告警 | 冻结 Runner、两批实验、规则触发/恢复 | 非空样本/实际预测/评分关联；真实故障状态时间线 |
| 5 完整验收与交接 | 一键启动、重启测试、文档、独立审查 | 需求 11 步证据、提交号、远端分支、已知限制 |

重要验证：测试专用 OpenAI-compatible 服务器只能在测试 profile 显式启用；分别覆盖工具参数、多 chunk 流、截断流、429/超时与密钥泄漏断言。生产配置绝不自动回退测试模型。真实模型验收和 SMTP 送达都需要用户可用配置，缺失则标为未验证，不能用协议测试替代。

本目录现阶段只包含设计产物。用户已提供目标远端 `git@github.com:ascend-agentos-bridge/fin-agent-assurance.git`；GitHub API 已确认仓库存在、当前分支列表为空，默认分支配置为 `main`。尚未初始化产品代码仓库、提交或推送。确认设计后将先形成可执行实施计划，再按独立工作分支逐阶段提交；不强推主分支、不覆盖已有代码。

## 11. 待确认的开发者选择

1. 单 Java 应用，评测共用执行服务；两个独立前端构建，不引入额外业务微服务。
2. 本地 SQLite 保存应用元数据；会话上下文仅内存，重启后旧会话只读。
3. 六个版本化合成案例与规则评分器；不默认启用 LLM Judge。
4. Grafana 只首次创建原生资源，默认暂停规则，保留用户后续修改。
5. 先用固定发布版做最小真实 SDK/OTel/实验探针，通过后锁定所有依赖及镜像。

框架探针若否定某项接口或版本，先调整兼容层并记录；若改变以上产品行为或范围，则回到设计确认。

## 12. 独立可实施性审查

[独立审查记录](../../design/builder-review.md)：审查者阅读需求、主设计、UCD、指标、案例及固定版本调研后，结论为 `nothing important left`。已澄清回归比较与实际差异演示的不同验收状态。这是设计可实施性审查，不是产品代码审查或端到端验收。
