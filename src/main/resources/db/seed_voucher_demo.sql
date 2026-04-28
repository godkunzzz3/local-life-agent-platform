INSERT INTO tb_voucher (id, shop_id, title, sub_title, rules, pay_value, actual_value, type, status, create_time, update_time) VALUES
(20001, 10143, '酒吧99抵120代金券', '微醺小酌通用，周一至周日可用', '到店核销\n不可兑换现金\n酒水套餐可用', 9900, 12000, 0, 1, NOW(), NOW()),
(20002, 10143, '酒吧59抵100秒杀券', '限时抢购，适合双人小酌', '每人限购1张\n到店核销\n不可与其它活动叠加', 5900, 10000, 1, 1, NOW(), NOW()),
(20003, 10027, '造型128抵168代金券', '洗剪吹/基础护理可用', '到店核销\n节假日可用\n不可兑换现金', 12800, 16800, 0, 1, NOW(), NOW()),
(20004, 10027, '美发88抵150秒杀券', '新客限时福利', '每人限购1张\n需提前预约\n到店核销', 8800, 15000, 1, 1, NOW(), NOW()),
(20005, 10113, '亲子游乐69抵100代金券', '亲子门票/手作体验可用', '到店核销\n周末通用\n不可兑换现金', 6900, 10000, 0, 1, NOW(), NOW()),
(20006, 10113, '亲子39抵80秒杀券', '限时亲子体验券', '每人限购1张\n到店核销\n不与团购叠加', 3900, 8000, 1, 1, NOW(), NOW()),
(20007, 10047, '健身体验49抵99代金券', '单次体验课/团课可用', '到店核销\n需提前预约\n不可兑换现金', 4900, 9900, 0, 1, NOW(), NOW()),
(20008, 10067, '足疗118抵168代金券', '足疗/肩颈套餐可用', '到店核销\n节假日可用\n不可兑换现金', 11800, 16800, 0, 1, NOW(), NOW()),
(20009, 1, '茶餐厅39抵50代金券', '工作餐/下午茶通用', '到店核销\n堂食可用\n不找零', 3900, 5000, 0, 1, NOW(), NOW()),
(20010, 10, 'KTV欢唱99抵180秒杀券', '晚场包厢限时福利', '每人限购1张\n需提前预约\n到店核销', 9900, 18000, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE shop_id = VALUES(shop_id), title = VALUES(title), sub_title = VALUES(sub_title), rules = VALUES(rules), pay_value = VALUES(pay_value), actual_value = VALUES(actual_value), type = VALUES(type), status = VALUES(status), update_time = NOW();

INSERT INTO tb_seckill_voucher (voucher_id, stock, create_time, begin_time, end_time, update_time) VALUES
(20002, 80, NOW(), '2026-04-28 00:00:00', '2026-05-30 23:59:59', NOW()),
(20004, 60, NOW(), '2026-04-28 00:00:00', '2026-05-30 23:59:59', NOW()),
(20006, 100, NOW(), '2026-04-28 00:00:00', '2026-05-30 23:59:59', NOW()),
(20010, 50, NOW(), '2026-04-28 00:00:00', '2026-05-30 23:59:59', NOW())
ON DUPLICATE KEY UPDATE stock = VALUES(stock), begin_time = VALUES(begin_time), end_time = VALUES(end_time), update_time = NOW();

ALTER TABLE tb_voucher AUTO_INCREMENT = 20100;
