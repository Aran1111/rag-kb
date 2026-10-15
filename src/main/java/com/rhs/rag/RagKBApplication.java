package com.rhs.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync            // 开启索引任务异步执行
@EnableRetry            // 开启重试 允许Embadding 调用失败重试
@EnableCaching
public class RagKBApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagKBApplication.class,args);
    }
}
