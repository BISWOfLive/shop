package com.chengli.shop.config;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 观测基础设施：业务指标采样使用独立单线程调度器，不占用到期发布/扫描线程；
 * 业务指标标签只允许固定结果类型，超出上限的值被丢弃，避免业务 ID 造成标签膨胀。
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ObservabilityConfiguration {

    @Bean
    public ThreadPoolTaskScheduler shopMetricsTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        // 单线程按 60 秒周期采样；采样查询失败不影响业务请求与到期任务。
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("shop-metrics-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }

    @Bean
    public MeterFilter shopTagCardinalityGuard() {
        // 只允许 result 这类固定枚举标签，超过 8 个不同值即拒绝该指标。
        return MeterFilter.maximumAllowableTags("shop.", "result", 8, MeterFilter.deny());
    }
}
