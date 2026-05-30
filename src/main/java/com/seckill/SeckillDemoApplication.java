package com.seckill;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 秒杀系统启动类
 * 
 * <p>负责启动 Spring Boot 应用，配置 MyBatis Mapper 扫描路径
 * 
 * @author seckill
 * @version 1.0.0
 */
@SpringBootApplication
@MapperScan("com.seckill.mapper") // 扫描 MyBatis Mapper 接口
public class SeckillDemoApplication {

    /**
     * 应用入口方法
     * 
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SeckillDemoApplication.class, args);
    }
}