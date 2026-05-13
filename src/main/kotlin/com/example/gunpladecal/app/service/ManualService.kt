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
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID

private val log = KotlinLogging.logger {}

/** 메뉴얼·데칼 CRUD 비즈니스 로직 */
@Service
@Transactional
class ManualService(
    private val manualRepository: ManualRepository,
    private val decalRepository: DecalRepository,
    private val appProperties: AppProperties,
    private val manualEntityCache: ManualEntityCache,
) {
    /** 애플리케이션 시작 시 PDF 업로드 디렉터리가 없으면 생성 */
    @PostConstruct
    fun init() {
        Files.createDirectories(Paths.get(appProperties.uploadDir))
    }

    /**
     * 메뉴얼 목록 반환. q가 있으면 등급명·형식번호·제품명으로 필터링 (대소문자 무시).
     * 서버 사이드 검색이므로 추후 DB 풀텍스트 검색으로 전환 가능.
     */
    @Transactional(readOnly = true)
    fun getAllManuals(q: String? = null): List<ManualSummary> {
        log.debug { "getAllManuals(q=$q)" }
        val all = manualRepository.findAllByOrderByIdDesc()
        if (q.isNullOrBlank()) return all.map { it.toSummary() }
        // 등급명·형식번호·제품명에 검색어가 포함된 메뉴얼 반환 (대소문자 무시)
        val lower = q.lowercase()
        return all
            .filter { m ->
                m.grade.name.lowercase().contains(lower) ||
                    m.modelNumber.lowercase().contains(lower) ||
                    m.productName.lowercase().contains(lower)
            }
            .map { it.toSummary() }
    }

    /** 메뉴얼 단건 조회 (데칼 목록 포함) */
    @Cacheable(cacheNames = ["gunpla-decal-manuals"], key = "#id")
    @Transactional(readOnly = true)
    fun getManual(id: Long): ManualDetail {
        log.debug { "getManual(id=$id)" }
        val manual = manualEntityCache.findById(id)
        val decals = decalRepository.findByManualIdOrderByDecalNumber(id).map { it.toResponse() }
        return manual.toDetail(decals)
    }

    /** 메뉴얼 등록: PDF 파일을 UUID 파일명으로 업로드 디렉터리에 저장 */
    fun createManual(
        grade: Grade,
        modelNumber: String,
        productName: String,
        pdfFile: MultipartFile,
        link: String? = null,
    ): ManualSummary {
        log.debug { "createManual(grade=$grade, modelNumber=$modelNumber, productName=$productName, filename=${pdfFile.originalFilename})" }
        validateLink(link)
        val path = Paths.get(appProperties.uploadDir, "${UUID.randomUUID()}.pdf").toAbsolutePath()
        pdfFile.transferTo(path)
        val manual = Manual(grade = grade, modelNumber = modelNumber, productName = productName, pdfPath = path.toString(), link = link?.takeIf { it.isNotBlank() })
        return manualRepository.save(manual).toSummary()
    }

    /** 메뉴얼 정보 수정 (등급·형식번호·제품명, null 필드는 변경하지 않음) */
    @Caching(evict = [
        CacheEvict(cacheNames = ["gunpla-decal-manual"], key = "#id"),
        CacheEvict(cacheNames = ["gunpla-decal-manuals"], key = "#id"),
    ])
    fun updateManual(
        id: Long,
        request: ManualUpdateRequest,
    ): ManualSummary {
        log.debug { "updateManual(id=$id, request=$request)" }
        validateLink(request.link)
        val manual = manualEntityCache.findById(id)
        request.grade?.let { manual.grade = it }
        request.modelNumber?.let { manual.modelNumber = it }
        request.productName?.let { manual.productName = it }
        if (request.link != null) manual.link = request.link.takeIf { it.isNotBlank() }
        return manualRepository.save(manual).toSummary()
    }

    /** 메뉴얼 삭제: DB 레코드와 업로드된 PDF 파일을 함께 제거 */
    @Caching(evict = [
        CacheEvict(cacheNames = ["gunpla-decal-manual"], key = "#id"),
        CacheEvict(cacheNames = ["gunpla-decal-manuals"], key = "#id"),
    ])
    fun deleteManual(id: Long) {
        log.debug { "deleteManual(id=$id)" }
        val manual = manualEntityCache.findById(id)
        Files.deleteIfExists(Paths.get(manual.pdfPath))
        manualRepository.delete(manual)
    }

    /** PDF 파일 리소스 반환. 파일이 없으면 404 응답 */
    @Transactional(readOnly = true)
    fun getPdfResource(id: Long): Resource {
        log.debug { "getPdfResource(id=$id)" }
        val manual = manualEntityCache.findById(id)
        val path = Paths.get(manual.pdfPath)
        if (!Files.exists(path)) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return FileSystemResource(path)
    }

    /** 데칼 등록 */
    @CacheEvict(cacheNames = ["gunpla-decal-manuals"], key = "#manualId")
    fun addDecal(
        manualId: Long,
        request: DecalCreateRequest,
    ): DecalResponse {
        log.debug { "addDecal(manualId=$manualId, request=$request)" }
        val manual = manualEntityCache.findById(manualId)
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
    @CacheEvict(cacheNames = ["gunpla-decal-manuals"], key = "#manualId")
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
    @CacheEvict(cacheNames = ["gunpla-decal-manuals"], key = "#manualId")
    fun deleteDecal(
        manualId: Long,
        decalId: Long,
    ): DecalResponse {
        log.debug { "deleteDecal(manualId=$manualId, decalId=$decalId)" }
        val decal = findDecal(manualId, decalId)
        decalRepository.delete(decal)
        return decal.toResponse()
    }

    /**
     * 데칼 조회 + 소유 메뉴얼 검증.
     * decalId가 해당 manualId 소속이 아니면 404 응답 (타 메뉴얼 데칼 접근 방지)
     */
    private fun findDecal(
        manualId: Long,
        decalId: Long,
    ): Decal {
        val decal =
            decalRepository.findById(decalId)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND) }
        if (decal.manual.id != manualId) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return decal
    }

    private fun validateLink(link: String?) {
        if (!link.isNullOrBlank() && !link.startsWith("https://"))
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "링크는 https://로 시작해야 합니다")
    }

    private fun Manual.toSummary() = ManualSummary(id, Base62.encode(id * 23), grade, modelNumber, productName, link)

    private fun Manual.toDetail(decals: List<DecalResponse>) = ManualDetail(id, Base62.encode(id * 23), grade, modelNumber, productName, decals, link)

    private fun Decal.toResponse() = DecalResponse(id, pageNumber, decalNumber, x, y, color, shape)
}
