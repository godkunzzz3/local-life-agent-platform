package com.hmdp.dto;

import lombok.Data;

/**
 * 店铺优惠券统计 DTO。
 *
 * <p>用于描述店铺当前优惠券结构。Agent 根据这些字段判断店铺是否缺少普通券、
 * 是否需要补充秒杀券、秒杀库存是否过高或过低。</p>
 */
@Data
public class VoucherStatsDTO {

    /**
     * 店铺优惠券总数。
     */
    private Integer totalVouchers;

    /**
     * 当前上架中的优惠券数量。
     */
    private Integer onlineVouchers;

    /**
     * 普通代金券数量。
     */
    private Integer normalVouchers;

    /**
     * 秒杀券数量。
     */
    private Integer seckillVouchers;

    /**
     * 秒杀券剩余库存总和。
     */
    private Integer seckillStock;

    /**
     * 是否已经配置秒杀券。
     */
    private Boolean hasSeckill;
}

