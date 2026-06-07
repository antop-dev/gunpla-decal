package com.example.gunpladecal.app.service

import com.example.gunpladecal.app.domain.Grade
import com.example.gunpladecal.app.domain.ManualId
import com.example.gunpladecal.app.dto.ManualDetailDto
import com.example.gunpladecal.app.dto.ManualSummaryDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException

private val log = KotlinLogging.logger {}

/** ManualService · DecalService · ThumbnailService를 조합하여 메뉴얼 전체 DTO를 생성하는 서비스 */
@Service
class ManualAssemblyService(
    private val manualService: ManualService,
    private val decalService: DecalService,
    private val thumbnailService: ThumbnailService,
) {
    /** 메뉴얼 단건 조회 (데칼·썸네일 목록 포함). onlyPublished=true이면 미공개 시 404 */
    @Transactional(readOnly = true)
    fun getManual(
        id: Long,
        onlyPublished: Boolean = false,
    ): ManualDetailDto {
        log.debug { "getManual(id=$id, onlyPublished=$onlyPublished)" }
        val manual = manualService.getManualEntity(id)
        if (onlyPublished && !manual.published) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val manualId = ManualId(manual.id)
        val decals = decalService.getDecalsByManualId(id)
        val thumbnails = thumbnailService.findThumbnails(id).map { "/manuals/$manualId/thumbnails/${it.pageNumber}" }
        return ManualDetailDto(
            id = manualId,
            grade = manual.grade,
            modelNumber = manual.modelNumber,
            productName = manual.productName,
            decals = decals,
            thumbnails = thumbnails,
            link = manual.link,
            published = manual.published,
        )
    }

    /** 메뉴얼 등록: DB 저장 후 썸네일 생성 */
    @Transactional
    fun createManual(
        grade: Grade,
        modelNumber: String,
        productName: String,
        pdfFile: MultipartFile? = null,
        pdfUrl: String? = null,
        link: String? = null,
    ): ManualSummaryDto {
        log.debug { "createManual(grade=$grade, modelNumber=$modelNumber, productName=$productName)" }
        val manual = manualService.createManual(grade, modelNumber, productName, pdfFile, pdfUrl, link)
        thumbnailService.generateThumbnails(manual)
        return ManualSummaryDto(
            id = ManualId(manual.id),
            grade = manual.grade,
            modelNumber = manual.modelNumber,
            productName = manual.productName,
            link = manual.link,
            published = manual.published,
        )
    }

    /** 메뉴얼 삭제: 썸네일 파일 제거 후 PDF·DB 레코드 삭제 */
    @Transactional
    fun deleteManual(id: Long) {
        log.debug { "deleteManual(id=$id)" }
        thumbnailService.deleteFiles(id)
        manualService.deleteManual(id)
    }
}
