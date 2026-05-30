package com.seckill.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.entity.SeckillGoods;
import com.seckill.service.GoodsService;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class SeckillStartupListener implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(SeckillStartupListener.class);

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("开始预热秒杀商品数据到Redis...");
        List<SeckillGoods> list = goodsService.getAllSeckillGoods();
        for (SeckillGoods goods : list) {
            Long goodsId = goods.getGoodsId();

            // 库存预热
            RAtomicLong stockAtomic = redissonClient.getAtomicLong("seckill:stock:" + goodsId);
            stockAtomic.set(goods.getStockCount());

            // 商品详情缓存（24小时过期）
            RBucket<String> goodsBucket = redissonClient.getBucket("seckill:goods:" + goodsId);
            try {
                String json = objectMapper.writeValueAsString(goods);
                goodsBucket.set(json, 24, TimeUnit.HOURS);
            } catch (Exception e) {
                log.error("序列化秒杀商品失败: goodsId={}", goodsId, e);
            }

            log.info("预热完成: goodsId={}, stock={}, price={}", goodsId, goods.getStockCount(), goods.getSeckillPrice());
        }
        log.info("秒杀商品Redis预热完毕，共{}件商品", list.size());
    }
}
