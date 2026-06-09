package ai.antop.gunpla.app.service

import ai.antop.gunpla.app.domain.ManualId
import ai.antop.gunpla.config.CacheName
import org.springframework.cache.annotation.Cacheable
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service

/** 파일 리소스 제공 서비스. PDF·썸네일을 Caffeine 캐시로 서빙한다 */
@Service
class ResourceService(
    private val manualService: ManualService,
    private val thumbnailService: ThumbnailService,
) {
    /** PDF 파일 리소스 반환 (캐시 키: manualId) */
    @Cacheable(cacheNames = [CacheName.MANUAL_RESOURCE], key = "#manualId")
    fun getManual(manualId: ManualId): Resource = manualService.getPdfResource(manualId)

    /** 특정 페이지 썸네일 PNG 리소스 반환 (캐시 키: manualId:pageNumber) */
    @Cacheable(cacheNames = [CacheName.THUMBNAIL_RESOURCE], key = "#manualId + ':' + #pageNumber")
    fun getThumbnail(
        manualId: ManualId,
        pageNumber: Int,
    ): Resource = thumbnailService.getThumbnailResource(manualId, pageNumber)
}
