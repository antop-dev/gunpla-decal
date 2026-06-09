package ai.antop.gunpla.app.repository

import ai.antop.gunpla.app.domain.Thumbnail
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

/** 썸네일 JPA 저장소 */
interface ThumbnailRepository : JpaRepository<Thumbnail, Long> {
    /** 메뉴얼에 속한 썸네일 목록 반환 (pageNumber 오름차순) */
    fun findByManualIdOrderByPageNumber(manualId: Long): List<Thumbnail>

    /** 메뉴얼의 특정 페이지 썸네일 단건 조회. 존재하지 않으면 null 반환 */
    fun findByManualIdAndPageNumber(
        manualId: Long,
        pageNumber: Int,
    ): Thumbnail?

    /** 메뉴얼에 속한 썸네일 전체 삭제 (JPQL 벌크 삭제) */
    @Modifying
    @Query("DELETE FROM Thumbnail t WHERE t.manualId = :manualId")
    fun deleteThumbnailsByManualIdQuery(manualId: Long)
}
