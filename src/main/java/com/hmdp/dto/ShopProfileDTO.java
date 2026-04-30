package com.hmdp.dto;

import lombok.Data;

/**
 * 店铺画像 DTO。
 *
 * <p>用于承载 Agent 分析店铺时需要的基础信息。店铺画像是运营报告的上下文，
 * 后续订单分析、优惠券建议、内容建议都要结合这些基础字段一起判断。</p>
 */
@Data
public class ShopProfileDTO {

    /**
     * 店铺名称。
     */
    private String name;

    /**
     * 店铺品类 id。
     */
    private Long typeId;

    /**
     * 所属商圈或区域。
     */
    private String area;

    /**
     * 店铺详细地址。
     */
    private String address;

    /**
     * 人均价格，单位为元。
     */
    private Long avgPrice;

    /**
     * 店铺评分。黑马点评原始数据通常是 0~50，例如 45 表示 4.5 分。
     */
    private Integer score;

    /**
     * 已售数量。
     */
    private Integer sold;

    /**
     * 评论数量。
     */
    private Integer comments;

    /**
     * 营业时间。
     */
    private String openHours;
}
