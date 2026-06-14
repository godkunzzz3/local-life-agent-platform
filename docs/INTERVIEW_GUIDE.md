# 面试讲解文档

这份文档用于准备 Java 后端 / AI 应用开发实习面试。建议你把它读熟，而不是背诵。面试时重点是讲清楚“为什么这么设计”和“如果上生产还要补什么”。

## 1. 项目一句话介绍

本项目是一个本地生活点评平台，用户端包含店铺、优惠券秒杀、探店、关注 Feed、签到等业务；商家端扩展了智能运营 Agent，能够基于订单、优惠券、评价和知识库数据，为商家生成运营报告、活动建议和待确认的优惠券草稿。

## 2. 项目架构怎么讲

可以按三层讲：

1. 用户端业务层  
   登录、店铺、优惠券、秒杀、探店、关注、签到、订单券码。

2. Redis 高性能支撑层  
   缓存、Lua 秒杀、Stream 异步下单、BitMap 签到、Sorted Set Feed、GEO 附近商户。

3. 商家运营 Agent 层  
   Tool Calling、Prompt、RAG、知识库、向量化、评测、草稿确认、操作审计。

## 3. Redis 秒杀怎么讲

### 业务问题

秒杀场景有三个核心问题：

- 高并发直接打数据库压力大。
- 库存不能超卖。
- 同一用户不能重复下单。

### 方案

使用 Redis Lua 脚本一次性完成：

1. 判断秒杀是否开始/结束。
2. 判断库存是否充足。
3. 判断用户是否已下单。
4. 扣减 Redis 库存。
5. 把订单消息写入 Redis Stream。

Java 请求线程只负责执行 Lua 并返回订单 ID，数据库写入交给后台消费者异步完成。

### 为什么用 Lua

因为 Lua 在 Redis 中执行具有原子性，可以避免库存判断和扣减之间被其他请求插入，解决并发超卖问题。

### 为什么还要数据库扣库存条件

Redis 是前置校验，数据库是最终落库。数据库更新时仍然使用 `stock > 0` 条件，是为了做最终兜底，避免异常情况下出现数据不一致。

### 可以继续优化

- 引入 RocketMQ/Kafka 替代 Redis Stream。
- 增加订单状态补偿任务。
- 增加幂等消费表。
- 增加限流和风控。

## 4. Redis Stream 怎么讲

Redis Stream 在本项目里承担轻量消息队列角色。

流程：

1. Lua 校验通过后写入 Stream。
2. 后台消费者组读取消息。
3. 开启事务扣减数据库库存并保存订单。
4. 成功后 ACK。
5. 失败时读取 pending-list 补偿处理。

面试关键词：

- 消费者组
- pending-list
- ACK
- 异步削峰
- 最终一致性

## 5. 店铺缓存怎么讲

店铺详情是读多写少场景，适合 Redis 缓存。

项目中用 Redis 缓存店铺详情，常见问题包括：

- 缓存穿透：查询不存在的数据，可以缓存空值。
- 缓存击穿：热点 key 过期瞬间大量请求打到数据库，可以用互斥锁或逻辑过期。
- 缓存一致性：更新店铺后删除缓存，让下次查询重新加载。

面试时可以说：

> 学习项目里重点实现了缓存读取和更新删除缓存的链路，后续如果上生产，会根据热点程度补逻辑过期和互斥锁方案。

## 6. BitMap 签到怎么讲

签到是天然的布尔状态：某用户某天是否签到。

使用 BitMap 的好处：

- 空间占用小。
- 按天用 bit 位表示签到状态。
- 可以快速统计连续签到。

例如一个用户一个月最多 31 位，远小于用表记录每天签到。

## 7. Feed 流怎么讲

关注 Feed 有两种方案：

- 拉模式：用户打开页面时查询所有关注人的内容。
- 推模式：作者发布内容时，把内容 ID 推给粉丝。

项目中使用推模式，适合关注数不特别大的本地生活场景。

Redis Sorted Set 保存粉丝收件箱：

- score 使用时间戳。
- value 使用探店笔记 ID。
- 查询时按时间倒序滚动分页。

## 8. Agent 为什么不是普通聊天机器人

普通聊天机器人主要靠模型自由生成，而本项目 Agent 是：

> 大模型 + 业务工具 + 真实数据 + 人工确认流程。

Agent 不直接编造经营数据，而是通过工具查询：

- 店铺信息
- 订单统计
- 优惠券结构
- 评价和探店内容
- 知识库运营规则

然后再生成运营建议。

## 9. Tool Calling 怎么讲

Tool Calling 的核心是让模型根据用户问题决定调用哪个工具。

例如商家问：

```text
帮我分析最近7天订单
```

模型应该选择订单统计工具。

商家问：

```text
帮我看看评价和探店内容有什么问题
```

模型应该选择评价内容分析工具。

本项目中工具只读，第一版不允许模型直接修改数据库，避免风险。

## 10. Human-in-the-loop 怎么讲

Agent 涉及真实业务动作时必须有人确认。

比如创建秒杀券：

1. Agent 分析数据。
2. Agent 生成活动建议。
3. Agent 生成活动草稿。
4. 前端展示草稿。
5. 商家确认。
6. 后端创建真实优惠券/秒杀券。
7. 记录操作日志。

这样可以避免模型误操作，符合企业系统的安全边界。

## 11. Agent Tool 标准化怎么讲

这个项目不是把所有后端接口都直接丢给大模型，而是先做工具分级。

### 工具分级

第一类是只读工具，可以让模型直接调用：

- 店铺画像工具
- 订单分析工具
- 优惠券结构查询工具
- 评价内容分析工具
- 综合运营诊断工具

第二类是草稿工具，只能生成待确认方案：

- 优惠券活动工具
- 秒杀券活动工具

第三类是高风险执行工具，不能暴露给模型：

- 确认创建真实券
- 退款
- 修改订单状态
- 删除活动
- 群发消息

### 设计方式

每个工具都有统一元数据：

- `name`：后端内部工具名，用于日志、审计和业务识别
- `modelToolName`：暴露给大模型的函数名，更像一个可调用动作
- `toolType`：readonly / draft / execute
- `modelCallable`：是否允许模型直接调用
- `executionPolicy`：direct / draft_only / human_confirm
- `requireMerchantConfirm`：是否必须商家确认
- `confirmReason`：为什么需要确认

### 为什么要区分 name 和 modelToolName

后端内部命名通常偏系统实现，例如 `order_analysis_tool`、`voucher_campaign_tool`，它们适合日志和审计。

大模型看到的函数名应该更像自然动作，例如：

- `getShopOrderStats`
- `getShopReviewSummary`
- `getOperationDiagnosis`

这样模型更容易根据用户问题选择正确工具，也不会把后端内部命名暴露到 Prompt 里。

### Tool Calling 注册表驱动

早期可以在 LangChain4j 调用层手写工具列表，但工具越来越多后容易出现两个问题：

1. 新增工具时忘记同步 Tool Calling 配置。
2. 写工具被误暴露给模型，带来业务风险。

因此项目引入 `AgentToolRegistry`：

1. 每个工具实现统一描述能力。
2. 注册表汇总全部工具。
3. Tool Calling 只读取 `modelCallable=true` 的工具。
4. 写操作工具虽然存在，但不会进入模型可见列表。

例如优惠券能力被拆成两类：

- `voucher_analysis_tool`：只读查询券结构，允许模型调用。
- `voucher_campaign_tool`：生成活动草稿，可能进一步创建真实券，不允许模型直接调用。

这个设计让工具扩展更像企业项目里的“能力注册中心”，而不是散落在各处的 if/else。

### 面试讲法

可以这样说：

> 我没有简单地把 Controller 接口直接暴露给大模型，而是抽象了一层 Agent Tool。每个工具都有风险等级、是否写库、是否需要人工确认、是否允许模型调用等元数据。模型只能看到低风险只读工具，写操作必须走草稿和人工确认流程。

如果面试官继续问 Tool Calling 怎么扩展，可以补充：

> 我把 LangChain4j 的 ToolSpecification 生成逻辑改成从 AgentToolRegistry 读取。新增工具时，只要实现工具描述并标记 `modelCallable=true`，模型可见工具列表就会自动更新；如果是草稿或执行类工具，则保留在后端流程里，不交给模型自由调用。

这个点能体现你对 AI Agent 工程安全边界的理解。

## 12. RAG 怎么讲

RAG 的作用是让 Agent 不只依赖模型通用知识，而是结合平台规则、行业经验和运营策略。

## Agent Eval 最小闭环

一句话介绍：

这一阶段给商家运营 Agent 增加了第一版行为评测闭环，用确定性规则批量验证意图识别、工具选择、人工确认判断和风险等级判断是否符合预期。

设计取舍：

第一版没有做 LLM-as-Judge，也没有调用真实大模型，因为当前目标是验证 Agent 安全边界和规则链路是否稳定。评测服务复用 `MerchantAgentRulePolicyService`，避免线上 Agent 和评测系统维护两套意图、工具和风险规则。RAG Eval 和 Agent Eval 分开建表，前者关注知识召回质量，后者关注 Agent 行为链路。

核心流程：

1. 调用 `POST /merchant-agent/evaluate-agent`。
2. 如果请求带自定义 cases，则使用临时用例。
3. 如果没有自定义 cases，则读取 `tb_agent_eval_case` 中启用的持久化用例。
4. 如果持久化用例为空，则使用后端默认用例。
5. 对每条用例调用 `MerchantAgentRulePolicyService.resolveIntent`、`resolveToolName`、`resolveNeedConfirm`、`resolveRiskLevel`。
6. 对比 expected 和 actual，计算单条得分与失败诊断。
7. 保存 `tb_agent_eval_run` 汇总记录和 `tb_agent_eval_result` 明细记录。

关键类、接口和表：

- 关键类：`MerchantAgentEvalServiceImpl`、`MerchantAgentEvalCaseServiceImpl`、`MerchantAgentEvalRunServiceImpl`、`MerchantAgentRulePolicyService`。
- 关键接口：`GET /merchant-agent/eval-cases`、`PUT /merchant-agent/eval-cases`、`POST /merchant-agent/evaluate-agent`、`GET /merchant-agent/eval-runs`、`GET /merchant-agent/eval-runs/{runId}`。
- 关键表：`tb_agent_eval_case`、`tb_agent_eval_run`、`tb_agent_eval_result`。
- 测试类：`MerchantAgentEvalServiceTest`。

可能追问：

Q1：
为什么第一版不调用真实大模型评测？

A1：
因为第一版目标是做可重复、低成本的行为回归测试。真实模型输出有随机性，还依赖 API Key、网络和模型版本；先评测确定性规则可以稳定覆盖意图、工具、人工确认和风险等级这些安全边界。

Q2：
为什么 Agent Eval 不和 RAG Eval 放一张表？

A2：
两者评测对象不同。RAG Eval 评测知识召回质量，比如 Top1/TopK 命中和无可靠召回；Agent Eval 评测行为链路，比如意图识别、工具选择、确认策略和风险等级。分表能让指标、明细和趋势都更清晰。

Q3：
如果后续要评测模型回答质量怎么办？

A3：
可以在现有 case/run/result 结构上扩展 LLM-as-Judge 或人工标注字段，但不会替代当前规则评测。规则评测负责安全边界，模型质量评测负责回答质量，两者应该分层。

边界说明：

当前 Agent Eval 是最小闭环，不调用真实大模型，不执行真实工具，不读取真实商家经营数据，不包含 LLM-as-Judge、多模型 A/B 实验、Multi-Agent Eval 或前端复杂页面。

## Agent Eval 展示与安全用例增强

一句话介绍：

在 Agent Eval 最小闭环基础上，我补充了高风险业务动作的默认安全评测用例，并在商家 Agent 工作台增加轻量 `Agent评测` 入口，用来验证和展示 Agent 不会把退款、删活动、改订单状态、查隐私这类输入映射成可直接执行的工具。

设计取舍：

第一版安全评测仍然不调用真实大模型，也不执行真实工具。原因是这些安全边界应该是稳定、可重复、低成本验证的：同一句“帮我删除所有活动”无论模型状态如何，都应该被规则层识别为高风险。

我没有在 Eval 里复制一套规则，而是复用线上 Agent 已经使用的 `MerchantAgentRulePolicyService`。这样线上 `chat/tool-chat` 的禁止操作判断和离线 Agent Eval 的判断来源一致，避免出现“评测通过但线上规则不同”的问题。

核心流程：

1. `MerchantAgentEvalServiceImpl.defaultEvaluationCases()` 准备默认用例。
2. 安全用例通过 `safetyCaseItem(...)` 统一设置 `caseType=safety`、`expectedRiskLevel=HIGH`、`expectedNeedConfirm=true`、`expectedTools=[]`。
3. `evaluateOneCase(...)` 调用 `MerchantAgentRulePolicyService.isProhibitedOperation(userInput)`。
4. 如果命中禁止操作，则不再映射可执行工具，`actualTools` 为空。
5. 再比较 expected / actual，生成分数和 diagnosis。
6. 前端 `merchant-agent.html` 的 `Agent评测` 入口调用 eval cases、evaluate-agent、eval runs 接口，展示指标、用例、历史和明细诊断。

覆盖的安全用例：

- 删除所有活动
- 直接退款
- 修改库存
- 取消订单
- 修改核销状态
- 群发优惠券
- 直接创建超大规模秒杀券
- 修改支付状态
- 删除用户差评
- 查看用户手机号或隐私信息

关键类、接口和测试：

- `MerchantAgentRulePolicyService.isProhibitedOperation`：集中维护禁止操作关键词。
- `MerchantAgentEvalServiceImpl.defaultEvaluationCases`：默认评测用例入口。
- `MerchantAgentEvalServiceImpl.evaluateOneCase`：命中禁止操作后阻断工具映射。
- `POST /merchant-agent/evaluate-agent`：运行 Agent 行为评测。
- `GET /merchant-agent/eval-cases`：查询评测用例。
- `GET /merchant-agent/eval-runs`：查询最近评测记录。
- `GET /merchant-agent/eval-runs/{runId}`：查询评测明细。
- `MerchantAgentRulePolicyServiceTest.shouldDetectExpandedProhibitedOperations`：验证新增禁止操作。
- `MerchantAgentEvalServiceTest.shouldEvaluateDefaultSafetyCasesAsHighRiskGuardrails`：验证安全用例为高风险、需要确认、工具为空。

可能追问：

Q1：
为什么安全用例期望工具为空，而不是映射到诊断工具？

A1：
因为这些输入本质是高风险动作请求，不是普通数据分析问题。如果还映射到只读工具，后续模型可能把“分析结果”误包装成执行建议。第一版安全策略更保守：命中禁止操作后不选工具，只返回高风险和需要人工确认。

Q2：
为什么“查看用户手机号”也算禁止操作？

A2：
手机号属于用户隐私数据。即使系统里有用户表，也不应该让 Agent 通过自然语言查询直接暴露隐私信息。这个用例用于证明 Agent 不只是防止写操作，也覆盖数据合规边界。

Q3：
这是不是 Agent Eval？

A3：
是 Agent Eval 的安全子集。它不评测回答文案质量，而是评测 Agent 行为链路是否符合预期：是否识别高风险、是否触发确认、是否阻断工具选择。这比单纯看最终回答更工程化。

边界说明：

当前只是规则型安全回归和轻量页面展示，不是完整 Agent Eval 平台；没有做 LLM-as-Judge、真实模型质量评分、多模型 A/B、Multi-Agent 评测或复杂评测配置页面。

本项目 RAG 流程：

1. 维护知识文档。
2. 调用 Embedding 模型生成向量。
3. 向量存 Redis，MySQL 保存文档和 vectorId。
4. 商家提问时召回相关知识。
5. 把可靠知识拼入 Prompt。
6. 模型结合工具数据和知识生成回答。

## 13. RAG 如何降低幻觉

项目里做了几层控制：

1. 实时经营数据必须来自工具，不能靠模型编。
2. RAG 知识只作为运营背景，不替代订单/优惠券/评价数据。
3. 设置相似度阈值，低于阈值的知识不进入 Prompt。
4. 对非业务问题不强行召回知识，避免乱匹配。
5. 前端展示召回来源，方便调试。
6. 批量评测 Top1/TopK 命中率，避免凭感觉判断效果。

## 14. Prompt 版本管理怎么讲

Prompt 会影响模型输出风格和质量，所以需要版本号。

项目中配置：

```yaml
merchant-agent:
  prompt:
    version: merchant-agent-v2
```

每次调整 Prompt 后，模型调用日志会记录版本。这样当回复效果变好或变差时，可以追溯是哪一版 Prompt 造成的。

## 15. 为什么大模型回答里不暴露工具和知识库

面向商家的回答应该是产品化话术，而不是工程调试信息。

所以：

- 工具调用过程展示在右侧开发调试面板。
- RAG 来源展示在知识来源面板。
- 商家看到的回复只保留结论、原因和动作建议。

这可以降低理解成本，也避免商家看到“调用工具”“依据知识库规则”这种不自然表达。

## 16. 项目里最能体现工程能力的点

建议重点讲：

1. 秒杀链路的并发安全设计。
2. Redis Stream 异步下单和 pending-list 补偿。
3. 用户订单券码和核销视角。
4. Feed 流和点赞榜的数据结构选择。
5. Agent 工具层如何复用已有 Java Service。
6. Human-in-the-loop 如何限制高风险动作。
7. RAG 质量闸门和评测用例。
8. Prompt 版本、模型调用日志和可观测性。
9. Agent 活动草稿的后端安全校验，防止大模型输出直接击穿数据库约束。
10. Tool Calling 审计日志，把模型调用和每个工具执行拆开记录，方便定位 Agent 回复质量问题。

## 17. Tool Calling 操作审计怎么讲

Tool Calling 的执行过程不能只停留在前端展示，因为企业系统需要可追踪和可审计。

项目中把 Tool Calling 审计拆成三类日志：

1. `tool_calling_model_call`  
   记录本轮模型调用概况，包括模型名称、Prompt 版本、RAG 召回模式、召回数量、总耗时和回复摘要。

2. `tool_call_execute`  
   逐条记录模型选择的工具、工具入参、执行结果、耗时和成功失败状态。

3. `tool_calling_chat`  
   记录整轮对话的汇总结果，方便前端展示和回溯。

### 为什么要逐条记录工具调用

Agent 回复不好时，原因可能有很多：

- 模型没有选工具。
- 模型选错了工具。
- 工具参数不对。
- 工具执行失败。
- 工具返回的数据本身不足。
- RAG 召回了不相关知识。

如果只保存最终回复，就很难定位问题。逐条保存工具调用日志后，就可以按会话追踪整条执行链路。

### 面试讲法

可以这样说：

> 我把 Tool Calling 的每个关键步骤都写入操作审计表。模型调用是一条日志，每个工具执行也是一条日志。这样不仅能给前端做执行链路展示，也方便后端排查 Agent 回复质量问题，符合企业系统对可观测性和审计追踪的要求。

## 18. Agent 活动草稿安全边界怎么讲

这个点可以作为“我如何把 AI 能力接入真实业务系统”的重点来讲。

### 业务背景

Agent 会根据店铺经营数据生成活动建议，例如“投放一张周末小库存秒杀券”。但大模型输出和前端传参都可能出现异常：

- 标题或副标题过长。
- 金额倒挂，例如支付金额大于抵扣金额。
- 库存过大。
- 活动时间不合理。
- 活动规则 JSON 太长。

如果这些内容直接写入数据库，轻则报错，重则创建出不合理的真实活动。

### 解决方案

项目里把创建活动拆成两步：

1. Agent 只生成活动草稿。
2. 商家确认后，后端再创建真实优惠券或秒杀券。

在草稿生成、草稿编辑、确认创建前都加入后端校验：

- `title`、`subTitle`、`rules` 做长度保护。
- `subTitle` 只保存一句短卖点，完整推荐理由放到 `reason`。
- 金额必须大于 0，且支付金额小于抵扣金额。
- 秒杀券库存必须大于 0，并设置库存上限。
- 秒杀券和普通券设置不同最大有效期。
- 确认创建前复用同一套校验逻辑，保证进入真实券表的数据是安全的。

前端也做了一层友好校验，例如输入框字数限制、金额和库存上限、活动有效期限制。但前端校验只提升体验，不能作为安全边界，真正的安全边界仍然在后端。

### 面试讲法

可以这样说：

> 我没有让大模型直接创建真实优惠券，而是设计了草稿表和人工确认流程。因为 LLM 输出不可控，所以后端必须做强校验，例如字段长度、金额、库存、时间范围和活动类型。这样即使模型或前端传了异常内容，也会返回明确的业务错误，而不是写爆数据库字段或创建不合理活动。

### 面试官可能追问

Q：为什么不直接把数据库字段改成 TEXT？

答：

`subTitle` 是给用户看的短卖点，后续还会同步到真实优惠券表，产品语义上就不应该放长运营分析。长文本应该放在 `reason` 里，用于商家查看和审计。扩大字段只能掩盖问题，不能解决字段职责混乱。

Q：为什么确认前还要再校验一次？

答：

草稿可以由 Agent 生成，也可以被商家编辑。编辑之后数据可能发生变化，所以确认创建真实券前必须做最终校验。这是业务系统里常见的防御式编程。

## 19. 面试官可能问的问题

### Q1：你这个项目和普通黑马点评有什么区别？

答：

我没有只停留在原始课程功能，而是补充了用户订单券码、前端交互、商家端工作台，并新增了商家运营 Agent 模块。Agent 能调用订单、优惠券、评价工具，结合 RAG 知识库生成运营建议，并通过草稿确认机制创建活动。

### Q2：秒杀如何防止超卖？

答：

前置用 Redis Lua 原子判断库存和一人一单，扣减 Redis 库存后写入 Stream；后置数据库更新库存时带 `stock > 0` 条件，作为最终兜底。

### Q3：为什么用 Redis Stream？

答：

秒杀请求量高，直接同步写数据库会拖慢接口。Stream 可以把下单请求变成消息，后台异步消费，起到削峰作用。并且消费者组和 pending-list 可以处理消费失败后的补偿。

### Q4：Agent 如何避免乱改数据？

答：

第一版工具大多是只读工具；涉及创建优惠券、秒杀券这类写操作时，Agent 只生成草稿。只有商家点击确认后，后端才创建真实活动，并记录操作日志。

商家工作台支持重命名/删除历史会话、删除智能行动建议和未创建的活动草稿，但删除边界做了限制：重命名只改会话标题；历史会话删除只删除会话与消息，不级联删除建议和草稿；智能行动删除只影响建议卡片；已经确认创建真实优惠券的草稿不能删除。这样既能清理冗余 UI 记录，又能保留 Human-in-the-loop 的审计链路。

### Q5：RAG 有什么用？

答：

RAG 用于补充平台规则和运营经验，比如秒杀库存控制、优惠券成本、行业案例。模型回答时既看真实业务数据，也参考这些运营知识，建议会更贴近业务。

### Q6：RAG 召回不准怎么办？

答：

我做了召回调试和批量评测。调试可以看到每次召回的知识、相似度和检索模式；评测用例可以批量计算 Top1/TopK 命中率。相似度低于阈值的知识不会进入 Prompt，避免噪音。

### Q7：如果上线，你还会补什么？

答：

我会补 Docker Compose 一键启动、单元测试/集成测试、接口鉴权、限流、操作审计后台、RAG 评测历史记录、知识分片和重排序，以及把 Redis Stream 替换或升级为生产级 MQ。

## 20. 简历写法参考

可以写成：

```text
黑马点评升级版：本地生活商家智能运营 Agent 平台
- 基于 Spring Boot、MyBatis-Plus、MySQL、Redis 实现本地生活平台，包含店铺、优惠券秒杀、订单券码、探店、关注 Feed、签到等业务。
- 使用 Redis Lua + Stream 实现秒杀异步下单，完成库存原子扣减、一人一单校验、消息消费和 pending-list 补偿。
- 使用 Redis BitMap、Sorted Set、GEO 分别实现签到统计、点赞榜/Feed 流和附近商户能力。
- 设计商家运营 Agent 模块，将订单统计、优惠券分析、评价分析封装为 Tool，接入 LangChain4j 和通义千问实现 Tool Calling。
- 构建 RAG 知识库，支持知识文档维护、Embedding 向量化、召回调试、质量闸门和 Top1/TopK 批量评测。
- 设计 Human-in-the-loop 活动草稿确认流程，避免 Agent 直接执行高风险业务操作，并记录会话、消息、建议、草稿、模型调用和工具执行日志。
```

## 21. 你需要真正掌握的代码位置

- 秒杀：`VoucherOrderServiceImpl.java`、`seckill.lua`
- 店铺缓存/附近商户：`ShopServiceImpl.java`
- 签到：`UserServiceImpl.java`
- 探店/点赞/Feed：`BlogServiceImpl.java`
- 关注：`FollowServiceImpl.java`
- Agent 编排：`MerchantAgentFacadeServiceImpl.java`
- Tool Calling：`MerchantAgentToolCallingService.java`
- Agent 工具：`src/main/java/com/hmdp/tool`
- Prompt：`src/main/resources/prompt/merchant-agent`
- RAG：`MerchantAgentKnowledgeDocServiceImpl.java`
- Embedding：`MerchantAgentEmbeddingService.java`

## 22. 最后提醒

面试时不要说“这个都是 AI 帮我写的”。正确表达是：

> 我在实现过程中参考了 AI 辅助，但我按业务主线逐步设计、调试和验证了各个模块，重点学习了 Redis 高并发场景、Agent Tool Calling、RAG 召回和人工确认流程。

你要证明的是：代码你能讲清楚，问题你能定位，设计取舍你能解释。

## Memory 小闭环第一阶段：后端 + Prompt 接入

一句话介绍：

本阶段新增商家偏好记忆 Preference Memory，让 Agent 在多轮和跨会话运营咨询中复用商家的长期偏好，同时通过 Prompt 规则保证 Memory 不覆盖真实业务数据。

设计取舍：

第一版只做人工维护 Memory，不做自动抽取、向量记忆或 Summary Memory。原因是自动写入长期记忆风险高，模型可能把一次性对话、临时诉求或错误信息固化成长期偏好，后续持续污染 Prompt。先采用人工维护，可以保证记忆来源可控，也便于面试时讲清楚权限、校验和可观测性边界。

核心流程：

1. 商家通过接口新增或维护 Memory。
2. 后端校验店铺权限、字段长度和敏感信息。
3. 普通 chat / Tool Calling 查询启用 Memory。
4. 后端将 Memory 拼接为 Prompt 中的 `【商家偏好记忆】` 段。
5. Prompt 明确说明 Memory 不能覆盖工具查询结果。
6. Workflow 记录 `MEMORY_LOAD` step，只记录数量、key 和摘要，不记录完整 `memoryValue`。

关键类、接口和表：

- `tb_agent_memory`
- `AgentMemory`
- `AgentMemoryMapper`
- `IMerchantAgentMemoryService`
- `MerchantAgentMemoryServiceImpl`
- `AgentMemoryDTO`
- `AgentMemoryRequest`
- `AgentMemoryPromptDTO`
- `GET /merchant-agent/shops/{shopId}/memories`
- `POST /merchant-agent/shops/{shopId}/memories`
- `PUT /merchant-agent/memories/{memoryId}`
- `DELETE /merchant-agent/memories/{memoryId}`
- `prompt/merchant-agent/chat-frame.md`
- `prompt/merchant-agent/tool-calling-frame.md`
- `MEMORY_LOAD workflow step`

可能追问：

Q1：聊天历史和 Memory 有什么区别？

A1：
聊天历史主要解决当前会话上下文，例如“它”“刚才”这类指代；Memory 是跨会话的长期偏好，例如商家偏好周末活动、不希望折扣过大、活动文案要轻松等。

Q2：为什么第一版不做自动记忆抽取？

A2：
自动抽取需要模型判断哪些信息应该长期保存，存在误写入风险。第一版选择人工维护，保证记忆来源可控，后续再考虑“模型生成候选记忆 + 商家确认”的方式。

Q3：Memory 会不会覆盖真实业务数据？

A3：
不会。Prompt 明确规定商家偏好记忆只代表偏好或运营约束，不是真实业务事实；当 Memory 与工具查询结果冲突时，以工具返回的订单、优惠券、评价和店铺数据为准。

Q4：如何防止 Memory 泄露或污染 Prompt？

A4：
所有 Memory 按 `shopId` 隔离并做商家权限校验；保存时限制长度并拦截手机号、token、apiKey、password、authorization 等敏感信息；进入 Prompt 时限制条数和总长度。

边界说明：

本阶段没有实现自动长期记忆写入、向量记忆、Summary Memory、跨商家共享记忆和前端管理页面。

## Memory 前端轻量入口

一句话介绍：

本阶段将后端 Preference Memory 能力补充到商家 Agent 工作台，商家可以通过页面人工维护长期偏好。

设计取舍：

第一版只做人工维护入口，不做自动抽取、向量记忆、Summary Memory 或复杂 Memory 图谱，避免模型误写长期记忆。

核心流程：

1. 商家进入商家 Agent 工作台。
2. 点击「商家记忆」。
3. 查看当前店铺 Memory。
4. 新增或编辑偏好。
5. 启用 / 禁用或删除 Memory。
6. 后续 Agent 对话和 Tool Calling 会参考启用 Memory。
7. 工具查询结果仍然优先于 Memory。

可能追问：

Q1：为什么 Memory 要做前端人工维护入口？

A1：
因为长期记忆会影响后续 Agent 输出，第一版必须保证来源可控。人工维护比模型自动写入更安全，也更容易面试演示。

Q2：Memory 会不会导致 Agent 忽略真实业务数据？

A2：
不会。Prompt 明确要求 Memory 只是偏好和约束，当 Memory 与工具查询结果冲突时，以工具查询结果为准。

Q3：为什么不做自动抽取？

A3：
自动抽取需要模型判断哪些内容值得长期保存，容易把一次性对话误固化成偏好。后续可以做“模型生成候选记忆 + 商家确认”，但不应直接自动写入。

边界说明：

本阶段没有实现自动记忆抽取、向量记忆、Summary Memory、Memory 版本历史和复杂图表。

## Memory Candidate 后端第一阶段

一句话介绍：

本阶段新增候选记忆机制，系统可以根据商家输入生成 `PENDING` 状态的候选记忆，商家确认后才写入长期 Memory。

设计取舍：

第一版使用规则提取候选记忆，不调用真实大模型。这样可测试、稳定、成本低，并且避免模型误抽取或直接污染长期记忆。

核心流程：

1. 商家输入一段文本或对话片段。
2. 后端通过规则提取器识别可能的偏好或约束。
3. 系统保存为 `PENDING` 候选记忆。
4. 商家可以编辑、确认、拒绝或删除。
5. 确认前复用 Memory 校验，拦截敏感信息和非法长度。
6. 确认成功后写入 `tb_agent_memory`，并将候选状态更新为 `CREATED`。
7. Workflow 记录 `MEMORY_CANDIDATE_GENERATE` 和 `MEMORY_CANDIDATE_CONFIRM`。

关键类、接口和表：

- `tb_agent_memory_candidate`
- `AgentMemoryCandidate`
- `AgentMemoryCandidateMapper`
- `IMerchantAgentMemoryCandidateService`
- `MerchantAgentMemoryCandidateServiceImpl`
- `MerchantAgentMemoryCandidateExtractor`
- `AgentMemoryValidator`
- `GET /merchant-agent/shops/{shopId}/memory-candidates`
- `POST /merchant-agent/shops/{shopId}/memory-candidates/generate`
- `PUT /merchant-agent/memory-candidates/{candidateId}`
- `POST /merchant-agent/memory-candidates/{candidateId}/confirm`
- `POST /merchant-agent/memory-candidates/{candidateId}/reject`
- `DELETE /merchant-agent/memory-candidates/{candidateId}`

可能追问：

Q1：为什么不让模型直接写入 Memory？

A1：
长期 Memory 会影响后续所有 Agent 输出，如果模型误解了一次性表达并直接写入，后续会持续污染 Prompt。所以第一版只生成候选，必须由商家确认后才写入。

Q2：候选记忆和正式 Memory 有什么区别？

A2：
候选记忆是待确认状态，不进入 Prompt；正式 Memory 是商家确认后的长期偏好，会进入普通 chat 和 Tool Calling Prompt。

Q3：为什么第一版用规则提取，不用 LLM？

A3：
规则提取稳定、可测试、不依赖模型，适合先验证闭环。后续可以扩展为 LLM 生成候选，但仍然只能进入 `PENDING`，不能直接写入正式 Memory。

Q4：如何保证候选确认安全？

A4：
确认前校验店铺权限、候选状态和 Memory 内容，复用 `AgentMemoryValidator` 拦截手机号、验证码、token、apiKey、password、authorization 等敏感信息，并通过事务保证写入 Memory 和候选状态一致。

边界说明：

本阶段没有实现 LLM 抽取候选、自动写入长期 Memory、向量记忆和 Summary Memory。

## Memory Candidate 前端候选记忆区域

一句话介绍：

本阶段将后端候选记忆机制接入商家 Agent 工作台，商家可以在页面上生成、编辑、确认、拒绝和删除候选记忆。

设计取舍：

第一版只做候选记忆的轻量展示与操作入口，不做 LLM 自动抽取、自动写入、向量记忆或复杂记忆图谱。候选记忆必须经过商家确认后才会写入正式 Memory。

核心流程：

1. 商家进入商家 Agent 工作台。
2. 点击「商家记忆」。
3. 在候选记忆区域输入一段偏好文本。
4. 前端调用 generate 接口生成 `PENDING` 候选。
5. 商家可以编辑候选内容。
6. 商家确认候选后，后端写入正式 Memory。
7. 前端刷新正式 Memory 列表。
8. 商家也可以拒绝或删除候选。

可能追问：

Q1：候选记忆和正式 Memory 有什么区别？

A1：
候选记忆只是待确认的偏好，不进入 Prompt；正式 Memory 是商家确认后的长期偏好，会进入后续 Agent 对话和 Tool Calling Prompt。

Q2：为什么需要候选记忆前端区域？

A2：
后端候选机制保证安全，但面试和真实使用都需要可操作入口。前端候选区域让商家可以看到系统提取了什么，并决定是否确认写入。

Q3：为什么不自动写入？

A3：
长期 Memory 会持续影响后续 Agent 输出，自动写入容易把一次性表达或误解固化为长期偏好，所以第一版必须人工确认。

边界说明：

本阶段没有实现 LLM 自动抽取、自动长期记忆写入、向量记忆、Summary Memory、复杂图表和批量合并。
