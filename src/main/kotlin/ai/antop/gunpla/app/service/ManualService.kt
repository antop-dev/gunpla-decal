package ai.antop.gunpla.app.service

import ai.antop.gunpla.app.domain.Grade
import ai.antop.gunpla.app.domain.Manual
import ai.antop.gunpla.app.domain.ManualId
import ai.antop.gunpla.app.dto.ManualItemDto
import ai.antop.gunpla.app.dto.ManualSummaryDto
import ai.antop.gunpla.app.dto.ManualUpdateRequestDto
import ai.antop.gunpla.app.event.ManualChangedEvent
import ai.antop.gunpla.app.repository.ManualRepository
import ai.antop.gunpla.config.AppProperties
import jakarta.annotation.PostConstruct
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.data.repository.findByIdOrNull
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
        val all = manualRepository.findAllByOrderByIdDesc()
        val base = if (onlyPublished) all.filter { it.published } else all
        if (q.isNullOrBlank()) {
            return base.map { it.toSummary() }
        }
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

    /** 메뉴얼 공개 여부 설정. published=true이면 ManualChangedEvent를 발행하여 캐시를 무효화한다 */
    fun updatePublished(
        manualId: ManualId,
        published: Boolean,
    ) {
        val manual = getManualEntity(manualId)
        manual.published = published
        if (manual.published) {
            eventPublisher.publishEvent(ManualChangedEvent(manualId))
        }
    }

    /** 메뉴얼 등록: PDF 파일 업로드 또는 URL 다운로드로 저장 (썸네일 생성은 AdminService에서 처리) */
    fun createManual(
        grade: Grade,
        modelNumber: String,
        productName: String,
        pdfFile: MultipartFile? = null,
        pdfUrl: String? = null,
        link: String? = null,
    ): ManualItemDto {
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
                link = link?.takeIf { it.isNotBlank() },
            )
        val saved = manualRepository.save(manual)
        return saved.toDto()
    }

    /** URL에서 PDF를 다운로드하여 dest 경로에 저장. HTTP 상태 코드 오류 시 400 예외 발생 */
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
            if (code == 404) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF URL이 존재하지 않습니다 (404)")
            }
            if (code !in 200..299) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF 다운로드 실패 (HTTP $code)")
            }
            conn.inputStream.use { Files.copy(it, dest) }
        } catch (e: ResponseStatusException) {
            throw e
        } catch (_: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF 다운로드 중 오류가 발생했습니다")
        } finally {
            conn.disconnect()
        }
    }

    /** 메뉴얼 삭제: DB 레코드와 업로드된 PDF 파일 제거 (썸네일 삭제는 AdminService에서 처리) */
    fun deleteManual(manualId: ManualId) {
        val manual = getManualEntity(manualId)
        Files.deleteIfExists(Paths.get(manual.pdfPath))
        manualRepository.delete(manual)
    }

    /** PDF 파일 리소스 반환. 파일이 존재하지 않으면 404 예외 발생 */
    @Transactional(readOnly = true)
    fun getPdfResource(manualId: ManualId): Resource {
        val manual = getManualEntity(manualId)
        val path = Paths.get(manual.pdfPath)
        if (!Files.exists(path)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        return FileSystemResource(path)
    }

    /** 메뉴얼 단건 조회. 존재하지 않으면 null 반환 */
    fun getManual(manualId: ManualId): ManualItemDto? {
        val manual = manualRepository.findByIdOrNull(manualId.value)
        return manual?.toDto()
    }

    /** 메뉴얼 정보 수정 (등급·형식번호·제품명·링크). null 링크는 변경하지 않음 */
    fun updateManual(
        manualId: ManualId,
        request: ManualUpdateRequestDto,
    ) {
        val manual = getManualEntity(manualId)
        manual.grade = request.grade
        manual.modelNumber = request.modelNumber
        manual.productName = request.productName
        request.link?.let { manual.link = it }
    }

    /** 서비스 간 호출용: Manual 엔티티 단건 조회. 존재하지 않으면 404 예외 발생 */
    @Transactional(readOnly = true)
    fun getManualEntity(manualId: ManualId): Manual =
        manualRepository.findByIdOrNull(manualId.value) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

    private fun Manual.toSummary() = ManualSummaryDto(ManualId(id), grade, modelNumber, productName, link, published, createdAt, updatedAt)

    private fun Manual.toDto() =
        ManualItemDto(ManualId(id), grade, modelNumber, productName, pdfPath, link, published, createdAt, updatedAt)
}
