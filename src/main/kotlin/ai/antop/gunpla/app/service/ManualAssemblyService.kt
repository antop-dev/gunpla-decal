package ai.antop.gunpla.app.service

import ai.antop.gunpla.app.domain.ManualId
import ai.antop.gunpla.app.dto.ManualAssemblyDto
import ai.antop.gunpla.app.dto.ManualSummaryDto
import ai.antop.gunpla.config.CacheName
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/** 메뉴얼·데칼·썸네일을 조합하여 응답 DTO를 구성하는 조회 전용 서비스 */
@Service
@Transactional(readOnly = false)
class ManualAssemblyService(
    private val manualService: ManualService,
    private val decalService: DecalService,
    private val thumbnailService: ThumbnailService,
) {
    /** 메뉴얼 요약 목록 반환. onlyPublished=true이면 공개 메뉴얼만 반환 */
    fun getManuals(
        q: String? = null,
        onlyPublished: Boolean = true,
    ): List<ManualSummaryDto> = manualService.getAllManuals(q, onlyPublished)

    /**
     * 메뉴얼 단건 조회 (데칼·썸네일 목록 포함).
     * useCache=true(사용자)이면 Caffeine 캐시를 적용하고, false(관리자)이면 항상 DB를 조회한다.
     * onlyPublished=true이면 비공개 메뉴얼을 404로 처리한다.
     */
    @Cacheable(cacheNames = [CacheName.MANUAL], key = "#manualId", condition = "#useCache")
    fun getManual(
        manualId: ManualId,
        onlyPublished: Boolean = false,
        useCache: Boolean = true,
    ): ManualAssemblyDto {
        val manual = manualService.getManual(manualId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        if (onlyPublished && !manual.published) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        val decals = decalService.getDecalsByManualId(manual.id)
        val thumbnails = thumbnailService.getThumbnails(manual.id)
        return ManualAssemblyDto(
            id = manual.id,
            grade = manual.grade,
            modelNumber = manual.modelNumber,
            productName = manual.productName,
            published = manual.published,
            decals = decals,
            thumbnails = thumbnails.map { "/resource/${manual.id}/thumbnails/${it.pageNumber}" },
            link = manual.link,
        )
    }
}
