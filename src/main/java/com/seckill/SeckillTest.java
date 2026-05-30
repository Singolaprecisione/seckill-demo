package com.seckill;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public class SeckillTest {

    public static void main(String[] args) throws Exception {
        int totalRequests = 1000;
        int threadCount = 200;
        String url = "http://localhost:8080/seckill/do";

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicInteger stockOutCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);
        AtomicInteger busyCount = new AtomicInteger(0);
        AtomicInteger otherFailCount = new AtomicInteger(0);
        LongAdder totalResponseTime = new LongAdder();

        long testStart = System.currentTimeMillis();
        System.out.println("========================================");
        System.out.println("  秒杀压测启动");
        System.out.println("  并发线程: " + threadCount);
        System.out.println("  总请求数: " + totalRequests);
        System.out.println("  商品ID: 1, 用户ID范围: 60000~" + (60000 + totalRequests - 1));
        System.out.println("  初始库存: Redis=100, MySQL=100");
        System.out.println("========================================");

        for (int i = 0; i < totalRequests; i++) {
            final int userId = 60000 + i;
            pool.execute(() -> {
                try {
                    startLatch.await(); // 所有线程等待发令枪
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                long reqStart = System.currentTimeMillis();
                try {
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                            new java.net.URL(url).openConnection();
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                    String body = "goodsId=1&userId=" + userId;
                    conn.getOutputStream().write(body.getBytes("UTF-8"));

                    int code = conn.getResponseCode();
                    java.util.Scanner s = new java.util.Scanner(conn.getInputStream(), "UTF-8").useDelimiter("\\A");
                    String resp = s.hasNext() ? s.next() : "";

                    long elapsed = System.currentTimeMillis() - reqStart;
                    totalResponseTime.add(elapsed);

                    if (resp.contains("秒杀成功")) {
                        successCount.incrementAndGet();
                    } else if (resp.contains("库存不足")) {
                        failCount.incrementAndGet();
                        stockOutCount.incrementAndGet();
                    } else if (resp.contains("已参与过")) {
                        failCount.incrementAndGet();
                        duplicateCount.incrementAndGet();
                    } else if (resp.contains("系统繁忙")) {
                        failCount.incrementAndGet();
                        busyCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                        otherFailCount.incrementAndGet();
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    otherFailCount.incrementAndGet();
                }
                doneLatch.countDown();
            });
        }

        System.out.println("所有线程就绪，3秒后同时发起请求...");
        Thread.sleep(3000);
        startLatch.countDown(); // 发令枪
        doneLatch.await(60, TimeUnit.SECONDS);

        long testEnd = System.currentTimeMillis();
        long totalElapsed = testEnd - testStart;
        double qps = totalRequests * 1000.0 / totalElapsed;
        long totalRespTime = totalResponseTime.sum();
        double avgRespTime = totalRequests > 0 ? totalRespTime * 1.0 / totalRequests : 0;

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println();
        System.out.println("========================================");
        System.out.println("  压测报告");
        System.out.println("========================================");
        System.out.printf("  总请求数:       %d%n", totalRequests);
        System.out.printf("  成功:           %d (%.1f%%)%n", successCount.get(), successCount.get() * 100.0 / totalRequests);
        System.out.printf("  失败:           %d (%.1f%%)%n", failCount.get(), failCount.get() * 100.0 / totalRequests);
        System.out.println("  ---");
        System.out.printf("  库存不足:       %d%n", stockOutCount.get());
        System.out.printf("  重复下单:       %d%n", duplicateCount.get());
        System.out.printf("  系统繁忙:       %d%n", busyCount.get());
        System.out.printf("  其他错误:       %d%n", otherFailCount.get());
        System.out.println("  ---");
        System.out.printf("  总耗时:         %d ms (%.2f s)%n", totalElapsed, totalElapsed / 1000.0);
        System.out.printf("  QPS:            %.1f req/s%n", qps);
        System.out.printf("  平均响应时间:   %.1f ms%n", avgRespTime);
        System.out.println("========================================");

        // ===== 超卖验证 =====
        System.out.println();
        System.out.print("等待MQ消费者处理完毕(5秒)... ");
        Thread.sleep(5000);
        System.out.println("完成");
        System.out.print("正在验证超卖... ");

        // 查询 Redis 剩余库存
        Process redisProc = Runtime.getRuntime().exec(
                new String[]{"redis-cli", "GET", "seckill:stock:1"});
        java.util.Scanner rs = new java.util.Scanner(redisProc.getInputStream()).useDelimiter("\\A");
        String redisStockStr = rs.hasNext() ? rs.next().trim() : "-1";
        int redisRemaining = Integer.parseInt(redisStockStr);

        // 查询 MySQL 剩余库存
        Process mysqlProc = Runtime.getRuntime().exec(
                new String[]{"mysql", "-h", "172.29.224.1", "-u", "root", "-p123456",
                        "seckill", "-N", "-e", "SELECT stock_count FROM seckill_goods WHERE goods_id=1"});
        java.util.Scanner ms = new java.util.Scanner(mysqlProc.getInputStream()).useDelimiter("\\A");
        String mysqlStockStr = ms.hasNext() ? ms.next().trim() : "-1";
        int mysqlRemaining = Integer.parseInt(mysqlStockStr);

        // 查询订单数
        Process orderProc = Runtime.getRuntime().exec(
                new String[]{"mysql", "-h", "172.29.224.1", "-u", "root", "-p123456",
                        "seckill", "-N", "-e",
                        "SELECT COUNT(1) FROM order_info WHERE user_id >= 60000 AND goods_id = 1"});
        java.util.Scanner os = new java.util.Scanner(orderProc.getInputStream()).useDelimiter("\\A");
        String orderCountStr = os.hasNext() ? os.next().trim() : "-1";
        int orderCount = Integer.parseInt(orderCountStr);

        int initialStock = 100;
        int expectedOrders = initialStock - mysqlRemaining;
        boolean oversold = orderCount > initialStock || successCount.get() > initialStock;
        boolean consistent = orderCount == expectedOrders;

        System.out.println("完成");
        System.out.println();
        System.out.println("========================================");
        System.out.println("  超卖验证报告");
        System.out.println("========================================");
        System.out.printf("  初始库存:       %d%n", initialStock);
        System.out.printf("  MySQL剩余库存:  %d%n", mysqlRemaining);
        System.out.printf("  Redis剩余库存:  %d%n", redisRemaining);
        System.out.printf("  理论售出:       %d (初始-剩余)%n", expectedOrders);
        System.out.printf("  实际订单数:     %d%n", orderCount);
        System.out.printf("  成功响应数:     %d%n", successCount.get());
        System.out.println("  ---");
        System.out.printf("  是否超卖:       %s%n", oversold ? "是 - 存在超卖!" : "否 - 无超卖");
        System.out.printf("  订单库存一致:   %s%n", consistent ? "是 - 一致" : "否 - 不一致");
        System.out.println("========================================");

        System.exit(0);
    }
}
