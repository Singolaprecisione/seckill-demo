package com.seckill.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import com.seckill.config.SeckillMetrics;
import com.seckill.entity.Goods;
import com.seckill.entity.SeckillGoods;
import com.seckill.mq.OrderMessage;
import com.seckill.service.GoodsService;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/seckill")
public class SeckillController {

    private static final Logger log = LoggerFactory.getLogger(SeckillController.class);

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RateLimiter seckillRateLimiter;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private SeckillMetrics metrics;

    @GetMapping("/goods/list")
    public List<Goods> getGoodsList() {
        return goodsService.getAllGoods();
    }

    @PostMapping("/do")
    public String doSeckill(@RequestParam Long goodsId, @RequestParam Long userId) {
        long startTime = System.currentTimeMillis();

        // --- 令牌桶限流 ---
        if (!seckillRateLimiter.tryAcquire()) {
            metrics.recordFailBusy();
            return "当前参与人数过多，请稍后再试";
        }

        // Redis获取商品详情缓存
        RBucket<String> goodsBucket = redissonClient.getBucket("seckill:goods:" + goodsId);
        String goodsJson = goodsBucket.get();
        if (goodsJson == null) {
            metrics.recordFailOther();
            return "商品不存在";
        }

        SeckillGoods seckillGoods;
        try {
            seckillGoods = objectMapper.readValue(goodsJson, SeckillGoods.class);
        } catch (Exception e) {
            metrics.recordException();
            log.error("[SEC-KILL] 商品数据反序列化失败: goodsId={}", goodsId, e);
            return "商品数据异常";
        }

        // 时间窗口校验
        Date now = new Date();
        if (now.before(seckillGoods.getStartTime())) {
            metrics.recordFailOther();
            return "秒杀活动未开始";
        }
        if (now.after(seckillGoods.getEndTime())) {
            metrics.recordFailOther();
            return "秒杀活动已结束";
        }

        // 幂等校验
        String dedupKey = "seckill:dedup:" + userId + ":" + goodsId;
        RBucket<String> dedupBucket = redissonClient.getBucket(dedupKey);
        if (!dedupBucket.setIfAbsent("1", Duration.ofHours(1))) {
            metrics.recordFailDuplicate();
            return "您已参与过该商品秒杀";
        }

        // 分布式锁 + Redis DECR
        RLock lock = redissonClient.getLock("lock:seckill:" + goodsId);
        try {
            boolean locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                dedupBucket.delete();
                metrics.recordFailBusy();
                return "系统繁忙，请稍后再试";
            }

            RAtomicLong stockAtomic = redissonClient.getAtomicLong("seckill:stock:" + goodsId);
            long remaining = stockAtomic.decrementAndGet();
            if (remaining < 0) {
                stockAtomic.incrementAndGet();
                dedupBucket.delete();
                metrics.recordFailStockOut();
                log.info("[SEC-KILL] 库存不足: userId={}, goodsId={}", userId, goodsId);
                return "秒杀失败，库存不足";
            }

            // 发送MQ
            OrderMessage msg = new OrderMessage(goodsId, userId,
                    "秒杀商品-" + goodsId, seckillGoods.getSeckillPrice());
            rabbitTemplate.convertAndSend("seckill.exchange", "seckill.order.create", msg);

            long elapsed = System.currentTimeMillis() - startTime;
            metrics.recordSuccess(elapsed);
            log.info("[SEC-KILL] 秒杀成功: userId={}, goodsId={}, price={}, rt={}ms",
                    userId, goodsId, seckillGoods.getSeckillPrice(), elapsed);
            return "秒杀成功";

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            dedupBucket.delete();
            metrics.recordFailBusy();
            return "系统繁忙，请稍后再试";
        } finally {
            if (lock != null && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
