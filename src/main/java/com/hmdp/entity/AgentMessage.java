package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商家运营 Agent 消息。
 *
 * <p>消息表保存用户、助手、系统和工具调用的完整上下文。后续接入 LangChain4j
 * 或 Spring AI 时，可以把这些记录作为对话记忆，也可以用于排查 Agent 为什么做出某个建议。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_agent_message")
public class AgentMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键。项目内统一使用 RedisIdWorker 生成。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 所属 Agent 会话 ID。
     */
    private Long sessionId;

    /**
     * 冗余店铺 ID，便于按店铺查询消息和做数据隔离。
     */
    private Long shopId;

    /**
     * 消息角色：user / assistant / tool / system。
     */
    private String role;

    /**
     * 消息正文。工具消息可保存工具执行摘要。
     */
    private String content;

    /**
     * 工具名称。非工具消息为空。
     */
    private String toolName;

    /**
     * 工具入参 JSON。用于审计和问题复现。
     */
    private String toolArgs;

    /**
     * 工具返回 JSON。注意后续如包含敏感数据，需要在写入前做脱敏。
     */
    private String toolResult;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;
}
