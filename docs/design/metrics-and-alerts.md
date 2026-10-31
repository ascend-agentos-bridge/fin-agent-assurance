# 真实指标与告警设计契约

状态：待实现、待验证。名称为应用计划导出的 Micrometer 指标，不宣称 Java Core 原生提供 Prometheus HTTP 端点。

## 数据与查询

以下查询窗口默认 5m；Prometheus 抓取周期候选 5s，Grafana 规则评估周期候选 10s。所有分位数仅在窗口存在实际样本时显示；不以 `or vector(0)` 把完全缺失数据涂成健康。标签只允许有限枚举，异常类映射为固定错误类别。

| 面板 / 指标 | 数据来源与含义 | 有界标签 | 查询示例 | 限制 |
|---|---|---|---|---|
| 请求量 `assurance_runs_started_total` | 应用接受一次新执行后递增 | `origin=chat/evaluation` | `sum(increase(assurance_runs_started_total[5m]))` | 幂等重放不增加；非业务通过量 |
| 终态量 `assurance_runs_finished_total` | 后端持久化终态后计一次 | `origin,outcome=completed/failed` | `sum by(outcome)(increase(assurance_runs_finished_total[5m]))` | 人工转交建议可 completed；未知不算成功 |
| E2E `assurance_run_duration_seconds` | 接受执行到技术终态，单调计时 | `origin,outcome` | `histogram_quantile(0.95,sum by(le)(rate(assurance_run_duration_seconds_bucket[5m])))` | 非首字延迟；未知运行无终态样本 |
| 模型调用/耗时/错误 `assurance_model_duration_seconds` | Core 实际模型调用前后薄适配，每次调用记一次 | `provider=openai_compatible,outcome=ok/error/timeout` | `sum by(outcome)(increase(assurance_model_duration_seconds_count[5m]))` | 内部隐式重试覆盖范围要在探针后记录 |
| 模型 P95 | 同上 | 同上 | `histogram_quantile(0.95,sum by(le)(rate(assurance_model_duration_seconds_bucket[5m])))` | 不支持 Token/成本则不建伪零面板 |
| 工具次数/耗时/错误 `assurance_tool_duration_seconds` | 各实际银行工具适配器计时 | `tool` 六个固定工具，`outcome=ok/error/timeout` | `sum by(tool,outcome)(increase(assurance_tool_duration_seconds_count[5m]))` | 不把工具错误被 Agent 保守处理后抹掉 |
| Skill 文档加载 `assurance_skill_load_total` | 实际发现与加载完成的结果 | `skill` 六项，`outcome=ok/error` | `sum by(skill,outcome)(assurance_skill_load_total)` | 启动累计；与运行内激活分开 |
| Skill 文档激活 `assurance_skill_activation_total` | Core 注册目录后，模型经应用受限 readFile 实际成功读取文档 | `skill,outcome=ok/error` | `sum by(skill,outcome)(increase(assurance_skill_activation_total[5m]))` | 是应用记录的文档读取，不冒称 Core 原生激活事件，也不等于业务工具执行 |
| JVM 内存 | Micrometer JVM binder | JVM 原生有限 pool/area | `sum by(area)(jvm_memory_used_bytes)` | 仅 Java 进程，非整机资源 |
| GC / 线程 | JVM binder | collector/state 有限 | `rate(jvm_gc_pause_seconds_sum[5m])`；`jvm_threads_live_threads` | 具体字段以锁定版本 scrape 输出为准 |
| 抓取链路 | Prometheus 内置 | `job,instance` 固定目标 | `up{job="credit-agent"}` | 只能证明 scrape，不证明模型、Trace、邮件成功 |
| 追踪导出失败 `assurance_trace_exports_total` | 应用 exporter 完成回调 | `outcome=ok/error` | `sum by(outcome)(increase(assurance_trace_exports_total[5m]))` | 网络接收不必然等于 UI 可查，另做读取确认 |

初版直方图候选 buckets：0.1、0.25、0.5、1、2、5、10、30、60、120 秒；由探针与演示流量调整。指标不得包含客户、会话、runId、traceId、用户输入、Base URL 或任意模型名等无界标签。单次排障用 Trace 与应用运行记录关联。

技术完成率按“终态 completed / 所有已记录终态”定义，仅在分母大于零时显示；正在运行和未知数量单列。不能称业务正确率，不将 started 与 completed 的时间窗错位比值称为成功率。

## 三类演示规则

| 规则 | 初始触发条件 | Pending | 恢复与低流量语义 |
|---|---|---|---|
| 征信持续错误 | 1m 内 credit_check 错误或超时 >=3，且实际调用 >=3 | 20s | 真实关闭故障并继续发送成功请求，窗口移出错误后恢复；零请求不当作工具异常 |
| 运行延迟 | 2m 有 >=5 个终态样本且 E2E P95 >8s | 30s | 恢复正常工具延迟并补充正常流量；不足样本显示“样本不足” |
| 抓取链路异常 | `up{job="credit-agent"} == 0`，或目标序列消失 | 20s | 恢复服务 / scrape 后解除；这是采集失败，不是业务质量结论 |

错误数要先注册固定工具与结果枚举的实际计数器，避免第一次 timeout 才产生新序列而漏掉首次增量；这只初始化已知计数器，不制造成功请求。采集链路规则单独处理 `absent(up{job="credit-agent"})`；Prometheus 本身查询失败由 Grafana `DatasourceError` 表示。

业务错误和延迟规则设置 NoData 为独立 NoData、执行错误为 Error，不能设 OK 掩盖缺失。新启动低流量本身不触发业务故障；采集异常规则才覆盖无样本/无目标。默认规则全部暂停，必须由演示者在 Grafana 原生界面显式启用。

## 原生配置与持久化

- 数据源、dashboard UID、规则 UID 使用稳定 ID。首次初始化先查询存在性，仅创建确定缺失的资源；冲突报错，不覆盖用户配置。全部成功后写持久化 seed-complete 标记，后续重启跳过，保留用户删除。首次部分失败只恢复缺失资源；认证/服务错误不能当作 404。数据源地址与演示端口来自同一配置文件。
- 规则通过版本匹配的 Grafana API 创建并允许 UI 编辑；不用文件预置反复覆盖规则或暂停状态。`X-Disable-Provenance: true` 是否适用以锁定版本 API 及端到端测试为准。
- 用户在 Grafana Contact points 添加邮箱并配置原生通知策略；不预填真实收件人，不在自动化发送真实邮件。Grafana 数据卷保留规则、暂停状态、通知策略、Contact points。
- SMTP 在服务端环境中配置，保留 TLS 校验与认证；SMTP 未配置时清楚说明“邮件未验证”。告警进入 Firing 与邮件送达是两个独立验收项。
- 规则恢复必须读取 Grafana 原生状态确认；不能把“故障开关已关闭”当成告警已恢复。

## 实际验收证据

保留脱敏 scrape 样本、PromQL 查询时间窗与结果、Grafana rule UID 与 Normal/Pending/Firing/Normal 状态时间戳。Agent 停止后验证目标失败/消失与查询错误区别。所有故障都通过真实 Agent/Mock 适配器执行产生；不使用 Grafana API 直接伪造 Firing。

参考：[Prometheus instrumentation](https://prometheus.io/docs/practices/instrumentation/)、[Histogram 类型](https://prometheus.io/docs/concepts/metric_types/)、[Grafana 原生规则与状态](https://grafana.com/docs/grafana/latest/alerting/alerting-rules/create-grafana-managed-rule/)。这些是设计依据，不能替代锁定版本的运行验证。
