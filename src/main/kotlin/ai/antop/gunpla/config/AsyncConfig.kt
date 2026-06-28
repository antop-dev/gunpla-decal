package ai.antop.gunpla.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
@EnableAsync
class AsyncConfig {
    /** 메뉴얼 등록 작업 전용 스레드 풀. 한 번에 하나씩 순서대로 처리, 나머지는 큐에 FIFO 대기 */
    @Bean("manualTaskExecutor")
    fun manualTaskExecutor(): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 1
            maxPoolSize = 1
            queueCapacity = Int.MAX_VALUE
            setThreadNamePrefix("manual-task-")
            initialize()
        }
}
