package com.example.gunpladecal.app.service

import com.example.gunpladecal.app.domain.Decal
import com.example.gunpladecal.app.domain.Grade
import com.example.gunpladecal.app.domain.Manual
import com.example.gunpladecal.app.dto.DecalCreateRequest
import com.example.gunpladecal.app.dto.DecalResponse
import com.example.gunpladecal.app.dto.DecalUpdateRequest
import com.example.gunpladecal.app.dto.ManualDetail
import com.example.gunpladecal.app.dto.ManualSummary
import com.example.gunpladecal.app.dto.ManualUpdateRequest
import com.example.gunpladecal.app.repository.DecalRepository
import com.example.gunpladecal.app.repository.ManualRepository
import com.example.gunpladecal.app.util.Base62
import com.example.gunpladecal.config.AppProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
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

/** 메뉴얼·데칼 CRUD 비즈니스 로직 */
@Service
@Transactional
class ManualService(
    private val manualRepository: ManualRepository,
    private val decalRepository: DecalRepository,
    private val thumbnailService: ThumbnailService,
    private val appProperties: AppProperties,
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
    ): List<ManualSummary> {
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

    /** 메뉴얼 단건 조회 (데칼 목록 포함). onlyPublished=true이면 미공개 시 404 */
    @Transactional(readOnly = true)
    fun getManual(
        id: Long,
        onlyPublished: Boolean = false,
    ): ManualDetail {
        log.debug { "getManual(id=$id, onlyPublished=$onlyPublished)" }
        val manual = findManualById(id)
        if (onlyPublished && !manual.published) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val decals = decalRepository.findByManualIdOrderByDecalNumber(id).map { it.toResponse() }
        return manual.toDetail(decals)
    }

    /** 공개 여부 토글 */
    fun togglePublished(id: Long): ManualSummary {
        log.debug { "togglePublished(id=$id)" }
        val manual = findManualById(id)
        manual.published = !manual.published
        return manualRepository.save(manual).toSummary()
    }

    /** 메뉴얼 등록: PDF 파일 업로드 또는 URL 다운로드로 저장 */
    fun createManual(
        grade: Grade,
        modelNumber: String,
        productName: String,
        pdfFile: MultipartFile? = null,
        pdfUrl: String? = null,
        link: String? = null,
    ): ManualSummary {
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
        val saved = manualRepository.save(manual)
        thumbnailService.generateThumbnails(saved)
        return saved.toSummary()
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
        request: ManualUpdateRequest,
    ): ManualSummary {
        log.debug { "updateManual(id=$id, request=$request)" }
        validateLink(request.link)
        val manual = findManualById(id)
        request.grade?.let { manual.grade = it }
        request.modelNumber?.let { manual.modelNumber = it }
        request.productName?.let { manual.productName = it }
        if (request.link != null) manual.link = request.link.takeIf { it.isNotBlank() }
        return manualRepository.save(manual).toSummary()
    }

    /** 메뉴얼 삭제: DB 레코드와 업로드된 PDF·썸네일 파일을 함께 제거 */
    fun deleteManual(id: Long) {
        log.debug { "deleteManual(id=$id)" }
        val manual = findManualById(id)
        thumbnailService.deleteFiles(manual.id)
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
        val manual = findManualById(id)
        if (onlyPublished && !manual.published) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val path = Paths.get(manual.pdfPath)
        if (!Files.exists(path)) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return FileSystemResource(path)
    }

    /** 데칼 등록 */
    fun addDecal(
        manualId: Long,
        request: DecalCreateRequest,
    ): DecalResponse {
        log.debug { "addDecal(manualId=$manualId, request=$request)" }
        val manual = findManualById(manualId)
        val decal =
            Decal(
                manual = manual,
                pageNumber = request.page,
                decalNumber = request.decalNumber,
                x = request.x,
                y = request.y,
                color = request.color,
                shape = request.shape,
            )
        return decalRepository.save(decal).toResponse()
    }

    /** 데칼 정보 수정 (null 필드는 변경하지 않음) */
    fun updateDecal(
        manualId: Long,
        decalId: Long,
        request: DecalUpdateRequest,
    ): DecalResponse {
        log.debug { "updateDecal(manualId=$manualId, decalId=$decalId, request=$request)" }
        val decal = findDecal(manualId, decalId)
        request.page?.let { decal.pageNumber = it }
        request.decalNumber?.let { decal.decalNumber = it }
        request.x?.let { decal.x = it }
        request.y?.let { decal.y = it }
        request.color?.let { decal.color = it }
        request.shape?.let { decal.shape = it }
        return decalRepository.save(decal).toResponse()
    }

    /** 데칼 삭제 */
    fun deleteDecal(
        manualId: Long,
        decalId: Long,
    ): DecalResponse {
        log.debug { "deleteDecal(manualId=$manualId, decalId=$decalId)" }
        val decal = findDecal(manualId, decalId)
        decalRepository.delete(decal)
        return decal.toResponse()
    }

    private fun findDecal(
        manualId: Long,
        decalId: Long,
    ): Decal {
        val decal =
            decalRepository
                .findById(decalId)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND) }
        if (decal.manual.id != manualId) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return decal
    }

    private fun findManualById(id: Long): Manual =
        manualRepository.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND) }

    private fun validateLink(link: String?) {
        if (!link.isNullOrBlank() && !link.startsWith("https://")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "링크는 https://로 시작해야 합니다")
        }
    }

    private fun Manual.toSummary() = ManualSummary(id, Base62.encode(id * 23), grade, modelNumber, productName, link, published)

    private fun Manual.toDetail(decals: List<DecalResponse>) =
        ManualDetail(id, Base62.encode(id * 23), grade, modelNumber, productName, decals, link)

    private fun Decal.toResponse() = DecalResponse(id, pageNumber, decalNumber, x, y, color, shape)
}
