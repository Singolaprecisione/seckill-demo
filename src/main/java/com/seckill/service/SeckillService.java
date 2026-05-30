package com.seckill.service;

import com.seckill.entity.OrderInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeckillService {

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private OrderService orderService;

    @Transactional(rollbackFor = Exception.class)
    public Long doSeckillCore(Long goodsId, OrderInfo orderInfo) {
        int result = goodsService.reduceStock(goodsId);
        if (result == 0) {
            throw new RuntimeException("库存不足");
        }
        return orderService.createOrder(orderInfo);
    }
}
