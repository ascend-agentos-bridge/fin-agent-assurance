# 智能体质量保障演示平台：开发准备交付

日期：2026-10-31。当前阶段为官方框架调研、架构与 UCD；**产品代码、Compose 实现及完整链路尚未开发或验收**。

## 首先审阅

1. [总体设计与阶段验收门槛](docs/superpowers/specs/2026-10-31-agent-assurance-design.md)
2. [系统架构图](docs/design/system-architecture.html)
3. [UCD 与交互契约](docs/design/ucd.md)
4. [指标与告警口径](docs/design/metrics-and-alerts.md)
5. [合成案例与规则](docs/design/credit-fixtures.md)

[Java/Python 能力差异总表](docs/research/capability-comparison.md) 汇总原生能力、应用适配及未找到的能力，逐项证据在分语言报告中。

关键界面：[Portal](docs/design/portal-wireframe.svg)、[信贷工作台](docs/design/workbench-wireframe.svg)、[模型服务](docs/design/model-wireframe.svg)。这些均为设计示意，不是运行截图。

## 已核查的官方基线

| 部件 | 固定版本 / 来源 | 当前证据 |
|---|---|---|
| Java Agent Core | v0.1.16 / 308d7bbfc2b2f8365e4dd47d8694c8c34fb18c31 | [Java 源码能力矩阵](docs/research/java-core.md) |
| Python Agent Core | v0.1.19 / 3c0cfc580eecbb8dbbbf4fce3783c143140e1e09 | [Python 对比矩阵](docs/research/python-core.md)，仅调研，不开发 Python 信贷 Agent |
| Langfuse | v4.72.0 候选 | [原生 OTLP 实验与 API 调研](docs/research/integrations.md) |
| Grafana OSS | v13.3.0 候选 | 同上；原生可编辑规则、暂停状态与持久化 |
| Prometheus | v3.15.1 候选 | 同上；真实指标抓取与状态语义 |

以上是源码和官方文档核查，**不是这些组件已经共同运行通过**。发布提交身份不替代镜像 digest、依赖解析或架构兼容验证。

主要工程决定：两个独立前端；Java ReAct + Core 真实 Skill 注册与受限文档读取；Java 原生 BaseEvaluator 扩展；应用实际埋点补 Prometheus；Langfuse/Grafana 原生界面；本地 Docker Compose；不增加 Kubernetes、A/B 或自动审批功能。

## 实际状态

| 项目 | 状态 |
|---|---|
| 用户需求归档 | [已保存](docs/requirements.md) |
| 官方 Java/Python 能力研究 | 已形成固定提交报告；未运行 SDK |
| 架构 / UCD / 指标 / 案例 | 已确认；进入实施 |
| 独立设计审查 | [审查记录](docs/design/builder-review.md) |
| 架构图自动检查 | 内容/结构、交付一致性与真实浏览器自动检查通过；仍有 1 处交叉关系线提示，未做人眼视觉验收 |
| 三张 SVG 设计稿 | XML 解析通过；未进行浏览器视觉验收 |
| 本机 Docker | Engine 29.6.1、Compose v5.3.0，可连接 |
| 本机 Java / Maven | 只有 Java 8，Maven 不在 PATH；产品拟以匹配 JDK 容器构建 |
| Java/前端构建、模型能力测试 | 未执行 |
| Trace/实验/评分/告警触发恢复 | 未执行 |
| 真实模型质量、SMTP 邮件送达 | 未验证；需要有效用户配置 |
| 产品工作分支、提交、推送 | 未创建；已确认目标远端，当前为空仓库，尚未推送 |

目标远端：`git@github.com:ascend-agentos-bridge/fin-agent-assurance.git`；[GitHub 仓库](https://github.com/ascend-agentos-bridge/fin-agent-assurance)。GitHub API 已确认仓库存在、当前分支列表为空，默认分支配置为 `main`，连接器报告具备 push 权限；这不等于实际推送已通过。SSH 22 端口在当前执行沙箱被阻止，仓库核查使用 GitHub 连接器完成。设计确认后在独立工作分支逐阶段提交。

## 后续开发顺序

设计已确认 → [实施计划](docs/superpowers/plans/2026-10-31-agent-assurance-implementation.md)已确认，采用本会话原生执行 → 固定版本最小 SDK 探针 → 独立双站点与配置 → Java Skill/工具纵向链路 → 原生观测 → 两批评测与告警恢复 → 重启与完整验收。每阶段都有可运行测试和提交，不以健康检查或设计图代替端到端证据。

## 验证材料与限制

- [阶段验证说明](docs/design/verification.md)
- [静态检查结果](docs/design/static-checks.json)：12 份 Markdown 无缺失本地链接、3 张 SVG 解析通过，架构图内容与校验摘要一致。
- [架构图自动检查摘要](docs/design/system-architecture.finalize-summary.json)
- [架构图源数据](docs/design/system-architecture.json)

官方 GitHub shell clone 因网络失败，研究通过官方 GitHub 连接器读取固定提交源码；未下载或修改旧项目。浏览器不允许直接导航本地 file URL，因此 SVG 未做浏览器视觉验收；没有绕过浏览器策略。

本交付包不含密钥、环境凭据、模型配置或运行数据。以上文档应在产品仓库建立后随代码提交，不能仅放在被忽略的临时目录。
