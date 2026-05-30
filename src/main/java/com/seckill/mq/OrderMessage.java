package com.seckill.mq;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

public class OrderMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String messageId;
    private Long goodsId;
    private Long userId;
    private String goodsName;
    private BigDecimal goodsPrice;

    public OrderMessage() {}

    public OrderMessage(Long goodsId, Long userId, String goodsName, BigDecimal goodsPrice) {
        this.messageId = UUID.randomUUID().toString();
        this.goodsId = goodsId;
        this.userId = userId;
        this.goodsName = goodsName;
        this.goodsPrice = goodsPrice;
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public Long getGoodsId() { return goodsId; }
    public void setGoodsId(Long goodsId) { this.goodsId = goodsId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getGoodsName() { return goodsName; }
    public void setGoodsName(String goodsName) { this.goodsName = goodsName; }
    public BigDecimal getGoodsPrice() { return goodsPrice; }
    public void setGoodsPrice(BigDecimal goodsPrice) { this.goodsPrice = goodsPrice; }
}
