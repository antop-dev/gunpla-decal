package ai.antop.gunpla.app.service

import ai.antop.gunpla.app.domain.Grade
import ai.antop.gunpla.app.domain.Manual
import ai.antop.gunpla.app.domain.ManualId
import ai.antop.gunpla.app.dto.ManualItemDto
import ai.antop.gunpla.app.dto.ManualSummaryDto
import ai.antop.gunpla.app.dto.ManualUpdateRequestDto
import ai.antop.gunpla.app.event.ManualChangedEvent
import ai.antop.gunpla.app.repository.ManualRepository
import ai.antop.gunpla.common.shorty.ShortyUrlShortener
import ai.antop.gunpla.config.AppProperties
import jakarta.annotation.PostConstruct
import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID

/** 메뉴얼 CRUD 비즈니스 로직 */
@Service
@Transactional
class ManualService(
    private val manualRepository: ManualRepository,
    private val appProperties: AppProperties,
    private val eventPublisher: ApplicationEventPublisher,
    private val shortyUrlShortener: ShortyUrlShortener,
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

    /**
     * 관리자 목록 검색 (미공개 포함). 각 조건은 null·빈 값이면 무시하며,
     * 형식번호·제품명은 부분 일치(대소문자 무시)로 비교한다.
     */
    @Transactional(readOnly = true)
    fun searchManuals(
        grade: Grade?,
        published: Boolean?,
        modelNumber: String?,
        productName: String?,
    ): List<ManualSummaryDto> =
        manualRepository
            .findAllByOrderByIdDesc()
            .filter { m ->
                (grade == null || m.grade == grade) &&
                    (published == null || m.published == published) &&
                    (modelNumber.isNullOrBlank() || m.modelNumber.contains(modelNumber, ignoreCase = true)) &&
                    (productName.isNullOrBlank() || m.productName.contains(productName, ignoreCase = true))
            }.map { it.toSummary() }

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

    /** PDF를 디스크에만 저장하고 경로를 반환. DB 레코드는 생성하지 않는다 */
    fun savePdfFile(
        pdfBytes: ByteArray?,
        pdfUrl: String?,
        pdfNumbers: List<String>? = null,
    ): Path {
        val dest = Paths.get(appProperties.uploadDir, "${UUID.randomUUID()}.pdf").toAbsolutePath()
        when {
            pdfBytes?.isNotEmpty() == true -> Files.write(dest, pdfBytes)
            !pdfUrl.isNullOrBlank() -> downloadFromUrl(pdfUrl, dest)
            !pdfNumbers.isNullOrEmpty() -> downloadAndMergeByNumbers(pdfNumbers, dest)
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF 파일 또는 URL을 입력해주세요")
        }
        return dest
    }

    /** Manual DB 레코드를 저장하고 반환. PDF 파일은 이미 디스크에 있어야 한다 */
    @Transactional
    fun saveManualRecord(
        grade: Grade,
        modelNumber: String,
        productName: String,
        pdfPath: String,
        pageCount: Int,
        link: String?,
    ): ManualItemDto {
        val manual =
            Manual(
                grade = grade,
                modelNumber = modelNumber,
                productName = productName,
                pdfPath = pdfPath,
                pageCount = pageCount,
                link = link?.takeIf { it.isNotBlank() }?.let { shortyUrlShortener.shorten(it) },
            )
        return manualRepository.save(manual).toDto()
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

    /** 번호 목록으로 반다이 호비 메뉴얼 PDF를 각각 다운로드한 뒤 하나로 병합하여 dest 경로에 저장 */
    private fun downloadAndMergeByNumbers(
        numbers: List<String>,
        dest: Path,
    ) {
        val invalid = numbers.firstOrNull { !MANUAL_NUMBER_REGEX.matches(it) }
        if (invalid != null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$invalid: 번호는 숫자와 '_'만 입력할 수 있습니다")
        }
        val tempFiles = numbers.map { Files.createTempFile("manual-pdf-", ".pdf") }
        try {
            numbers.zip(tempFiles).forEach { (number, tempFile) -> downloadPdfByNumber(number, tempFile) }
            val merger = PDFMergerUtility()
            tempFiles.forEach { merger.addSource(it.toFile()) }
            merger.destinationFileName = dest.toString()
            merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly().streamCache)
        } finally {
            tempFiles.forEach { Files.deleteIfExists(it) }
        }
    }

    /** 반다이 호비 메뉴얼 사이트에서 번호에 해당하는 PDF를 다운로드하여 dest 경로에 저장. 실제 PDF가 아니면(존재하지 않는 번호) 400 예외 발생 */
    private fun downloadPdfByNumber(
        number: String,
        dest: Path,
    ) {
        val url = "https://manual.bandai-hobby.net/pdf/$number.pdf"
        val conn =
            try {
                URI(url).toURL().openConnection() as HttpURLConnection
            } catch (_: Exception) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$number: 유효하지 않은 번호입니다")
            }
        try {
            conn.connect()
            val code = conn.responseCode
            if (code !in 200..299) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$number: PDF를 찾을 수 없습니다 (HTTP $code)")
            }
            conn.inputStream.use { Files.copy(it, dest, StandardCopyOption.REPLACE_EXISTING) }
        } catch (e: ResponseStatusException) {
            throw e
        } catch (_: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$number: PDF 다운로드 중 오류가 발생했습니다")
        } finally {
            conn.disconnect()
        }
        if (!isPdfFile(dest)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$number: 존재하지 않는 메뉴얼 번호입니다")
        }
    }

    /** 파일 시작 부분의 매직 바이트(%PDF-)로 실제 PDF 여부를 판별. 존재하지 않는 번호는 HTML 안내 페이지가 200으로 내려오므로 이 검사가 필요하다 */
    private fun isPdfFile(path: Path): Boolean {
        val header = ByteArray(PDF_MAGIC.size)
        val read = Files.newInputStream(path).use { it.read(header) }
        return read == PDF_MAGIC.size && header.contentEquals(PDF_MAGIC)
    }

    /** 메뉴얼 삭제: DB 레코드와 업로드된 PDF 파일 제거 (썸네일 삭제는 AdminService에서 처리) */
    fun deleteManual(manualId: ManualId) {
        val manual = getManualEntity(manualId)
        Files.deleteIfExists(Paths.get(appProperties.uploadDir, manual.pdfPath))
        manualRepository.delete(manual)
    }

    /** PDF 파일 리소스 반환. 파일이 존재하지 않으면 404 예외 발생 */
    @Transactional(readOnly = true)
    fun getPdfResource(manualId: ManualId): Resource {
        val manual = getManualEntity(manualId)
        val path = Paths.get(appProperties.uploadDir, manual.pdfPath)
        if (!Files.exists(path)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        return FileSystemResource(path)
    }

    /** 다운로드용 PDF 파일명 반환. "&#91;등급&#93; 형식번호 제품명.pdf" 형태이며 파일명에 쓸 수 없는 문자는 _로 치환한다 */
    @Transactional(readOnly = true)
    fun getPdfFileName(manualId: ManualId): String {
        val manual = getManualEntity(manualId)
        val name =
            "[${manual.grade}] ${manual.modelNumber} ${manual.productName}"
                .replace(Regex("\\s+"), " ")
                .trim()
                .replace(Regex("""[\\/:*?"<>|]"""), "_")
        return "$name.pdf"
    }

    /** 메뉴얼 단건 조회. 존재하지 않으면 null 반환 */
    fun getManual(manualId: ManualId): ManualItemDto? {
        val manual = manualRepository.findByIdOrNull(manualId.value)
        return manual?.toDto()
    }

    /** 메뉴얼 정보 수정 (등급·형식번호·제품명·링크). null 링크는 변경하지 않고, 변경된 링크는 짧은 URL로 저장한다 */
    fun updateManual(
        manualId: ManualId,
        request: ManualUpdateRequestDto,
    ) {
        val manual = getManualEntity(manualId)
        manual.grade = request.grade
        manual.modelNumber = request.modelNumber
        manual.productName = request.productName
        request.link?.let { newLink ->
            if (newLink != manual.link) {
                manual.link = if (newLink.isBlank()) newLink else shortyUrlShortener.shorten(newLink)
            }
        }
    }

    /** 서비스 간 호출용: Manual 엔티티 단건 조회. 존재하지 않으면 404 예외 발생 */
    @Transactional(readOnly = true)
    fun getManualEntity(manualId: ManualId): Manual =
        manualRepository.findByIdOrNull(manualId.value) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

    private fun Manual.toSummary() = ManualSummaryDto(ManualId(id), grade, modelNumber, productName, link, published, createdAt, updatedAt)

    private fun Manual.toDto() =
        ManualItemDto(ManualId(id), grade, modelNumber, productName, pdfPath, pageCount, link, published, createdAt, updatedAt)

    companion object {
        private val MANUAL_NUMBER_REGEX = Regex("^[0-9_]+$")
        private val PDF_MAGIC = "%PDF-".toByteArray()
    }
}
