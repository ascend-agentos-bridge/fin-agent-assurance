# 开发准备阶段验证说明

2026-10-31。验证对象仅为研究与设计交付包，不是产品。

## 实际进行的检查

- 已读取用户完整需求，按 Java/Python 两版官方固定发布提交分别核查源码；外部集成以官方发布与版本源码为依据。
- Java 研究的 104 个固定提交源码链接、Python 研究的 69 个固定提交源码链接，由研究者与已读取的源码缓存检查文件存在及行号范围；这不等于运行 SDK。
- 独立设计审查已执行，结果见 [builder-review.md](builder-review.md)；重要工程选择均在主设计中明示。
- 三张 SVG 使用 XML parser 解析；没有将其宣称为运行截图。
- 架构图通过 Archify 内容/结构校验、交付一致性检查与真实浏览器自动检查；最终摘要 `ok=true`，四项 gates 均 pass。仍有一个关系线交叉和一个绕行提示；没有声称人眼视觉验收通过。
- 已独立计算演示贷款月供及负债比，确认 3650.63、0.3325 与 0.8825 的示例值。
- 本机 Docker 只读版本/连接检查成功。未启动产品容器、未拉取产品镜像。

## 未进行或受限的检查

- GitHub shell clone 超时，已改用官方连接器读取固定提交；没有完整可构建 clone。
- 本机只有 Java 8，Maven 未在 PATH；匹配 JDK 的构建、依赖下载、SDK 能力探针未执行。
- 本地 `file:` URL 被浏览器安全策略拒绝，没有以其他浏览器路径绕过。因此 SVG 的浏览器视觉验收未执行；此限制不影响设计文档的静态交付。
- 前后端产品代码与 Compose 尚未编写；无产品单元测试、集成测试或端到端结果。
- 真实模型质量、真实 Trace/实验/评分写入、Prometheus 抓取、Grafana 故障触发/恢复、重启持久化和 SMTP 送达均未验证。
- 用户已提供远端 `git@github.com:ascend-agentos-bridge/fin-agent-assurance.git`。GitHub 连接器只读核查确认仓库存在、分支列表为空、默认分支配置 main，报告 push 权限。SSH 22 在执行沙箱被阻止，未进行实际推送测试；没有据此将网络阻止误报为账号无权限。未创建产品分支、提交或推送，不能用本地文档包代替远端阶段提交。

## 可复现静态检查

从交付包根目录执行 Python XML 解析、Markdown 本地链接检查；将架构 HTML 的 SHA-256 与 `system-architecture.finalize-summary.json` 中 `artifact.sha256` 比对。归档中的 manifest 同样提供文件摘要。

架构图源码为 [system-architecture.json](system-architecture.json)，自动检查摘要为 [system-architecture.finalize-summary.json](system-architecture.finalize-summary.json)。检查工具记录的绝对 work 路径是本次生成环境的证据位置，不是产品运行依赖；交付 HTML 自包含。
