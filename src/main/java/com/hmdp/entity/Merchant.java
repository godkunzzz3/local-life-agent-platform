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
 * 商家店铺授权关系。
 *
 * <p>登录身份仍然来自 tb_user；当用户在 tb_merchant 中绑定了某个 shopId，
 * 才代表该用户拥有这个店铺的商家端权限。这样不需要维护两套登录系统，也能让普通用户
 * 和商家用户共用同一套 token。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_merchant")
public class Merchant implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键。
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 登录用户ID。
     */
    private Long userId;

    /**
     * 店铺ID。
     */
    private Long shopId;

    /**
     * 商家角色：OWNER / STAFF。
     */
    private String role;

    /**
     * 状态：1正常，2禁用。
     */
    private Integer status;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;
}
