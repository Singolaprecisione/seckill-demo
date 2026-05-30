package com.seckill.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 商品实体类
 * 
 * <p>对应数据库表: goods
 * <p>存储商品的基本信息
 * 
 * @author seckill
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Goods {

    /** 商品ID */
    private Long id;

    /** 商品名称 */
    private String goodsName;

    /** 商品标题 */
    private String goodsTitle;

    /** 商品图片URL */
    private String goodsImg;

    /** 商品详情描述 */
    private String goodsDetail;

    /** 商品原价 */
    private BigDecimal goodsPrice;

    /** 商品库存数量 */
    private Integer goodsStock;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;
}