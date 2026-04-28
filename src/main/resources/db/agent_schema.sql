/*
 Navicat Premium Data Transfer

 商家运营 Agent 模块表结构
 说明：
 1. 这些表接入原 hmdp 数据库，不单独拆库，方便 Agent 直接分析店铺、订单、优惠券、评价等业务数据。
 2. Agent 只负责分析、生成建议和活动草稿；创建真实活动必须经过商家确认后再执行。
 3. 金额字段延续项目原有习惯，统一使用“分”为单位，避免浮点精度问题。
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for tb_agent_session
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tb_agent_session` (
  `id` bigint(20) UNSIGNED NOT NULL COMMENT '主键，建议使用 RedisIdWorker 生成',
  `shop_id` bigint(20) UNSIGNED NOT NULL COMMENT '店铺ID',
  `merchant_id` bigint(20) UNSIGNED NOT NULL COMMENT '商家用户ID，学习阶段可先复用用户ID',
  `title` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '会话标题',
  `scene` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'operation_report' COMMENT '场景：operation_report / voucher_plan / review_reply',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1进行中，2已完成，3已关闭',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_shop_time`(`shop_id`, `create_time`) USING BTREE,
  INDEX `idx_merchant_time`(`merchant_id`, `create_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商家运营Agent会话表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tb_agent_message
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tb_agent_message` (
  `id` bigint(20) UNSIGNED NOT NULL COMMENT '主键，建议使用 RedisIdWorker 生成',
  `session_id` bigint(20) UNSIGNED NOT NULL COMMENT '会话ID',
  `shop_id` bigint(20) UNSIGNED NOT NULL COMMENT '店铺ID',
  `role` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '消息角色：user / assistant / tool / system',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '消息内容',
  `tool_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '工具名称，非工具消息为空',
  `tool_args` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '工具入参JSON',
  `tool_result` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '工具返回JSON',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_session_time`(`session_id`, `create_time`) USING BTREE,
  INDEX `idx_shop_time`(`shop_id`, `create_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商家运营Agent消息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tb_agent_suggestion
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tb_agent_suggestion` (
  `id` bigint(20) UNSIGNED NOT NULL COMMENT '主键，建议使用 RedisIdWorker 生成',
  `session_id` bigint(20) UNSIGNED NOT NULL COMMENT '会话ID',
  `shop_id` bigint(20) UNSIGNED NOT NULL COMMENT '店铺ID',
  `suggestion_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '建议类型：voucher / seckill / review / operation',
  `title` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '建议标题',
  `summary` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '建议摘要',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '完整建议内容',
  `confidence_score` decimal(5,2) DEFAULT NULL COMMENT '置信度，0-100',
  `risk_level` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '风险等级：1低，2中，3高',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1待确认，2已采纳，3已拒绝，4已执行',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_session`(`session_id`) USING BTREE,
  INDEX `idx_shop_status_time`(`shop_id`, `status`, `create_time`) USING BTREE,
  INDEX `idx_type_status`(`suggestion_type`, `status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商家运营Agent建议表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tb_agent_campaign_draft
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tb_agent_campaign_draft` (
  `id` bigint(20) UNSIGNED NOT NULL COMMENT '主键，建议使用 RedisIdWorker 生成',
  `suggestion_id` bigint(20) UNSIGNED NOT NULL COMMENT 'Agent建议ID',
  `shop_id` bigint(20) UNSIGNED NOT NULL COMMENT '店铺ID',
  `draft_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '草稿类型：voucher / seckill',
  `title` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '活动标题',
  `sub_title` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '活动副标题',
  `pay_value` bigint(20) UNSIGNED NOT NULL COMMENT '用户支付金额，单位分',
  `actual_value` bigint(20) UNSIGNED NOT NULL COMMENT '抵扣金额，单位分',
  `stock` int(11) UNSIGNED DEFAULT NULL COMMENT '库存，普通券可为空，秒杀券必填',
  `begin_time` timestamp NULL DEFAULT NULL COMMENT '活动开始时间',
  `end_time` timestamp NULL DEFAULT NULL COMMENT '活动结束时间',
  `rules` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '活动规则JSON',
  `reason` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT 'Agent推荐理由',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1待确认，2已创建，3已拒绝，4已过期',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_suggestion`(`suggestion_id`) USING BTREE,
  INDEX `idx_shop_status_time`(`shop_id`, `status`, `create_time`) USING BTREE,
  INDEX `idx_type_status`(`draft_type`, `status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商家运营Agent活动草稿表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tb_agent_action_log
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tb_agent_action_log` (
  `id` bigint(20) UNSIGNED NOT NULL COMMENT '主键，建议使用 RedisIdWorker 生成',
  `session_id` bigint(20) UNSIGNED DEFAULT NULL COMMENT '会话ID',
  `shop_id` bigint(20) UNSIGNED NOT NULL COMMENT '店铺ID',
  `operator_id` bigint(20) UNSIGNED DEFAULT NULL COMMENT '操作人ID',
  `operator_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '操作人类型：agent / merchant / system',
  `action_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '操作类型',
  `target_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '目标类型：voucher / seckill / suggestion / draft',
  `target_id` bigint(20) UNSIGNED DEFAULT NULL COMMENT '目标ID',
  `request_data` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '请求参数JSON',
  `result_data` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '执行结果JSON',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1成功，2失败',
  `error_msg` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '错误信息',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_session_time`(`session_id`, `create_time`) USING BTREE,
  INDEX `idx_shop_time`(`shop_id`, `create_time`) USING BTREE,
  INDEX `idx_target`(`target_type`, `target_id`) USING BTREE,
  INDEX `idx_action_status`(`action_type`, `status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商家运营Agent操作审计表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
