# 项目概览

## 1. 项目定位

本项目是一个面向本地生活商家的智能运营 Agent 平台。底层覆盖优惠券秒杀、缓存、异步下单、探店社交等 Java / Redis 业务链路，上层将商家运营分析、RAG 知识问答、Tool Calling、Workflow、Agent Eval 和 Preference Memory 整合成一个可观测、可评测、有人审的 Agent 工程化系统。

它不是普通聊天机器人。核心价值是把模型放进真实业务系统，并通过工具白名单、Human-in-the-loop、Workflow、Eval 和 Memory 安全边界控制执行风险。

## 2. 技术栈

- 后端：JDK 17、Spring Boot 2.3.12、MyBatis-Plus、MySQL
- Redis：String、Set、Sorted Set、BitMap、GEO、Lua、Stream、缓存重建互斥锁
- AI：LangChain4j、DashScope / 通义千问、Embedding、Prompt 模板
- 前端：Vue 2、Element UI、Axios、Nginx
- 测试与工程化：JUnit、Mockito、Maven、GitHub Actions

基础业务代码源于 Java 8 / Spring Boot 2.3 生态；由于当前 Agent / DashScope 依赖包含 Java 17 字节码，项目统一推荐使用 JDK 17 构建和运行。

本地配置通过环境变量提供：`MYSQL_PASSWORD` 用于 MySQL 密码，`HMDP_IMAGE_UPLOAD_DIR` 或 JVM 参数 `-Dhmdp.image-upload-dir=/your/path/` 用于图片上传目录。仓库不应提交真实数据库密码、API Key、Token 或用户隐私数据；SQL 中的手机号格式数据仅用于本地演示初始化。

## 3. 核心业务能力

- 手机号验证码登录、用户资料和店铺查询
- 普通优惠券、秒杀券、订单和券码
- Lua 原子秒杀、Redis Stream 异步下单、缓存重建互斥锁和一人一单
- 店铺缓存、缓存穿透、击穿与一致性处理
- 签到 BitMap、关注 Set、Feed Sorted Set、附近商户 GEO
- 探店笔记、点赞榜、关注关系和粉丝 Feed

## 4. Agent 能力

- 普通商家运营对话和 Tool Calling
- 店铺、订单、优惠券、评价和经营诊断只读工具
- Tool Registry 工具元数据与模型可调用白名单
- 活动建议与优惠券 / 秒杀券草稿
- 草稿编辑、后端安全校验和商家确认创建
- 模型调用、工具调用、草稿动作和操作审计

模型不能直接执行退款、删除活动、修改库存、支付状态等高风险操作，也不能绕过确认创建真实活动。

## 5. RAG 能力

- 商家知识文档维护、启用和禁用
- 文本分片、Embedding 和向量存储
- TopK 召回、关键词兜底、规则重排和相似度阈值
- 无可靠召回判断与知识来源展示
- RAG 调试、评测用例、批量评测和历史运行记录

RAG Eval 用于评估知识召回质量，不与 Agent 行为评测混表。

## 6. Workflow / Eval 能力

Workflow 将一次 Agent 执行保存为 Run，将 RAG、意图、工具选择、工具执行、模型回复、Memory 加载等节点保存为 Step，用于历史回放和问题定位。Recorder 对异常做兜底，并对记录内容截断、脱敏，避免审计失败影响主流程。

Agent Eval 第一版复用 `MerchantAgentRulePolicyService`，不调用真实大模型，评测 intent、tool、confirm、risk 四类确定性指标。安全用例覆盖删除活动、退款、库存、订单、核销、支付、差评和用户隐私等高风险输入。

## 7. Memory 能力

- 店铺级 Preference Memory 人工维护
- 启用 Memory 注入普通 chat 和 Tool Calling Prompt
- Prompt 规定工具查询事实优先
- Workflow 记录 `MEMORY_LOAD`
- 前端支持新增、编辑、启用、禁用和删除
- 规则生成 Memory Candidate
- 候选支持编辑、确认、拒绝和删除
- 商家确认后才写入正式 Memory

当前没有实现 LLM 自动抽取、自动写入长期 Memory、向量 Memory 或 Summary Memory。

## 8. 安全边界

- 模型只能调用 Tool Registry 中允许的只读工具。
- 写操作使用活动草稿和商家确认。
- 高风险输入由后端规则识别和拒绝。
- Memory 与候选按店铺权限隔离。
- Memory 保存时限制长度并拦截手机号、验证码、token、apiKey、password、authorization 等敏感信息。
- 候选记忆不会直接进入 Prompt。
- Workflow 不记录完整敏感 Memory 内容。
- 自动化测试与 GitHub Actions 持续验证关键边界。

## 9. 面试亮点

1. 同时具备 Java / Redis 业务深度与 Agent 工程化，不是孤立的模型 Demo。
2. Tool Calling 建立后端白名单，写操作通过 Human-in-the-loop 隔离风险。
3. RAG 不止实现召回，还包含阈值、兜底、来源和批量 Eval。
4. Workflow Run / Step 让 Agent 执行可追踪，并通过异常兜底避免影响主链路。
5. Agent Eval 使用线上同源规则做确定性安全回归。
6. Memory 采用正式记忆与候选记忆两层设计，确认后才进入长期 Prompt。

## 10. 简历表述

本地生活商家智能运营 Agent 平台｜Java / Spring Boot / Redis / MySQL / Vue / LangChain4j

- 基于 Spring Boot、Redis、MySQL 与 Vue2 实现本地生活商家运营平台，覆盖优惠券秒杀、探店社交、商家运营分析和 Agent 工作台。
- 使用 Lua 脚本与 Redis Stream 实现秒杀资格原子校验、异步下单和一人一单控制，降低高并发下数据库直接写入压力。
- 将商家运营查询、订单分析、优惠券分析、评价分析等业务 Service 封装为 Agent Tool，通过 Tool Registry 建立只读工具白名单，写操作采用草稿 + 商家确认机制。
- 构建 RAG、Workflow、Agent Eval、Preference Memory 与候选记忆确认闭环，实现商家知识问答、执行过程回放、行为评测和长期记忆可控写入。

## 能力边界

项目当前未实现 Multi-Agent、MCP、可配置 Workflow 引擎、LLM-as-Judge、模型 A/B 实验、LLM 自动长期记忆、向量 Memory、Summary Memory 和 Docker Compose 一键编排。
