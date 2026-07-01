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

-- ----------------------------
-- Table structure for tb_agent_knowledge_doc
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tb_agent_knowledge_doc` (
  `id` bigint(20) UNSIGNED NOT NULL COMMENT '主键，建议使用 RedisIdWorker 生成',
  `shop_id` bigint(20) UNSIGNED DEFAULT NULL COMMENT '店铺ID，NULL表示全局公共知识，非空表示店铺私有知识',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '标题',
  `category` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '分类：voucher_rule / seckill_rule / industry_case / risk_rule / cost_rule',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '知识正文',
  `vector_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '向量库文档ID，第一版可为空',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_agent_knowledge_shop_status_category`(`shop_id`, `status`, `category`) USING BTREE,
  INDEX `idx_category_status_time`(`category`, `status`, `update_time`) USING BTREE,
  INDEX `idx_status_time`(`status`, `update_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商家运营Agent知识库文档表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tb_agent_knowledge_eval_case
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tb_agent_knowledge_eval_case` (
  `id` bigint(20) UNSIGNED NOT NULL COMMENT '主键，建议使用 RedisIdWorker 生成',
  `message` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '测试问题',
  `intent` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'operation_chat' COMMENT '业务意图：voucher_plan / order_analysis / review_analysis / operation_chat',
  `expected_categories` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '期望命中的知识分类JSON数组',
  `sort_order` int(11) UNSIGNED NOT NULL DEFAULT 1 COMMENT '排序号',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_status_sort`(`status`, `sort_order`) USING BTREE,
  INDEX `idx_intent_status`(`intent`, `status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商家运营Agent RAG评测用例表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tb_agent_knowledge_eval_run
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tb_agent_knowledge_eval_run` (
  `id` bigint(20) UNSIGNED NOT NULL COMMENT '主键，建议使用 RedisIdWorker 生成',
  `case_source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'default' COMMENT '用例来源：custom/persisted/default/default_fallback',
  `limit_count` int(11) UNSIGNED NOT NULL DEFAULT 3 COMMENT '本次评测使用的 TopK 数量',
  `total_count` int(11) UNSIGNED NOT NULL DEFAULT 0 COMMENT '评测用例总数',
  `top1_pass_count` int(11) UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Top1 命中数量',
  `topk_pass_count` int(11) UNSIGNED NOT NULL DEFAULT 0 COMMENT 'TopK 命中数量',
  `no_reliable_hit_count` int(11) UNSIGNED NOT NULL DEFAULT 0 COMMENT '无可靠召回数量',
  `top1_pass_rate` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0.00%' COMMENT 'Top1 命中率',
  `topk_pass_rate` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0.00%' COMMENT 'TopK 命中率',
  `vector_min_similarity` decimal(8,4) NOT NULL DEFAULT 0.0000 COMMENT '本次评测使用的向量相似度最低阈值',
  `result_snapshot` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '完整评测结果JSON快照',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE,
  INDEX `idx_case_source_time`(`case_source`, `create_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商家运营Agent RAG评测运行记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tb_agent_eval_case
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tb_agent_eval_case` (
  `id` bigint(20) UNSIGNED NOT NULL COMMENT '主键，使用 RedisIdWorker 生成',
  `case_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用例名称',
  `user_input` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '商家输入问题',
  `expected_intent` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '期望意图',
  `expected_tools` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '期望工具名JSON数组',
  `expected_need_confirm` tinyint(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否期望触发人工确认：0否，1是',
  `expected_risk_level` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'LOW' COMMENT '期望风险等级：LOW/MEDIUM/HIGH',
  `expected_keywords` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '期望命中关键词JSON数组，第一版可选',
  `case_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'rule' COMMENT '用例类型：rule/safety/tool/confirm',
  `sort_order` int(11) UNSIGNED NOT NULL DEFAULT 1 COMMENT '排序号',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_status_sort`(`status`, `sort_order`) USING BTREE,
  INDEX `idx_intent_status`(`expected_intent`, `status`) USING BTREE,
  INDEX `idx_case_type_status`(`case_type`, `status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商家运营Agent行为评测用例表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tb_agent_eval_run
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tb_agent_eval_run` (
  `id` bigint(20) UNSIGNED NOT NULL COMMENT '主键，使用 RedisIdWorker 生成',
  `case_source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'persisted' COMMENT '用例来源：custom/persisted/default',
  `total_count` int(11) UNSIGNED NOT NULL DEFAULT 0 COMMENT '评测用例总数',
  `pass_count` int(11) UNSIGNED NOT NULL DEFAULT 0 COMMENT '通过用例数',
  `fail_count` int(11) UNSIGNED NOT NULL DEFAULT 0 COMMENT '失败用例数',
  `intent_accuracy` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0.00%' COMMENT '意图识别准确率',
  `tool_accuracy` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0.00%' COMMENT '工具匹配准确率',
  `confirm_accuracy` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0.00%' COMMENT '人工确认判断准确率',
  `risk_accuracy` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0.00%' COMMENT '风险等级准确率',
  `overall_score` decimal(6,2) NOT NULL DEFAULT 0.00 COMMENT '综合得分，0-100',
  `summary` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '评测摘要',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE,
  INDEX `idx_case_source_time`(`case_source`, `create_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商家运营Agent行为评测运行记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tb_agent_eval_result
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tb_agent_eval_result` (
  `id` bigint(20) UNSIGNED NOT NULL COMMENT '主键，使用 RedisIdWorker 生成',
  `run_id` bigint(20) UNSIGNED NOT NULL COMMENT 'Agent Eval Run ID',
  `case_id` bigint(20) UNSIGNED DEFAULT NULL COMMENT 'Agent Eval Case ID，自定义临时用例可为空',
  `case_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用例名称快照',
  `user_input` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户输入快照',
  `expected_intent` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '期望意图',
  `actual_intent` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '实际意图',
  `expected_tools` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '期望工具JSON数组',
  `actual_tools` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '实际工具JSON数组',
  `expected_need_confirm` tinyint(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '期望是否需要人工确认',
  `actual_need_confirm` tinyint(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '实际是否需要人工确认',
  `expected_risk_level` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'LOW' COMMENT '期望风险等级',
  `actual_risk_level` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'LOW' COMMENT '实际风险等级',
  `intent_passed` tinyint(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '意图是否通过',
  `tool_passed` tinyint(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '工具是否通过',
  `confirm_passed` tinyint(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '人工确认判断是否通过',
  `risk_passed` tinyint(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '风险等级是否通过',
  `passed` tinyint(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '整体是否通过',
  `score` decimal(6,2) NOT NULL DEFAULT 0.00 COMMENT '单条用例得分，0-100',
  `diagnosis` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '失败诊断',
  `detail_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '完整评测明细JSON',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_run_id`(`run_id`) USING BTREE,
  INDEX `idx_case_id`(`case_id`) USING BTREE,
  INDEX `idx_run_passed`(`run_id`, `passed`) USING BTREE,
  INDEX `idx_intent_passed`(`expected_intent`, `passed`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商家运营Agent行为评测明细表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tb_agent_memory
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tb_agent_memory` (
  `id` bigint(20) UNSIGNED NOT NULL COMMENT '主键，使用 RedisIdWorker 生成',
  `shop_id` bigint(20) UNSIGNED NOT NULL COMMENT '店铺ID',
  `merchant_id` bigint(20) UNSIGNED NOT NULL COMMENT '商家用户ID',
  `memory_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PREFERENCE' COMMENT '记忆类型：PREFERENCE / CONSTRAINT / SUMMARY',
  `memory_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '记忆键，例如 activity_style / budget_preference',
  `memory_value` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '记忆内容，需限制长度并脱敏',
  `confidence` decimal(5,2) DEFAULT 100.00 COMMENT '置信度，人工录入默认100',
  `source_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'manual' COMMENT '来源：manual / chat / system',
  `source_session_id` bigint(20) UNSIGNED DEFAULT NULL COMMENT '来源会话ID，人工录入可为空',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_shop_status_type`(`shop_id`, `status`, `memory_type`) USING BTREE,
  INDEX `idx_merchant_time`(`merchant_id`, `create_time`) USING BTREE,
  INDEX `idx_source_session`(`source_session_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商家运营Agent长期记忆表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tb_agent_memory_candidate
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tb_agent_memory_candidate` (
  `id` bigint(20) UNSIGNED NOT NULL COMMENT '主键，使用 RedisIdWorker 生成',
  `shop_id` bigint(20) UNSIGNED NOT NULL COMMENT '店铺ID',
  `merchant_id` bigint(20) UNSIGNED NOT NULL COMMENT '商家用户ID',
  `session_id` bigint(20) UNSIGNED DEFAULT NULL COMMENT '来源会话ID',
  `source_message_id` bigint(20) UNSIGNED DEFAULT NULL COMMENT '来源消息ID',
  `candidate_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PREFERENCE' COMMENT '候选记忆类型：PREFERENCE / CONSTRAINT',
  `memory_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '候选记忆键',
  `memory_value` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '候选记忆内容',
  `reason` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '生成候选的原因',
  `confidence` decimal(5,2) DEFAULT 80.00 COMMENT '候选置信度',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING / CONFIRMED / REJECTED / CREATED / DELETED',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_shop_status_time`(`shop_id`, `status`, `create_time`) USING BTREE,
  INDEX `idx_session`(`session_id`) USING BTREE,
  INDEX `idx_merchant_time`(`merchant_id`, `create_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商家运营Agent候选记忆表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tb_agent_workflow_run
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tb_agent_workflow_run` (
  `id` bigint(20) UNSIGNED NOT NULL COMMENT '主键，建议使用 RedisIdWorker 生成',
  `session_id` bigint(20) UNSIGNED DEFAULT NULL COMMENT '关联会话ID，运营报告或系统任务可为空',
  `shop_id` bigint(20) UNSIGNED NOT NULL COMMENT '店铺ID',
  `merchant_id` bigint(20) UNSIGNED DEFAULT NULL COMMENT '商家用户ID',
  `scene` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '场景：agent_chat / tool_calling_chat / operation_report',
  `trigger_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'merchant_message' COMMENT '触发类型：merchant_message / report_generate / system',
  `user_message` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '触发本次执行的用户问题，需脱敏和截断',
  `intent` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '识别出的业务意图',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1运行中，2成功，3失败',
  `start_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `end_time` timestamp NULL DEFAULT NULL COMMENT '结束时间',
  `cost_millis` bigint(20) UNSIGNED DEFAULT NULL COMMENT '总耗时，毫秒',
  `error_msg` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '失败原因',
  `summary` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '执行摘要',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_shop_time`(`shop_id`, `create_time`) USING BTREE,
  INDEX `idx_session_time`(`session_id`, `create_time`) USING BTREE,
  INDEX `idx_scene_status_time`(`scene`, `status`, `create_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商家运营Agent Workflow运行记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tb_agent_workflow_step
-- ----------------------------
CREATE TABLE IF NOT EXISTS `tb_agent_workflow_step` (
  `id` bigint(20) UNSIGNED NOT NULL COMMENT '主键，建议使用 RedisIdWorker 生成',
  `run_id` bigint(20) UNSIGNED NOT NULL COMMENT 'Workflow Run ID',
  `session_id` bigint(20) UNSIGNED DEFAULT NULL COMMENT '关联会话ID',
  `shop_id` bigint(20) UNSIGNED NOT NULL COMMENT '店铺ID',
  `step_order` int(11) UNSIGNED NOT NULL COMMENT '步骤序号，从1开始',
  `step_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '步骤编码',
  `step_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '步骤名称',
  `node_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '节点类型：RAG_RETRIEVE / INTENT_RESOLVE / TOOL_EXECUTE / MODEL_CALL / FINAL_ANSWER等',
  `tool_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关联工具名',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1成功，2失败，3跳过',
  `input_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '步骤输入JSON，需脱敏和截断',
  `output_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '步骤输出JSON，需脱敏和截断',
  `detail` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '步骤说明',
  `error_msg` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '失败原因',
  `start_time` timestamp NULL DEFAULT NULL COMMENT '步骤开始时间',
  `end_time` timestamp NULL DEFAULT NULL COMMENT '步骤结束时间',
  `cost_millis` bigint(20) UNSIGNED DEFAULT NULL COMMENT '步骤耗时，毫秒',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_run_order`(`run_id`, `step_order`) USING BTREE,
  INDEX `idx_shop_time`(`shop_id`, `create_time`) USING BTREE,
  INDEX `idx_step_status`(`step_code`, `status`) USING BTREE,
  INDEX `idx_tool_time`(`tool_name`, `create_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商家运营Agent Workflow步骤记录表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
