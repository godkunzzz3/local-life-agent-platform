/*
 商家身份与店铺授权表。

 说明：
 1. 登录仍复用 tb_user，用户是否拥有商家身份由 tb_merchant 决定。
 2. 一个用户可以绑定多个店铺；第一版先用 OWNER / STAFF 区分角色。
 3. 商家端所有涉及 shopId 的写操作，都必须校验当前 user_id 是否绑定该 shop_id。
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `tb_merchant` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '登录用户ID，关联 tb_user.id',
  `shop_id` bigint(20) UNSIGNED NOT NULL COMMENT '店铺ID，关联 tb_shop.id',
  `role` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'OWNER' COMMENT '商家角色：OWNER / STAFF',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1正常，2禁用',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_shop` (`user_id`, `shop_id`) USING BTREE,
  INDEX `idx_shop_status` (`shop_id`, `status`) USING BTREE,
  INDEX `idx_user_status` (`user_id`, `status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '商家店铺授权表' ROW_FORMAT = Dynamic;

INSERT INTO `tb_merchant` (`user_id`, `shop_id`, `role`, `status`)
VALUES (1010, 10143, 'OWNER', 1)
ON DUPLICATE KEY UPDATE `role` = VALUES(`role`), `status` = VALUES(`status`);

SET FOREIGN_KEY_CHECKS = 1;
