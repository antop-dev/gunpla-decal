package ai.antop.gunpla.app.event

import ai.antop.gunpla.config.CacheName
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.cache.annotation.CacheEvict
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

val log = KotlinLogging.logger {}

/** ManualChangedEvent를 수신하여 메뉴얼 관련 캐시를 제거하는 리스너 */
@Component
class ManualCacheEvictListener {
    @CacheEvict(
        cacheNames = [CacheName.MANUAL, CacheName.MANUAL_RESOURCE, CacheName.THUMBNAIL_RESOURCE],
        key = "#event.manualId",
    )
    @EventListener
    fun onManualChanged(event: ManualChangedEvent) {
        log.debug { "Manual changed, evicting caches for manualId: ${event.manualId}" }
    }
}
