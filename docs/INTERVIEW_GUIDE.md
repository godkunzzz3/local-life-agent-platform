# 面试主线

## 1. 项目一句话介绍

这是一个基于黑马点评业务扩展的本地生活商家智能运营 Agent 平台：既保留秒杀、缓存、Feed 等 Java / Redis 业务，又让商家通过自然语言完成经营分析、知识问答、活动草稿和偏好记忆管理，而不是一个只会聊天的机器人。

## 2. 为什么做这个项目

原始黑马点评适合展示 Spring Boot、MyBatis-Plus 和 Redis 实战，但 AI 应用开发岗位还会关注 Tool Calling、RAG、Prompt、安全边界、评测和可观测性。

本项目没有丢掉原有业务，而是在真实订单、优惠券、评价和店铺数据上增加 Agent 工程层。商家可以用自然语言查询经营情况、调用只读工具、参考知识库、生成待确认活动草稿，并管理长期运营偏好。

核心目标是证明：我不仅能接入模型，还能把模型放进可控制、可测试、可追踪的 Java 业务系统。

## 3. 技术栈

### 后端

- Java 8、Spring Boot 2.3.12
- MyBatis-Plus、MySQL
- Hutool、Lombok

### 前端

- Vue 2、Element UI、Axios
- Nginx 静态页面与 `/api` 反向代理

### 中间件

- Redis String、Set、Sorted Set、BitMap、GEO
- Lua、Redis Stream、缓存重建互斥锁

### AI / Agent

- LangChain4j
- DashScope / 通义千问聊天模型与 Embedding
- Prompt 模板、Tool Calling、RAG、Workflow、Agent Eval
- Preference Memory、Memory Candidate、Human-in-the-loop

### 测试与工程化

- JUnit、Mockito、Maven
- GitHub Actions 自动执行 `mvn -B test`

## 4. 核心架构

```text
hmdp-web 商家工作台
        |
        | /api
        v
Nginx -> MerchantAgentController
        |
        v
MerchantAgentFacadeServiceImpl
        |
        +-- RAG：知识召回、重排、质量闸门
        +-- Tool Calling：工具白名单、只读执行
        +-- Memory：偏好加载、候选确认
        +-- Workflow：Run / Step 持久化
        +-- Eval：RAG Eval / Agent Eval
        |
        v
黑马点评业务 Service -> MySQL / Redis
```

普通业务接口继续服务用户端；Agent Facade 负责把会话、知识、工具、记忆和审计组合起来。模型不能绕过 Service 直接访问数据库。

## 5. Java / Redis 业务主线怎么讲

### 秒杀链路

请求进入后先执行 Lua。Lua 在 Redis 内原子完成库存判断、一人一单判断、库存预扣和订单消息写入 Stream。Java 请求线程快速返回订单 ID，后台消费者再完成数据库扣库存和订单落库。

### Lua 原子扣减

如果库存检查、重复下单检查和扣减分成多个 Redis 命令，并发请求可能在命令间插入。Lua 把这些动作放在一次原子执行中，避免超卖和重复抢购窗口。

### Redis Stream 异步下单

Stream 用于削峰和解耦请求线程与数据库写入。消费者组读取订单消息，成功后 ACK；异常消息通过 pending-list 继续处理。数据库更新仍保留库存条件作为最终兜底。

### 互斥控制与一人一单

Lua 在入口层按用户和优惠券做原子资格判断，落库阶段再次查询重复订单，并使用数据库条件扣减库存，形成 Redis 前置校验与数据库最终校验的双层保护。缓存重建场景则使用 Redis 互斥锁避免热点 Key 并发回源。

### 缓存一致性

店铺查询覆盖空值缓存防穿透、互斥锁或逻辑过期防击穿。更新店铺时先更新数据库，再删除缓存，让后续查询重建最新值。

### 达人探店

笔记发布后推送到粉丝 Feed，使用 Sorted Set 以时间戳为 score，查询时采用滚动分页解决同一时间分数重复问题；点赞榜同样利用 Sorted Set 排序。

## 6. Agent 主线怎么讲

### Tool Calling

项目把店铺档案、订单统计、优惠券分析、评价总结和经营诊断封装为工具。模型返回工具调用请求后，后端根据注册表找到对应工具并执行，再把结果交给模型生成答案。

### Tool Registry

注册表保存工具类型、是否可被模型调用、是否写数据库、是否需要商家确认和执行策略。`listModelCallableDefinitions` 只返回只读工具，白名单由后端控制，而不是相信模型传来的工具名。

### 写操作安全

活动类工具不会被暴露成可直接执行的模型工具。Agent 只能生成优惠券或秒杀券草稿；草稿要经过标题、金额、库存、时间等后端校验，商家确认后才创建真实活动。

### Human-in-the-loop

高风险动作的边界不是“Prompt 提醒模型”，而是后端状态机和接口权限。模型负责提出方案，人负责确认，Service 负责最终校验和写库。

### 操作审计

模型调用、工具调用、草稿动作、RAG 信息和失败原因会进入操作日志或 Workflow，便于判断是模型选错工具、参数错误、工具失败还是数据不足。

## 7. RAG 主线怎么讲

### 知识库链路

商家上传或维护知识文档后，后端按文本长度分片，调用 Embedding 生成向量并保存。查询时同时准备向量召回和关键词兜底，避免模型服务或向量存储不可用时整个知识问答失效。

### 召回与重排

召回结果经过规则重排，并结合相似度阈值判断可靠性。低于阈值时标记 `no_reliable_hit`，Prompt 不会把不可靠内容伪装成事实。

### 知识来源

调试接口和 Agent 响应可以展示命中文档、分片和分数，回答依据可追踪。

### RAG Eval

RAG Eval 使用持久化评测用例批量执行召回，统计 Top1、TopK 和无可靠召回等指标，并保存评测运行历史。它评的是知识检索质量，不等同于 Agent 行为评测。

## 8. Workflow / Eval 主线怎么讲

### 为什么做 Workflow Run / Step

只在响应中返回 `flowTrace` 无法支持历史回放和线上排障，因此项目将一次 Agent 执行保存为 Run，把 RAG、意图识别、工具选择、工具执行、模型调用、最终回答和 Memory 加载保存为 Step。

### 如何不影响主流程

Workflow Recorder 对记录操作做异常兜底，并使用独立事务思路隔离审计写入。日志失败只告警，不改变 Agent 原有返回语义；保存前还会截断和脱敏。

### Agent Eval

Agent Eval 第一版不调用真实模型，而是复用 `MerchantAgentRulePolicyService`，对固定用例评测：

- intent：意图是否符合预期
- tool：工具映射是否符合预期
- confirm：是否正确要求人工确认
- risk：风险等级是否正确

每次评测保存 case、run 和 result，可查看单项准确率、综合得分和失败诊断。

### 安全用例

安全集覆盖删除活动、退款、修改库存、取消订单、修改核销或支付状态、群发优惠券、超大规模秒杀、删除差评和查看用户隐私等输入。目标是确保它们被识别为高风险或禁止操作，且不会映射到可直接执行的只读工具。

## 9. Memory 主线怎么讲

### 聊天历史与长期 Memory

聊天历史解决当前会话中的上下文和指代；Preference Memory 保存跨会话的店铺长期偏好，例如活动文案风格、折扣边界和库存约束。

### Preference Memory

商家可以按店铺新增、编辑、启用、禁用和删除 Memory。普通 chat 和 Tool Calling 只加载启用记录，并限制条数与总长度。

### Prompt 注入与事实优先级

Memory 以“商家偏好记忆”段进入 Prompt。系统明确规定：Memory 是偏好或约束，不是订单、库存、评价等事实；冲突时必须以工具查询结果为准。

### Memory Candidate

第一版由规则从商家输入生成候选，候选状态为待确认，不会直接进入 Prompt。商家可以编辑、拒绝或删除；只有确认后才写入正式 Memory。

### 为什么不自动写入

长期记忆会影响后续多次对话。直接让模型写入容易把一次性表达、误判或敏感信息固化，因此项目采用“候选生成 + 商家确认”的 Human-in-the-loop。

## 10. 安全边界怎么讲

- Tool Registry 只向模型暴露只读工具，未知工具名和写工具名由后端拒绝。
- 创建真实优惠券或秒杀券必须经过草稿校验和商家确认。
- 禁止操作由规则组件识别，不依赖模型自觉。
- Memory 按 `shopId` 隔离，保存时校验长度并拦截手机号、验证码、token、apiKey、password、authorization 等敏感信息。
- 候选记忆不会直接进入 Prompt，确认后才进入正式 Memory。
- Workflow 只记录必要摘要，对输入输出做截断和脱敏，记录失败不影响主流程。
- Mockito 单测覆盖工具白名单、草稿校验、规则策略、Eval、Workflow、Memory 和候选状态流转；GitHub Actions 持续运行测试。

## 11. 简历 bullet

- 基于 Spring Boot、MyBatis-Plus、MySQL 与 Redis 扩展黑马点评业务，使用 Lua 与 Redis Stream 实现秒杀资格原子校验、异步下单及一人一单控制。
- 将店铺、订单、优惠券、评价等业务 Service 封装为 Agent Tool，通过 Tool Registry 建立只读白名单，写操作采用活动草稿与商家确认机制隔离模型和真实数据库。
- 构建商家知识库 RAG 链路，支持文档分片、Embedding、TopK 召回、关键词兜底、规则重排、相似度阈值和批量 RAG Eval。
- 设计 Workflow Run / Step 持久化与脱敏审计，记录 RAG、意图、工具、模型回复和 Memory 加载节点，且审计异常不阻断 Agent 主流程。
- 实现基于统一规则策略的 Agent Eval，离线评测意图、工具、人工确认和风险等级，并用安全用例验证高风险操作边界。
- 实现店铺级 Preference Memory 与候选确认闭环，支持 Prompt 注入、工具事实优先、敏感信息拦截及候选确认后写入长期记忆。

## 12. 高频追问 Q&A

### Q1：你的项目和普通黑马点评有什么区别？

普通版本重点是业务 CRUD 和 Redis。本项目保留这些能力，并增加 Tool Calling、RAG、Workflow、Agent Eval、Memory 和 Human-in-the-loop，把真实业务 Service 组织成可控的 Agent 系统。

### Q2：为什么不用模型直接操作数据库？

模型输出不稳定，也可能伪造工具名或参数。项目只允许模型调用后端白名单中的只读工具，真实写操作必须经过业务校验和人工确认。

### Q3：Tool Calling 的安全边界在哪里？

边界在 `AgentToolRegistry` 和执行层。注册表决定哪些工具 `modelCallable=true`，执行层再次拒绝未知工具和写工具，不能只靠 Prompt。

### Q4：为什么活动工具要先生成草稿？

草稿让商家看到标题、金额、库存和时间，并允许编辑。确认时后端复用同一套校验，再创建真实券，避免模型直接产生不可逆写操作。

### Q5：RAG 为什么需要 Eval？

能召回不代表召回正确。RAG Eval 用固定问题和期望文档统计 Top1、TopK、无可靠召回率，帮助判断分片、阈值和重排策略是否真的改善质量。

### Q6：RAG 的无可靠召回怎么处理？

召回结果低于阈值时标记 `no_reliable_hit`，不把低质量片段强行注入 Prompt，回答会明确知识不足或转向业务工具数据。

### Q7：为什么同时保留向量召回和关键词兜底？

Embedding 或向量存储可能不可用，且专有名词对关键词匹配更直接。双通道让链路更稳，并方便比较召回来源。

### Q8：Agent Eval 和 RAG Eval 有什么区别？

RAG Eval 评知识检索质量；Agent Eval 评行为决策，包括意图、工具、确认和风险。两者使用独立表和指标，不混在一起。

### Q9：为什么 Agent Eval 第一版不调用真实模型？

第一版目标是稳定验证确定性安全规则。真实模型有随机性、成本和环境依赖，适合后续单独做模型效果评测，而不是混进基础回归测试。

### Q10：Workflow 为什么要落库？

响应里的 trace 只存在一次请求中。Run / Step 落库后可以回放历史执行，定位错误发生在 RAG、意图、工具、模型还是 Memory 阶段。

### Q11：Workflow 写入失败会不会拖垮 Agent？

不会。Recorder 内部捕获异常，审计写入与主流程隔离；失败只记录日志，不改变原有业务响应。

### Q12：Workflow 如何防止泄露敏感信息？

保存前对 token、authorization、apiKey、验证码、手机号等内容脱敏，并对 userMessage、inputJson、outputJson、summary 和 errorMsg 截断。

### Q13：聊天历史和 Memory 有什么区别？

聊天历史服务当前会话上下文；Memory 是跨会话的长期偏好。历史消息不等于经过筛选、可长期复用的商家约束。

### Q14：Memory 为什么不能自动写入？

一次性表达可能被误认为长期偏好，模型也可能抽取错误或包含敏感信息。因此候选必须经过商家确认，才进入正式 Memory。

### Q15：Memory 会不会覆盖真实库存或订单数据？

不会。Prompt 明确规定工具结果优先，Memory 只能影响风格和运营约束，不能改变事实数据。

### Q16：Memory Candidate 是否使用大模型抽取？

第一版使用规则提取，不调用真实大模型。它追求可测试和可控，并为以后增加“模型生成候选但仍需确认”预留空间。

### Q17：秒杀怎么保证一人一单？

入口 Lua 使用用户与券的购买标记做原子判断，落库阶段再次查询订单并使用数据库条件扣减库存，形成双层校验。

### Q18：Lua 脚本解决了什么问题？

它把库存检查、重复下单检查、库存扣减和 Stream 写入放进一次 Redis 原子执行，消除多命令间的并发窗口。

### Q19：为什么用 Redis Stream 做异步下单？

它能削峰、支持消费者组、ACK 和 pending-list，适合本项目的轻量异步链路。生产规模更大时可以迁移到 Kafka 或 RocketMQ。

### Q20：数据库库存为什么还要再判断？

Redis 是高性能前置资格校验，MySQL 是最终事实源。数据库条件更新提供最后一道防超卖保护。

### Q21：缓存穿透和击穿怎么处理？

不存在的店铺写入短期空值防穿透；热点失效时使用互斥锁或逻辑过期控制缓存重建，避免大量请求同时打到数据库。

### Q22：Feed 为什么用 Sorted Set？

时间戳天然适合作为 score，能够按时间倒序读取，并通过最大时间和偏移量实现滚动分页。

### Q23：如何证明安全设计不是只写在文档里？

项目有 Mockito 单测验证模型工具白名单、写工具不可见、草稿非法字段、禁止操作、Memory 敏感信息和候选重复确认等边界，CI 会自动执行测试。

### Q24：如果继续迭代，你最先补什么？

优先补容器化复现、真实环境集成测试和更系统的模型效果评测；不会先堆 Multi-Agent 或 MCP，因为当前项目更需要稳定性和可复现性。

## 边界说明

当前未实现 LLM 自动抽取或自动写入长期 Memory、向量 Memory、Summary Memory、Multi-Agent、MCP、LLM-as-Judge、模型 A/B 实验、可配置 Workflow 引擎和 Docker Compose 一键编排。
