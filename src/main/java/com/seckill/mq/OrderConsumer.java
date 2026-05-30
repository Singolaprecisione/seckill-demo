package com.seckill.mq;

import com.seckill.entity.OrderInfo;
import com.seckill.service.SeckillService;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;

@Component
public class OrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderConsumer.class);

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private RedissonClient redissonClient;

    @RabbitListener(queues = "seckill.order.queue")
    public void handleOrderCreate(OrderMessage msg) {
        log.info("收到订单创建消息: messageId={}, userId={}, goodsId={}", msg.getMessageId(), msg.getUserId(), msg.getGoodsId());

        // Layer 2: MQ消息幂等校验
        RBucket<String> dedupBucket = redissonClient.getBucket("seckill:msg:" + msg.getMessageId());
        if (!dedupBucket.setIfAbsent("1", Duration.ofHours(1))) {
            log.warn("消息已处理，直接ACK: messageId={}", msg.getMessageId());
            return;
        }

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setUserId(msg.getUserId());
        orderInfo.setGoodsId(msg.getGoodsId());
        orderInfo.setGoodsName(msg.getGoodsName());
        orderInfo.setGoodsCount(1);
        orderInfo.setGoodsPrice(msg.getGoodsPrice());
        orderInfo.setStatus(0);
        orderInfo.setCreateTime(new Date());

        try {
            seckillService.doSeckillCore(msg.getGoodsId(), orderInfo);
            log.info("订单创建成功: userId={}, goodsId={}, orderId={}", msg.getUserId(), msg.getGoodsId(), orderInfo.getId());
        } catch (DuplicateKeyException e) {
            log.warn("并发重复订单(唯一索引)，忽略: userId={}, goodsId={}", msg.getUserId(), msg.getGoodsId());
        } catch (RuntimeException e) {
            log.error("订单创建失败，补偿Redis库存: userId={}, goodsId={}, error={}", msg.getUserId(), msg.getGoodsId(), e.getMessage());
            RAtomicLong stockAtomic = redissonClient.getAtomicLong("seckill:stock:" + msg.getGoodsId());
            stockAtomic.incrementAndGet();
            // 清除幂等标记，允许重试
            dedupBucket.delete();
        }
    }
}
