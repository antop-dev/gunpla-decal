package ai.antop.gunpla.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableCaching
class CacheConfig {
    /** Caffeine 기반 CacheManager 등록. 캐시당 최대 1,000개 항목을 인메모리로 유지 */
    @Bean
    fun cacheManager(): CacheManager {
        val caffeine = Caffeine.newBuilder().maximumSize(1000)
        return CaffeineCacheManager().apply { setCaffeine(caffeine) }
    }
}
