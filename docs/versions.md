# 版本与来源

状态：Java Core 与 Spring Boot 模型服务契约通过；整体 Compose/前端版本锁定仍进行中。

| 项目 | 版本 / 摘要 | 当前证据 |
|---|---|---|
| Java Core | com.openjiuwen:agent-core-java:0.1.16 | Maven Central 制品可获取；源码 jar SHA256 014b893254381c1f2f4c50152dd09c4709ef90abdad0306114b246142b0081c4；ReAct 源码与固定 Git 提交比较仅多一个末尾空行 |
| Maven 构建镜像 | maven:3.9.16-eclipse-temurin-17 | 官方镜像下载成功；digest sha256:1a352420f7aba21f5ad08df31bab55f74c013fb491f1ae8ab1dd7ff9ed698584；Linux arm64 容器中 Core 契约测试 3/3 通过 |
| 本机局部 JDK | Amazon Corretto 17.0.21+11，aarch64 macOS | 官方 tar.gz SHA256 3cd5a2bdd380a5654aa1ef5eda3032f60021e8e48a0bdd552e8e39b032705732；java -version 已执行 |
| 本机局部 Maven | 3.9.16 | 官方 tar.gz SHA512 831a8591fe20c8243b1dbe7d71e3244f31d1665b0804b2e825e38cbbe5ce0cafb8338851f90780735568773e0a6cd07bbec107cda0b896b008b861075358b6f6 |

局部工具只解压在本次工作目录，不修改系统 Java、全局 Maven 或 PATH。交付物使用标准公开镜像与 Maven 坐标，不依赖该绝对目录。

官方来源：[Core 制品目录](https://repo.maven.apache.org/maven2/com/openjiuwen/agent-core-java/0.1.16/)、[Amazon Corretto 下载与 SHA256](https://docs.aws.amazon.com/corretto/latest/corretto-17-ug/downloads-list.html)、[Maven 公开发行目录](https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/)、[Maven 官方镜像](https://hub.docker.com/_/maven/)。

后端基础：Spring Boot 4.0.7、Jackson 2.21.5 BOM、OTel 1.64.0 BOM、JUnit 6.0.3；模型服务 7 项定向测试通过。没有将此结果扩大为真实模型质量验证。
