package com.seckill.config;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@Component
@EnableScheduling
public class SeckillMetrics {

    private static final Logger log = LoggerFactory.getLogger("SECKILL-MONITOR");

    @Autowired
    private RedissonClient redissonClient;

    // 请求计数器
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder successRequests = new LongAdder();
    private final LongAdder failStockOut = new LongAdder();
    private final LongAdder failDuplicate = new LongAdder();
    private final LongAdder failBusy = new LongAdder();
    private final LongAdder failOther = new LongAdder();
    private final LongAdder totalResponseTime = new LongAdder();

    // 上一周期快照（用于QPS计算）
    private final AtomicLong lastTotalRequests = new AtomicLong(0);
    private final AtomicLong lastTotalResponseTime = new AtomicLong(0);

    // 异常计数器
    private final LongAdder exceptionCount = new LongAdder();

    // 初始库存（从Redis读取）
    private volatile long initialStock = -1;

    @PostConstruct
    public void init() {
        log.info("========== 秒杀监控系统启动 ==========");
    }

    @Scheduled(initialDelay = 5000, fixedRate = 60000)
    public void initStock() {
        if (initialStock <= 0) {
            RAtomicLong stock = redissonClient.getAtomicLong("seckill:stock:1");
            long s = stock.get();
            if (s > 0) {
                initialStock = s;
                log.info("[INIT] 初始库存记录: {}", initialStock);
            }
        }
    }

    public void recordSuccess(long responseTimeMs) {
        totalRequests.increment();
        successRequests.increment();
        totalResponseTime.add(responseTimeMs);
    }

    public void recordFailStockOut() {
        totalRequests.increment();
        failStockOut.increment();
    }

    public void recordFailDuplicate() {
        totalRequests.increment();
        failDuplicate.increment();
    }

    public void recordFailBusy() {
        totalRequests.increment();
        failBusy.increment();
    }

    public void recordFailOther() {
        totalRequests.increment();
        failOther.increment();
    }

    public void recordException() {
        exceptionCount.increment();
    }

    @Scheduled(fixedRate = 10000)
    public void reportMetrics() {
        long total = totalRequests.sum();
        long success = successRequests.sum();
        long lastTotal = lastTotalRequests.getAndSet(total);
        long lastRtSum = lastTotalResponseTime.getAndSet(totalResponseTime.sum());
        long stockOut = failStockOut.sum();
        long dup = failDuplicate.sum();
        long busy = failBusy.sum();
        long other = failOther.sum();
        long fail = stockOut + dup + busy + other;
        long exceptions = exceptionCount.sum();

        if (total == 0) {
            return;
        }

        // QPS (最近10秒)
        long intervalRequests = total - lastTotal;
        double qps = intervalRequests / 10.0;
        long intervalRtSum = totalResponseTime.sum() - lastRtSum;
        double avgRt = intervalRequests > 0 ? intervalRtSum * 1.0 / intervalRequests : 0;
        double successRate = total > 0 ? success * 100.0 / total : 0;
        double failRate = total > 0 ? fail * 100.0 / total : 0;

        // 库存查询
        long stock = -1;
        try {
            RAtomicLong stockAtomic = redissonClient.getAtomicLong("seckill:stock:1");
            stock = stockAtomic.get();
        } catch (Exception e) {
            log.warn("[STOCK] 库存查询失败: {}", e.getMessage());
        }

        log.info("========== 秒杀监控 (10秒快照) ==========");
        log.info("[QPS]      请求: {} | QPS: {} req/s", intervalRequests, String.format("%.1f", qps));
        log.info("[RT]       平均响应: {} ms", String.format("%.1f", avgRt));
        log.info("[RATE]     成功率: {}% | 失败率: {}%", String.format("%.1f", successRate), String.format("%.1f", failRate));
        log.info("[STOCK]    剩余库存: {}", stock);
        log.info("[BREAKDOWN] 成功:{} | 库存不足:{} | 重复:{} | 繁忙:{} | 其他:{} | 异常:{}",
                success, stockOut, dup, busy, other, exceptions);
        log.info("==========================================");

        // === 告警规则 ===

        // 库存低于10%
        if (stock >= 0 && initialStock > 0) {
            double stockPercent = stock * 100.0 / initialStock;
            if (stockPercent < 10) {
                log.warn("!!!! [库存告警] 库存低于10% ! 剩余:{} / 初始:{} ({}%)", stock, initialStock, String.format("%.1f", stockPercent));
            }
            if (stock == 0) {
                log.warn("!!!! [库存告警] 库存已耗尽！秒杀即将结束");
            }
        }

        // 异常率超过1% (当前10秒窗口)
        if (intervalRequests > 10) {
            double exceptionRate = exceptions * 100.0 / total;
            if (exceptionRate > 1) {
                log.warn("!!!! [异常告警] 异常率: {}% > 1% 阈值", String.format("%.2f", exceptionRate));
            }
        }
    }

    @Scheduled(fixedRate = 60000)
    public void reportStockWarning() {
        long stock = -1;
        try {
            stock = redissonClient.getAtomicLong("seckill:stock:1").get();
        } catch (Exception e) {
            return;
        }
        if (stock >= 0 && initialStock > 0) {
            double pct = stock * 100.0 / initialStock;
            log.info("[库存快照] 剩余:{} / 初始:{} ({}%)", stock, initialStock, String.format("%.1f", pct));
        }
    }
}
