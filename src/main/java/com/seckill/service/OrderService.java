package com.seckill.service;

import com.seckill.entity.OrderInfo;
import com.seckill.mapper.OrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    @Autowired
    private OrderMapper orderMapper;

    public Long createOrder(OrderInfo orderInfo) {
        orderMapper.insertOrder(orderInfo);
        return orderInfo.getId();
    }

    public int countByUserAndGoods(Long userId, Long goodsId) {
        return orderMapper.countByUserAndGoods(userId, goodsId);
    }
}
