package com.example.gunpladecal.app.service

import com.example.gunpladecal.app.domain.Grade
import com.example.gunpladecal.app.domain.Manual
import com.example.gunpladecal.app.domain.ManualId
import com.example.gunpladecal.app.dto.ManualFeedItemDto
import com.example.gunpladecal.app.dto.ManualSummaryDto
import com.example.gunpladecal.app.dto.ManualUpdateRequestDto
import com.example.gunpladecal.app.event.ManualPublishedEvent
import com.example.gunpladecal.app.repository.ManualRepository
import com.example.gunpladecal.config.AppProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

private val log = KotlinLogging.logger {}

/** 메뉴얼 CRUD 비즈니스 로직 */
@Service
@Transactional
class ManualService(
    private val manualRepository: ManualRepository,
    private val appProperties: AppProperties,
    private val eventPublisher: ApplicationEventPublisher,
) {
    /** 애플리케이션 시작 시 PDF 업로드 디렉터리가 없으면 생성 */
    @PostConstruct
    fun init() {
        Files.createDirectories(Paths.get(appProperties.uploadDir))
    }

    /**
     * 메뉴얼 목록 반환. q가 있으면 등급명·형식번호·제품명으로 필터링 (대소문자 무시).
     * onlyPublished=true이면 공개 메뉴얼만 반환.
     */
    @Transactional(readOnly = true)
    fun getAllManuals(
        q: String? = null,
        onlyPublished: Boolean = false,
    ): List<ManualSummaryDto> {
        log.debug { "getAllManuals(q=$q, onlyPublished=$onlyPublished)" }
        val all = manualRepository.findAllByOrderByIdDesc()
        val base = if (onlyPublished) all.filter { it.published } else all
        if (q.isNullOrBlank()) return base.map { it.toSummary() }
        val lower = q.lowercase()
        return base
            .filter { m ->
                m.grade.name
                    .lowercase()
                    .contains(lower) ||
                    m.modelNumber.lowercase().contains(lower) ||
                    m.productName.lowercase().contains(lower)
            }.map { it.toSummary() }
    }

    /** 공개된 메뉴얼의 피드 아이템 목록 반환 (sitemap/RSS/Atom 전용) */
    @Transactional(readOnly = true)
    fun getAllPublishedFeedItems(): List<ManualFeedItemDto> = manualRepository.findAllByPublishedTrueOrderByIdDesc().map { it.toFeedItem() }

    /** 공개 여부 토글 */
    fun togglePublished(id: Long): ManualSummaryDto {
        log.debug { "togglePublished(id=$id)" }
        val manual = getManualEntity(id)
        manual.published = !manual.published
        val saved = manualRepository.save(manual)
        eventPublisher.publishEvent(ManualPublishedEvent(saved.id, saved.published, saved.createdAt))
        return saved.toSummary()
    }

    /** 메뉴얼 등록: PDF 파일 업로드 또는 URL 다운로드로 저장 (썸네일 생성은 ManualAssemblyService에서 처리) */
    fun createManual(
        grade: Grade,
        modelNumber: String,
        productName: String,
        pdfFile: MultipartFile? = null,
        pdfUrl: String? = null,
        link: String? = null,
    ): Manual {
        log.debug { "createManual(grade=$grade, modelNumber=$modelNumber, productName=$productName)" }
        validateLink(link)
        val dest = Paths.get(appProperties.uploadDir, "${UUID.randomUUID()}.pdf").toAbsolutePath()
        when {
            pdfFile != null && !pdfFile.isEmpty -> pdfFile.transferTo(dest)
            !pdfUrl.isNullOrBlank() -> downloadFromUrl(pdfUrl, dest)
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF 파일 또는 URL을 입력해주세요")
        }
        val manual =
            Manual(
                grade = grade,
                modelNumber = modelNumber,
                productName = productName,
                pdfPath = dest.toString(),
                link =
                    link?.takeIf {
                        it.isNotBlank()
                    },
            )
        return manualRepository.save(manual)
    }

    private fun downloadFromUrl(
        url: String,
        dest: Path,
    ) {
        val conn =
            try {
                URI(url).toURL().openConnection() as HttpURLConnection
            } catch (_: Exception) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 URL입니다")
            }
        try {
            conn.connect()
            val code = conn.responseCode
            if (code == 404) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF URL이 존재하지 않습니다 (404)")
            if (code !in 200..299) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF 다운로드 실패 (HTTP $code)")
            conn.inputStream.use { Files.copy(it, dest) }
        } catch (e: ResponseStatusException) {
            throw e
        } catch (_: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF 다운로드 중 오류가 발생했습니다")
        } finally {
            conn.disconnect()
        }
    }

    /** 메뉴얼 정보 수정 (등급·형식번호·제품명, null 필드는 변경하지 않음) */
    fun updateManual(
        id: Long,
        request: ManualUpdateRequestDto,
    ): ManualSummaryDto {
        log.debug { "updateManual(id=$id, request=$request)" }
        validateLink(request.link)
        val manual = getManualEntity(id)
        request.grade?.let { manual.grade = it }
        request.modelNumber?.let { manual.modelNumber = it }
        request.productName?.let { manual.productName = it }
        if (request.link != null) manual.link = request.link.takeIf { it.isNotBlank() }
        return manualRepository.save(manual).toSummary()
    }

    /** 메뉴얼 삭제: DB 레코드와 업로드된 PDF 파일 제거 (썸네일 삭제는 ManualAssemblyService에서 처리) */
    fun deleteManual(id: Long) {
        log.debug { "deleteManual(id=$id)" }
        val manual = getManualEntity(id)
        Files.deleteIfExists(Paths.get(manual.pdfPath))
        manualRepository.delete(manual)
    }

    /** PDF 파일 리소스 반환. 파일이 없거나 onlyPublished=true일 때 미공개이면 404 */
    @Transactional(readOnly = true)
    fun getPdfResource(
        id: Long,
        onlyPublished: Boolean = false,
    ): Resource {
        log.debug { "getPdfResource(id=$id, onlyPublished=$onlyPublished)" }
        val manual = getManualEntity(id)
        if (onlyPublished && !manual.published) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val path = Paths.get(manual.pdfPath)
        if (!Files.exists(path)) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return FileSystemResource(path)
    }

    /** 서비스 간 호출용: Manual 엔티티 단건 조회 */
    @Transactional(readOnly = true)
    fun getManualEntity(id: Long): Manual = manualRepository.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND) }

    /** 서비스 간 호출용: 전체 Manual 엔티티 목록 조회 */
    @Transactional(readOnly = true)
    fun getAllManualEntities(): List<Manual> = manualRepository.findAllByOrderByIdDesc()

    private fun validateLink(link: String?) {
        if (!link.isNullOrBlank() && !link.startsWith("https://")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "링크는 https://로 시작해야 합니다")
        }
    }

    private fun Manual.toSummary() = ManualSummaryDto(ManualId(id), grade, modelNumber, productName, link, published)

    private fun Manual.toFeedItem() = ManualFeedItemDto(ManualId(id), grade, modelNumber, productName, createdAt, updatedAt)
}
