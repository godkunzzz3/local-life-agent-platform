# 黑马点评升级版：本地生活商家智能运营 Agent 平台

这是一个基于黑马点评实战项目扩展的本地生活平台后端项目。项目保留了用户端店铺、优惠券、秒杀、探店、关注、签到等核心业务，并在此基础上新增了面向商家的智能运营 Agent 模块。

项目目标不是单纯做一个聊天机器人，而是把已有本地生活业务能力封装成 Agent 可调用的工具，让 Agent 基于真实业务数据为商家生成可执行、可追踪、可确认的运营建议。

## 项目亮点

- Redis 实战完整链路：缓存、分布式锁、Lua 秒杀、Stream 异步下单、BitMap 签到、Feed 流、GEO 附近商户。
- 优惠券秒杀闭环：库存校验、一人一单、异步下单、订单券码、商家核销视角。
- 达人探店与社交能力：发布探店笔记、点赞、点赞榜、关注、共同关注、关注 Feed。
- 商家运营 Agent：自然语言对话、工具调用、Prompt 模板、RAG 知识库、向量化、评测用例、活动草稿确认。
- Human-in-the-loop：Agent 只生成建议和草稿，创建真实优惠券/秒杀券必须由商家确认。
- 可追踪能力：会话、消息、建议、草稿、操作日志、模型调用信息、RAG 召回信息均可记录和展示。

## 项目截图

### 用户端店铺详情与优惠券

![用户端店铺详情与优惠券](docs/assets/shop-detail.png)

### 商家运营 Agent 工作台

![商家运营 Agent 工作台](docs/assets/merchant-agent-overview.png)

### RAG 知识库召回与批量评测

![RAG 知识库召回与批量评测](docs/assets/rag-evaluation.png)

## 技术栈

- Java 8
- Spring Boot 2.3.12
- MyBatis-Plus
- MySQL
- Redis
- Lua
- Redis Stream
- Hutool
- LangChain4j
- DashScope / 通义千问
- Vue 2 + Element UI
- Nginx 静态前端

## 核心业务模块

### 用户端业务

- 手机号验证码登录
- 用户资料编辑
- 店铺分类与店铺详情
- 附近商户与排序筛选
- 普通代金券购买
- 秒杀券抢购
- 用户订单与券码展示
- 签到与连续签到统计
- 达人探店笔记发布
- 探店点赞与点赞榜
- 关注、取关、共同关注
- 关注 Feed 流

### Redis 实战能力

| 场景 | Redis 能力 |
| --- | --- |
| 店铺缓存 | String 缓存、缓存穿透处理 |
| 秒杀下单 | Lua 原子扣减、一人一单校验 |
| 异步下单 | Redis Stream 消息队列 |
| 签到 | BitMap |
| 关注关系 | Set |
| Feed 流 | Sorted Set |
| 附近商户 | GEO |

### 商家运营 Agent 模块

Agent 模块面向商家，支持：

- 查询店铺经营报告
- 分析订单表现
- 分析优惠券结构
- 分析评价和探店内容
- 生成运营建议
- 生成优惠券/秒杀券活动草稿
- 商家确认后创建真实活动
- 活动效果复盘
- RAG 知识库维护
- 知识文档向量化
- RAG 召回调试
- RAG 批量评测和评测用例持久化

## Agent 设计原则

本项目中的 Agent 遵守一个核心原则：

> Agent 负责分析、建议和准备方案；商家负责确认关键动作。

因此，Agent 不会直接执行高风险操作：

- 不自动退款
- 不自动取消订单
- 不自动删除活动
- 不自动群发消息
- 不绕过商家确认创建真实优惠券或秒杀券
- 不修改支付、核销、库存等高风险状态

## Agent 调用流程

```mermaid
flowchart TD
    A["商家输入问题"] --> B["识别业务意图"]
    B --> C["RAG 检索运营知识"]
    C --> D["模型选择只读工具"]
    D --> E["查询店铺/订单/优惠券/评价数据"]
    E --> F["生成商家可读回复"]
    F --> G{"是否需要创建活动?"}
    G -- "否" --> H["返回分析结果"]
    G -- "是" --> I["生成活动草稿"]
    I --> J["商家确认"]
    J --> K["创建真实优惠券或秒杀券"]
    K --> L["记录操作日志"]
```

## 目录结构

```text
src/main/java/com/hmdp
  agent       Agent 模型、Prompt、Tool Calling、Embedding
  controller 业务接口和商家 Agent 接口
  dto        请求响应 DTO、Agent 上下文 DTO
  entity     MySQL 实体
  mapper     MyBatis-Plus Mapper
  service    Service 接口
  service/impl 业务实现和 Agent 编排
  tool       可被 Agent 调用的业务工具
  utils      Redis、ID、用户上下文等工具

src/main/resources
  db         初始化 SQL、Agent 表结构、演示数据
  prompt     Agent Prompt 模板
  seckill.lua 秒杀 Lua 脚本
```

## 数据库脚本

核心 SQL 文件：

- `src/main/resources/db/hmdp.sql`：黑马点评基础业务表。
- `src/main/resources/db/merchant_schema.sql`：商家账号相关表。
- `src/main/resources/db/agent_schema.sql`：Agent 会话、消息、建议、草稿、知识库、评测用例等表。
- `src/main/resources/db/seed_shop_demo.sql`：店铺演示数据。
- `src/main/resources/db/seed_voucher_demo.sql`：优惠券演示数据。
- `src/main/resources/db/seed_agent_knowledge.sql`：Agent RAG 知识库种子数据。

## 本地启动

### 1. 准备依赖

需要本地启动：

- MySQL
- Redis
- Nginx

默认配置见 `src/main/resources/application.yaml`：

```yaml
server:
  port: 8081
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/hmdp?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: 123456
  redis:
    host: 127.0.0.1
    port: 6379
```

### 2. 导入数据库

按顺序导入：

```bash
mysql -uroot -p hmdp < src/main/resources/db/hmdp.sql
mysql -uroot -p hmdp < src/main/resources/db/merchant_schema.sql
mysql -uroot -p hmdp < src/main/resources/db/agent_schema.sql
mysql -uroot -p hmdp < src/main/resources/db/seed_shop_demo.sql
mysql -uroot -p hmdp < src/main/resources/db/seed_voucher_demo.sql
mysql -uroot -p hmdp < src/main/resources/db/seed_agent_knowledge.sql
```

### 3. 配置大模型 API Key

Agent 可以在没有 Key 的情况下使用部分规则兜底能力；如需真实模型和向量化能力，需要配置环境变量：

```bash
export DASHSCOPE_API_KEY=你的百炼APIKey
```

不要把 API Key 提交到 Git。

### 4. 启动后端

本机如果没有全局 Maven，可以使用 IntelliJ 自带 Maven：

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn spring-boot:run
```

后端地址：

```text
http://localhost:8081
```

### 5. 启动前端

前端静态页面位于另一个目录：

```text
/Users/qjkzzz3/Documents/nginx-1.18.0/html/hmdp
```

Nginx 默认访问：

```text
http://localhost:8080/login.html
```

商家端工作台：

```text
http://localhost:8080/merchant-agent.html
```

## 典型接口

### 用户端

- `POST /user/code` 发送验证码
- `POST /user/login` 手机号登录
- `GET /shop/{id}` 查询店铺详情
- `GET /shop/of/type` 按分类查询店铺
- `POST /voucher-order/seckill/{id}` 秒杀券下单
- `GET /voucher-order/my` 查询我的订单
- `POST /user/sign` 签到
- `GET /user/sign/count` 连续签到统计
- `POST /blog` 发布探店笔记
- `PUT /blog/like/{id}` 点赞探店笔记
- `GET /blog/of/follow` 查询关注 Feed

### 商家 Agent

- `POST /merchant-agent/shops/{shopId}/operation-report` 生成经营报告
- `POST /merchant-agent/shops/{shopId}/chat` Agent 对话
- `POST /merchant-agent/shops/{shopId}/tool-chat` LangChain4j Tool Calling 对话
- `GET /merchant-agent/shops/{shopId}/sessions` 查询会话
- `GET /merchant-agent/sessions/{sessionId}/messages` 查询消息
- `GET /merchant-agent/shops/{shopId}/suggestions` 查询建议
- `POST /merchant-agent/suggestions/{suggestionId}/drafts` 生成活动草稿
- `POST /merchant-agent/drafts/{draftId}/confirm` 确认创建活动
- `GET /merchant-agent/knowledge-docs` 查询知识库
- `POST /merchant-agent/knowledge-docs` 新增知识
- `POST /merchant-agent/knowledge-docs/{docId}/vectorize` 单条向量化
- `POST /merchant-agent/knowledge-docs/retrieve-debug` RAG 召回调试
- `POST /merchant-agent/knowledge-docs/evaluate` RAG 召回评测
- `GET /merchant-agent/knowledge-docs/evaluate-cases` 查询评测用例
- `PUT /merchant-agent/knowledge-docs/evaluate-cases` 保存评测用例

## 面试讲解关键词

这个项目可以重点讲下面几条主线：

1. Redis 秒杀如何防止超卖和一人多单。
2. 为什么用 Lua 保证库存扣减和一人一单的原子性。
3. 为什么用 Redis Stream 做异步下单。
4. 店铺缓存如何处理穿透、击穿和一致性。
5. Feed 流为什么用 Sorted Set。
6. 签到为什么用 BitMap。
7. Agent Tool Calling 如何把 Java Service 包装成工具。
8. 为什么 Agent 高风险动作必须 Human-in-the-loop。
9. RAG 为什么需要质量闸门和召回评测。
10. Prompt 版本和模型调用日志如何帮助排查效果变化。

## 当前状态

当前项目已经具备可演示的完整主线：

- 用户端本地生活业务闭环
- Redis 实战功能闭环
- 商家端 Agent 工作台
- RAG 知识库与评测闭环

后续可继续增强：

- 增加单元测试和集成测试
- 增加 Docker Compose 一键启动
- 增加线上部署脚本
- 增加 RAG 评测历史记录
- 增加知识文档分片与重排序
- 增加更完整的商家权限体系
