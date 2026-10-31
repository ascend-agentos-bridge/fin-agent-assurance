# 智能体质量保障演示平台 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付两个独立站点、真实 Java Core 信贷 Agent、原生可观测/评测/告警与可复现的本地 Compose 演示。

**Architecture:** 单 Java 应用承载真实 Agent、Mock 银行工具、配置快照、运行记录和评测 Runner。Portal 与工作台独立构建；Langfuse/Grafana 使用原生专业界面。应用通过实际埋点导出指标，通过 OTel 和原生 API 关联追踪与实验。

**Tech Stack:** Java 17、Maven 3.9、openJiuwen Java Core 0.1.16；Spring Boot、Micrometer、OTel、SQLite；React、TypeScript、Ant Design、Vite；Langfuse 4.72.0、Grafana 13.3.0、Prometheus 3.15.1、Docker Compose。精确应用依赖与镜像 digest 在任务 1/8 的兼容性检查后冻结，不把候选组合当成已验证。

**Spec:** [已确认总体设计](../specs/2026-10-31-agent-assurance-design.md)，以及其中引用的 UCD、指标与合成案例契约。

## Global Constraints

- 信贷业务只使用 Java Core；Python 仅作为已有比较调研。
- Portal 8080、工作台 8081、Agent 8082、Langfuse 3000、Grafana 3001、Prometheus 9090；可统一改配。
- 规划中生命周期无可点击假功能；不建设部署、A/B、自动回滚、自动审批放款。
- 密钥不回显、不入日志/Trace/浏览器持久存储/Git；生产不回退测试模型。
- Skill 真实注册、实际文档读取和业务工具执行分开留证；不暴露通用 shell。
- 指标没有样本保持未知；禁止客户、会话、runId、traceId 等无界指标标签。
- 批次冻结模型、凭据引用、Skill 字节、数据、政策、规则与 Mock 场景。
- Grafana 原生资源首次初始化后保留用户编辑、删除、收件人、暂停及通知设置。
- 测试专用模型必须显式 profile；真实模型质量和 SMTP 送达分别验收。
- 使用工作分支 `codex/agent-assurance`，阶段提交与推送；文档随代码入库。

## Review Focus

- 断流、终态未知与同幂等键重放：不重复执行或扣费（任务 4）。
- 旧会话遇到配置/凭据更新：保持旧快照且从不回显密钥（任务 2/4）。
- Skill 路径穿越、符号链接与运行中内容变化：拒绝读取，不能只验证 hash（任务 3）。
- Langfuse 异步 202 / 部分 207 与分页：不能把接收当成已完成实验（任务 5/6）。
- 零流量、缺序列、查询错误与用户删除预置规则：保持不同状态，重启不恢复被删除规则（任务 7/8）。

## 文件布局与共享接口

- `agent/pom.xml`：后端单 Maven 模块；`agent/src/main/java/com/assurance/` 按 config、skills、bank、runtime、evaluation、observability 划分。
- `apps/portal/` 与 `apps/workbench/`：独立 Vite 根路径应用；`packages/contracts/` 共享 TypeScript API 类型与链接验证。
- `skills/<skillId>/SKILL.md`：六个可信文档；`fixtures/credit-cases.json`、`fixtures/demo-policy.json` 为版本化合成数据。
- `infra/`：Prometheus、Grafana 初始资源、Langfuse 初始化；`compose.yaml` 只暴露必要 loopback 端口。
- `scripts/demo`：本地唯一启动入口；`scripts/test-*`、`tests/e2e/`：协议、持久化及完整链路验收。
- `docs/`：现有调研/设计、版本锁、运行说明、阶段结果和限制；`data/`、`.secrets/`、`.env`、构建产物全部忽略。

共享 DTO 由 `agent/.../runtime/RunTypes.java` 与 `packages/contracts/src/index.ts` 对齐：

- `ModelConfigInput(provider,baseUrl,modelId,apiKey,temperature,maxTokens,timeoutSeconds)`；脱敏 `ModelConfigView(version,provider,baseUrl,modelId,hasApiKey,temperature,maxTokens,timeoutSeconds)`。
- `ConversationView(id,modelConfigVersion,skillBundleHash,policyVersion,readOnly)`。
- `RunRequest(task,caseId)`；`RunView(id,conversationId,state,result,error,traceId,traceExportState)`；`RunEvent(sequence,type,timestamp,payload)`。
- `CreditAdvice(recommendation,evidence,missingMaterials,repaymentCalculation,policyReferences,handoffReasons,humanApprovalRequired)`。
- `EvaluationView(id,state,snapshot,items,experimentId)`；每个 item 分开 `executionStatus`、`judgementStatus`、可空 `score`、原因与 traceId。

枚举：运行 `accepted/running/completed/failed/unknown`；建议 `supplement_materials/manual_review/eligible_for_human_review`；规则 `pass/fail/not_applicable/error/not_evaluated`。前端连接状态独立于运行状态。

---

### Task 1: 固定 Java Core 的可执行接入契约

**Files:** `agent/pom.xml`；`agent/src/test/java/com/assurance/core/CoreContractTest.java`；`agent/src/test/java/com/assurance/support/TestModelServer.java`；`docs/versions.md`；`.gitignore`。

**Interfaces:**
- Consumes: 固定官方源码方法与 POM；无早期产品代码。
- Produces: 可构建的 Java 17 依赖树；测试用 `TestModelServer.start(): URI`；已确认的 Agent/Session/Tool/Skill/stream/evaluator 绑定步骤。

- [ ] Step 1: 建立最小构建配置，先写契约测试：真实 Core 注册一个可信 Skill，模型请求中存在目录，`readFile` 实际返回正文，真实 Java 工具收到参数，最终输出与工具结果一致；另测流式多 chunk 与异常。
- [ ] Step 2: 在容器 JDK 17 跑 `mvn -f agent/pom.xml -Dtest=CoreContractTest test`，确认缺少绑定/实现的预期断言失败；依赖解析或编译错误单独诊断，不能当作业务 RED。
- [ ] Step 3: 用 ResourceMgr 注册 Tool 实现、AbilityManager 注册 ToolCard、有效 SysOperation 和 `registerSkill(path,root)` 完成实际绑定。测试模型只在 test 源集中，禁用生产兜底。
- [ ] Step 4: 同命令 GREEN，读取依赖树，核验 Core artifact 可获取、JUnit/OTel/Jackson/Reactor 收敛；若中央制品不可用，固定提交源码构建并记录来源与校验。
- [ ] Step 5: 锁定 Maven/依赖并提交 `test: verify Java Core execution contracts`；推送工作分支。

### Task 2: 可启动后端与不可变模型配置

**Files:** `agent/.../AssuranceApplication.java`；`agent/.../config/{ModelConfigService,CredentialVault,ModelConfigController}.java`；`agent/.../storage/Database.java`；对应 `ModelConfigTest.java`、`ModelCapabilityTest.java`；`agent/src/main/resources/application.yaml`。

**Interfaces:**
- Consumes: Task 1 的真实 Core Model 文本/tool/stream 协议。
- Produces: `ModelConfigService.save(ModelConfigInput): ModelConfigView`、`latest(): Optional<ModelConfigView>`、`snapshot(long): ModelSnapshot`；GET/PUT `/api/model-config` 与 POST `/api/model-tests`。

- [ ] Step 1: 先写测试：无模型也可启动；密钥加密落库且 GET/异常/日志不含密钥；更新后旧 version 仍解析旧快照；无效 URL/参数拒绝；缺 key 不假成功。
- [ ] Step 2: 执行 `mvn -f agent/pom.xml -Dtest=ModelConfigTest,ModelCapabilityTest test`，确认目标行为 RED。
- [ ] Step 3: 实现 SQLite 事务、AES-GCM vault、服务端主密钥文件与脱敏 DTO；实际能力测试分别执行文本、工具回合和多 chunk 流，记录时间/配置版本，不以 HTTP200 代替能力成功。
- [ ] Step 4: 同命令 GREEN；用测试密钥做浏览器存储、日志和 span 输出扫描。生产测试接口不接受测试模型 profile 的自动回退。
- [ ] Step 5: 提交 `feat: persist versioned model configuration securely` 并推送。

### Task 3: 六个真实 Skill 与 Mock 银行工具

**Files:** `skills/{materials,credit,repayment,policy,summary,handoff}/SKILL.md`；`fixtures/{credit-cases,demo-policy}.json`；`agent/.../skills/{SkillCatalog,BoundedSkillReader}.java`；`agent/.../bank/{MockBank,RepaymentCalculator}.java`；`SkillBindingTest.java`、`MockBankTest.java`。

**Interfaces:**
- Consumes: Core Tool/Skill 绑定契约；设计中的 six-case 与政策规则。
- Produces: `SkillCatalog.freeze(): SkillBundle`、`BoundedSkillReader.read(SkillBundle,String): SkillDocument`；`MockBank.invoke(String,Map,MockScenario): ToolResult`；GET `/api/skills`、GET `/api/skills/{id}`、GET `/api/mock/cases`。

- [ ] Step 1: 测试真实注册/读取与工具顺序；目录穿越、绝对路径、symlink 逃逸、文档被改动拒绝；正常月供 3650.63、比例 0.3325，高负债 0.8825；真实超时与政策冲突事实。
- [ ] Step 2: 执行 `mvn -f agent/pom.xml -Dtest=SkillBindingTest,MockBankTest test`，观察预期行为 RED。
- [ ] Step 3: 实现只读内容寻址 Skill 副本、逐次 realpath/内容验证、六个显式工具；事件保留 Mock 来源与实际异常。提示注入备注作为数据；无 shell/批准/放款工具。
- [ ] Step 4: 同命令 GREEN；确认没有读取文档时不记录已使用 Skill，工具失败没有被成功终态覆盖。
- [ ] Step 5: 提交 `feat: execute registered credit skills and mock bank tools` 并推送。

### Task 4: 对话运行、快照与可恢复状态查询

**Files:** `agent/.../runtime/{RunTypes,ConversationService,AgentExecutionService,RunRepository,RunController}.java`；`RunLifecycleTest.java`、`IdempotencyTest.java`、`SseRecoveryTest.java`。

**Interfaces:**
- Consumes: ModelSnapshot、SkillBundle、MockScenario、Task 3 工具。
- Produces: `createConversation(): ConversationView`；`submit(String,String,RunRequest): RunView`（conversationId,idempotencyKey,request）；`get(String): RunView`；`events(String,long): List<RunEvent>`；设计中的 conversation/run/SSE API。

- [ ] Step 1: 测试同键同 payload 返回同 runId 且执行一次；同键不同 payload 409；单会话并发拒绝；无模型提示配置；断流后查询原 run；重启未完成运行 unknown、旧会话只读；既有会话保持配置版本。
- [ ] Step 2: 执行 `mvn -f agent/pom.xml -Dtest=RunLifecycleTest,IdempotencyTest,SseRecoveryTest test`，确认每类缺失行为 RED。
- [ ] Step 3: 先事务保存幂等键和 accepted 状态，再在受限执行器运行真实 Agent；持久化有序脱敏事件；SSE 只读已有运行、支持游标，不重复提交；解析 CreditAdvice 时验证结构化边界。
- [ ] Step 4: 同命令 GREEN；完整后端 `mvn -f agent/pom.xml verify`；检查失败、保守人工建议与工具异常能同时准确呈现。
- [ ] Step 5: 提交 `feat: add idempotent agent runs and reconnectable events` 并推送。

### Task 5: 独立双站点与真实观测

**Files:** `apps/portal/src/{App,Journey,ServiceLinks}.tsx`；`apps/workbench/src/{App,Chat,Skills,ModelServices,MockBank}.tsx`；`packages/contracts/src/{index,links}.ts`；各应用 Vite 配置；`agent/.../observability/{RunTelemetry,RedactingSpanExporter,MetricsConfiguration}.java`；`TelemetryTest.java`；`tests/e2e/navigation.spec.ts`、`chat.spec.ts`。

**Interfaces:**
- Consumes: Task 2–4 API；UCD；指标设计；固定 Langfuse v4 OTLP HTTP 接口。
- Produces: 两个根路径独立产物；实际 traceId/导出状态；`/actuator/prometheus` 指标；Portal 非敏感地址设置。

- [ ] Step 1: 写行为测试：规划阶段不可操作；原生外链新标签页；未检查不绿灯；无模型不可发送；重复点击不重复执行；断流显示核实状态；Key 不在浏览器存储。遥测测试检查父子 span、secret allowlist、工具异常计数与无界标签拒绝。
- [ ] Step 2: 分别执行 `npm test` 与 `mvn -f agent/pom.xml -Dtest=TelemetryTest test`，确认对应行为 RED。
- [ ] Step 3: 实现中文界面与 API 接入，不使用前端固定回复；模型测试先解释费用；注入 Core OTel 与应用补充 span，固定 HTTP endpoint/header，防止重复 root 或重复计数。Micrometer 仅导出真实聚合与已知固定零计数器，不生成成功样本。
- [ ] Step 4: `npm test`、`npm run build`、后端 verify；运行已显式测试模型的完整对话，再从 Langfuse API 读回 trace，确认每层事实来自真实执行。
- [ ] Step 5: 提交 `feat: deliver separate assurance and credit interfaces with telemetry` 并推送。

### Task 6: 冻结评测与 Langfuse 原生实验比较

**Files:** `agent/.../evaluation/{EvaluationSnapshot,CreditRuleEvaluator,EvaluationRunner,LangfuseClient,EvaluationController}.java`；`EvaluationFreezeTest.java`、`CreditRuleEvaluatorTest.java`、`LangfuseContractTest.java`；`scripts/evaluate`。

**Interfaces:**
- Consumes: AgentExecutionService；Core BaseEvaluator；固定 Langfuse dataset/item/OTLP experiment/score API。
- Produces: `EvaluationRunner.start(EvaluationRequest): EvaluationView`；`get(String): EvaluationView`；CLI 与 evaluation API；真实 experimentId 与逐例结果。

- [ ] Step 1: 测试冻结后改模型/Skill/Mock 不影响批次；六例非空真实预测；0 分/执行失败/评判失败/未评/不适用分开；202 后有界轮询读取；207 只处理失败项；分页不漏样本。
- [ ] Step 2: 执行 `mvn -f agent/pom.xml -Dtest=EvaluationFreezeTest,CreditRuleEvaluatorTest,LangfuseContractTest test`，确认目标行为 RED。
- [ ] Step 3: 复用 Java BaseEvaluator 做薄规则适配；每例独立真实 trace、传播 v4 experiment 属性，分数关联根 observation；固定 dataset snapshot/hash，不用移除的 dataset-run-items API。
- [ ] Step 4: 同命令 GREEN；实际跑两批并从原生 API 读取所有样本/分数，再在原生 Compare 核验。测试模型已知差异只算协议验证；真实模型无差异则 §10.8 保持未完成。
- [ ] Step 5: 提交 `feat: run frozen credit evaluations linked to native experiments` 并推送。

### Task 7: Grafana 原生初始化、真实故障告警及恢复

**Files:** `infra/prometheus/prometheus.yml`；`infra/grafana/{dashboard.json,alerts.json}`；`scripts/initialize-grafana`；`agent/.../bank/DemoFaultController.java`；`tests/integration/{grafana-seed,alerts}.test.ts`；`docs/metrics.md`。

**Interfaces:**
- Consumes: 实际 Micrometer 指标；Task 3 Mock 故障；固定 Grafana 原生 API。
- Produces: 原生 datasource/dashboard/三类默认暂停规则；固定 seed-complete 标记；真实运行告警与恢复证据。

- [ ] Step 1: 测试只有确定 404 才创建；已存在资源和 seed-complete 后不写；用户删除、阈值、暂停、Contact points/策略重启保留；NoData/Error/低流量分开；未配置 SMTP 不宣称送达。
- [ ] Step 2: 执行 `npm run test:integration -- grafana-seed alerts`，先确认缺失种子/规则行为 RED。
- [ ] Step 3: 用 UI 可编辑的 API seed 与稳定 UID，初次规则全部 paused；不写收件人或覆盖通知树；实现真实 Mock 工具故障开关。指标查询按设计窗口和门槛，缺失不补健康零。
- [ ] Step 4: 同命令 GREEN；显式启用规则并通过实际 Agent 流量故障触发 Pending/Firing；恢复工具与正常流量后读取 Normal，保留状态时间戳。自动化不发真实邮件。
- [ ] Step 5: 提交 `feat: provision editable native monitoring and fault-driven alerts` 并推送。

### Task 8: 一键演示、持久化与最终验收交接

**Files:** `compose.yaml`；`agent/Dockerfile`；`apps/{portal,workbench}/Dockerfile`；`.env.example`；`scripts/{demo,initialize-langfuse,verify-demo}`；`tests/e2e/full-demo.spec.ts`；`docs/{startup,demo,acceptance,known-limitations}.md`；根 `README.md`。

**Interfaces:**
- Consumes: 全部已测试服务/API、原生 seed 与版本锁。
- Produces: `./scripts/demo up`、`./scripts/demo check`、`./scripts/demo down`；手工独立 Agent 启动命令；11 步验收证据与明确限制。

- [ ] Step 1: 写验收断言：空模型允许启动、两站点根路径、统一改端口、幂等初始化、内部 NO_PROXY、只绑定 loopback、重启数据保留、unknown 运行不重放、生产 profile 没测试模型兜底。
- [ ] Step 2: `npm run test:e2e -- full-demo`，记录当前未能完成的实际失败，不用健康检查替代执行证据。
- [ ] Step 3: 实现 Compose 完整 Langfuse 官方依赖栈及持久卷、随机本地 secrets、只初始化缺失数据集、前置检查与错误退出；锁定镜像 digest/架构和安装包校验，保留 TLS/认证。
- [ ] Step 4: 用一条命令冷启动、跑完整协议测试、重启复核、停服/故障/恢复；真实模型和邮件需要用户配置，缺失明确未验证。后端 verify、前端 test/build 与集成/e2e 全部读取结果。
- [ ] Step 5: 一次独立全分支审查，重要修正按 RED→GREEN 验证；提交 `docs: complete reproducible demo and acceptance evidence` 并推送；报告真实远端分支/commit、完成/未完成/未验证，不自动合并主分支。

## 自查与执行方式

本计划覆盖需求 §1–11。初始依赖版本/API 的运行兼容性由任务 1 验证，不以此前源码研究冒充通过；所有业务实现、UI、观测与交付分别有测试门槛。共享 DTO 和枚举只在前述共享契约处定义，后续任务消费同名类型。

推荐本会话原生执行：任务之间接口依赖紧密，由同一实现者连续完成并记录 ledger，末尾一次独立审查。也可选择每任务独立实现者与审查者的子智能体执行。计划确认后按选择执行，不再逐任务询问是否继续。
