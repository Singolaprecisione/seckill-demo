package com.seckill.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 订单实体类
 * 
 * <p>对应数据库表: order_info
 * <p>存储用户秒杀成功后的订单信息
 * 
 * @author seckill
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderInfo {

    /** 订单ID */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 商品ID */
    private Long goodsId;

    /** 商品名称 */
    private String goodsName;

    /** 购买数量 */
    private Integer goodsCount;

    /** 商品价格（秒杀价格） */
    private BigDecimal goodsPrice;

    /** 订单状态: 0-未支付, 1-已支付, 2-已取消 */
    private Integer status;

    /** 创建时间 */
    private Date createTime;
}