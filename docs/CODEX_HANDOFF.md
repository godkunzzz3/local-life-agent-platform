# Codex 项目交接记录

> 用途：换 Codex 账号、换机器或长时间中断后，快速恢复本项目上下文。
> 新账号接手时，请先阅读本文件，再阅读 `README.md`、`docs/INTERVIEW_GUIDE.md` 和 `docs/DEMO_SCRIPT.md`。

## 1. 项目定位

项目名称：本地生活商家智能运营 Agent 平台。

项目面向本地生活点评类业务域，目前包含：

- 用户端：店铺、优惠券、秒杀、订单券码、探店、关注、签到、附近商户。
- 商家端：商家登录、经营看板、商家运营 Agent 工作台、活动草稿确认。
- AI Agent：Tool Calling、RAG 知识库、Prompt 管理、模型调用日志、操作审计。

项目目标不是单纯做聊天机器人，而是：

> 把本地生活业务能力封装成 Agent 可调用的工具，让 Agent 基于真实业务数据为商家生成可执行、可追踪、可确认的运营建议。

## 2. 重要目录

后端项目：

```text
hm-dianping
```

前端项目：

```text
hmdp-web
```

本地路径请按个人开发环境调整。

后端核心文档：

```text
README.md
docs/INTERVIEW_GUIDE.md
docs/DEMO_SCRIPT.md
docs/CODEX_HANDOFF.md
docs/软考高级系统架构师论文模板-本地生活商家智能运营Agent平台.docx
```

## 3. 当前已完成能力

### 3.1 用户端业务

- 手机号验证码登录。
- 用户资料编辑。
- 店铺分类、店铺列表、店铺详情。
- 普通代金券购买。
- 秒杀券抢购。
- 订单列表、订单详情、券码展示。
- 商家核销视角雏形。
- 达人探店笔记发布。
- 探店点赞和点赞榜。
- 关注、取关、共同关注。
- 关注 Feed 流。
- 用户签到和连续签到统计。
- 附近商户 GEO 查询。

### 3.2 Redis 实战能力

- 店铺缓存。
- Redis Lua 秒杀库存扣减。
- 一人一单校验。
- Redis Stream 异步下单。
- pending-list 异常补偿。
- BitMap 签到。
- Set 关注关系。
- Sorted Set Feed 流。
- GEO 附近商户。

### 3.3 商家端页面

- 商家登录入口。
- 商家运营首页。
- 商家运营 Agent 对话页。
- Agent 模型来源展示。
- Tool Calling 执行链路展示。
- RAG 知识来源展示。
- 活动草稿编辑和确认。
- RAG 评测趋势图和调试页初版。

前端改动后通常需要执行：

```bash
nginx -s reload
```

### 3.4 Agent 后端能力

- 商家 Agent 会话、消息、建议、草稿、操作日志基础表和 CRUD。
- 经营报告接口。
- Agent 对话接口。
- 活动建议、活动草稿、确认创建真实券。
- Prompt 模板管理。
- Prompt 版本号和模型调用日志。
- LangChain4j + 通义千问模型接入。
- Tool Calling 初版。
- Tool 注册表 `AgentToolRegistry`。
- 只读 Tool 与写 Tool 风险分级。
- RAG 知识文档维护。
- Embedding 向量化。
- RAG 召回调试。
- RAG 质量闸门。
- RAG 批量评测。
- Rerank 初版。

## 4. 最近一次开发进度

最近完成的主线：Agent Tool Calling 注册表驱动化 + 操作审计增强。

核心提交：

```text
165e4f2 drive tool calling specs from registry
```

本轮新增改动：

- Tool Calling 成功后会记录 `tool_calling_model_call`，保存模型、Prompt、RAG、耗时和回复摘要。
- Tool Calling 成功后会把每个工具调用拆成 `tool_call_execute` 审计日志。
- 每条工具日志包含工具名、入参、执行结果、耗时、成功失败状态。
- `targetType=session/tool/model` 都补充了中文展示名称，方便前端和面试演示。

本次做了：

- `AgentToolDefinitionDTO` 增加 `modelToolName`。
- 区分后端内部工具名和大模型可见函数名。
- `MerchantAgentToolCallingService` 不再手写工具列表，而是从 `AgentToolRegistry` 读取模型可调用工具。
- 新增只读优惠券结构工具 `voucher_analysis_tool`。
- 保留写工具 `voucher_campaign_tool`，但不暴露给模型。
- 模型可调用工具只包含低风险只读工具。

当前模型可调用工具包括：

```text
getOperationDiagnosis
getShopOrderStats
getShopReviewSummary
getShopProfile
getShopVouchers
```

不允许模型直接调用：

```text
createCampaignDraft
confirmCampaignDraft
退款
取消订单
修改支付状态
修改核销状态
删除活动
群发消息
```

## 5. 当前工作区状态

换号前应执行：

```bash
cd hm-dianping
git status

cd hmdp-web
git status
```

如果有未提交内容，优先提交后端和前端仓库。

## 6. 启动方式

后端常用启动：

```bash
cd hm-dianping
mvn spring-boot:run
```

如果 8081 被占用，可以临时换端口：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18081
```

查看端口占用：

```bash
lsof -i :8081
```

前端静态页面由本地 Nginx 提供，常用地址：

```text
http://localhost:8080/login.html
http://localhost:8080/merchant-agent.html
```

## 7. 环境变量

推荐使用 JDK 17 构建和运行。基础业务代码源于 Java 8 / Spring Boot 2.3 生态，但当前 DashScope / Agent 依赖需要 Java 17 兼容。

数据库密码、模型 API Key 和图片上传目录使用环境变量配置，不要写死进代码：

```bash
export MYSQL_PASSWORD='你的本地 MySQL 密码'
export DASHSCOPE_API_KEY='你的key'
export HMDP_IMAGE_UPLOAD_DIR='/your/path/'
```

图片目录也可以通过 JVM 参数 `-Dhmdp.image-upload-dir=/your/path/` 配置。

如果通过 IntelliJ 启动，需要在 Run/Debug Configuration 的 Environment variables 中配置。

## 8. 下一步建议

最近新增：

- 商家端支持删除历史会话，后端只清理 `tb_agent_session` 和 `tb_agent_message`，不会级联删除建议、草稿和审计日志。
- 历史会话列表改为 GPT 风格的三点菜单，支持重命名和删除；重命名只修改会话标题，不影响消息、建议、草稿和审计。
- 商家端支持删除智能行动建议，删除后仅影响建议卡片展示，已生成的草稿继续保留。
- 商家端支持删除单个未创建草稿，以及一键清空未创建草稿；状态为“已创建”的草稿会跳过，避免破坏真实优惠券的审计链路。

建议按这个顺序继续：

1. 优化 Tool Calling 执行链路可视化：前端更清晰展示模型选择工具、后端执行工具、RAG 召回、最终回复。
2. 完善 RAG 评测持久化：把每次评测结果、Top1/TopK、失败原因、Rerank 结果保存并可视化。
3. 完善商家端页面体验：建议卡片、草稿弹窗、确认流程和运营看板继续打磨。
4. 整理简历和面试话术：围绕 Redis 高并发、Agent Tool Calling、RAG 和 Human-in-the-loop 讲项目亮点。
5. 补自动化测试：对秒杀、草稿确认、Tool 注册表、RAG 召回阈值做单元或接口测试。

## 9. 新 Codex 账号接手提示词

换号后可以直接复制下面这段给新 Codex：

```text
我正在开发“本地生活商家智能运营 Agent 平台”。

请先阅读以下文件，恢复项目上下文：
- README.md
- docs/INTERVIEW_GUIDE.md
- docs/DEMO_SCRIPT.md
- docs/CODEX_HANDOFF.md

当前主线是：继续完善 Agent Tool Calling、RAG 和企业级可追踪能力。

请先不要大改代码，先总结：
1. 当前项目已经完成了什么；
2. Agent 模块现在做到哪一步；
3. 下一步最适合做什么；
4. 如果要改代码，请边改边解释业务逻辑，并同步更新文档。
```

## 10. 学习重点

目前项目最值得复习和面试讲解的点：

- Redis Lua 如何解决秒杀超卖。
- Redis Stream 如何实现异步下单和 pending-list 补偿。
- BitMap、Sorted Set、Set、GEO 在本地生活业务里的使用。
- Agent 为什么不是普通聊天机器人。
- Tool Calling 如何把 Java Service 包装成大模型可调用工具。
- 为什么只读 Tool 可以给模型调用，写 Tool 必须走人工确认。
- RAG 如何结合平台规则和真实业务数据降低幻觉。
- Prompt 版本和模型调用日志为什么重要。
- Human-in-the-loop 如何保证 AI 系统安全落地。
