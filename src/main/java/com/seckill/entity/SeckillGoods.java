package com.seckill.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 秒杀商品实体类
 * 
 * <p>对应数据库表: seckill_goods
 * <p>存储秒杀活动的商品信息，包含秒杀价格、库存、时间等
 * 
 * @author seckill
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillGoods {

    /** 秒杀商品ID */
    private Long id;

    /** 关联商品ID */
    private Long goodsId;

    /** 秒杀价格 */
    private BigDecimal seckillPrice;

    /** 秒杀库存数量 */
    private Integer stockCount;

    /** 秒杀开始时间 */
    private Date startTime;

    /** 秒杀结束时间 */
    private Date endTime;

    /** 乐观锁版本号 */
    private Integer version;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;
}