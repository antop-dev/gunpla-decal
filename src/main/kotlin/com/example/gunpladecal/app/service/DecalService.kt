package com.example.gunpladecal.app.service

import com.example.gunpladecal.app.domain.Decal
import com.example.gunpladecal.app.dto.DecalCreateRequestDto
import com.example.gunpladecal.app.dto.DecalResponseDto
import com.example.gunpladecal.app.dto.DecalUpdateRequestDto
import com.example.gunpladecal.app.repository.DecalRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

private val log = KotlinLogging.logger {}

/** 데칼 CRUD 비즈니스 로직 */
@Service
@Transactional
class DecalService(
    private val decalRepository: DecalRepository,
    private val manualService: ManualService,
) {
    /** 메뉴얼에 속한 데칼 목록 반환 */
    @Transactional(readOnly = true)
    fun getDecalsByManualId(manualId: Long): List<DecalResponseDto> =
        decalRepository.findByManualIdOrderByDecalNumber(manualId).map { it.toResponse() }

    /** 데칼 등록 */
    fun addDecal(
        manualId: Long,
        request: DecalCreateRequestDto,
    ): DecalResponseDto {
        log.debug { "addDecal(manualId=$manualId, request=$request)" }
        val manual = manualService.getManualEntity(manualId)
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
        request: DecalUpdateRequestDto,
    ): DecalResponseDto {
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
    ) {
        log.debug { "deleteDecal(manualId=$manualId, decalId=$decalId)" }
        val decal = findDecal(manualId, decalId)
        decalRepository.delete(decal)
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

    private fun Decal.toResponse() = DecalResponseDto(id, pageNumber, decalNumber, x, y, color, shape)
}
