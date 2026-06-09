package ai.antop.gunpla.app.service

import ai.antop.gunpla.app.domain.Decal
import ai.antop.gunpla.app.domain.ManualId
import ai.antop.gunpla.app.dto.DecalCreateRequestDto
import ai.antop.gunpla.app.dto.DecalItemDto
import ai.antop.gunpla.app.dto.DecalUpdateRequestDto
import ai.antop.gunpla.app.repository.DecalRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/** 데칼 CRUD 비즈니스 로직 */
@Service
@Transactional(readOnly = false)
class DecalService(
    private val decalRepository: DecalRepository,
) {
    /** 메뉴얼에 속한 데칼 목록 반환 (데칼 번호 오름차순) */
    fun getDecalsByManualId(manualId: ManualId): List<DecalItemDto> =
        decalRepository.findByManualIdOrderByDecalNumber(manualId.value).map { it.toDto() }

    /** 데칼 등록 */
    @Transactional
    fun addDecal(
        manualId: ManualId,
        request: DecalCreateRequestDto,
    ): DecalItemDto {
        val decal =
            Decal(
                manualId = manualId.value,
                pageNumber = request.pageNumber,
                decalNumber = request.decalNumber,
                x = request.x,
                y = request.y,
                color = request.color,
                shape = request.shape,
            )
        return decalRepository.save(decal).toDto()
    }

    /** 데칼 정보 수정 (번호·색상·도형). null 필드는 변경하지 않음 */
    @Transactional
    fun updateDecal(
        decalId: Long,
        request: DecalUpdateRequestDto,
    ): DecalItemDto {
        val decal = decalRepository.findByIdOrNull(decalId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        decal.decalNumber = request.decalNumber
        decal.color = request.color
        decal.shape = request.shape
        return decal.toDto()
    }

    /** 데칼 삭제 */
    @Transactional
    fun deleteDecal(decalId: Long) {
        val decal = getDecalEntity(decalId)
        decalRepository.delete(decal)
    }

    /** 메뉴얼에 속한 데칼 전체 삭제 (메뉴얼 삭제 시 호출) */
    @Transactional
    fun deleteDecals(manualId: ManualId) {
        decalRepository.deleteDecalsByManualIdQuery(manualId.value)
    }

    /** 데칼 엔티티 단건 조회. 존재하지 않으면 404 예외 발생 */
    private fun getDecalEntity(decalId: Long): Decal =
        decalRepository.findByIdOrNull(decalId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

    private fun Decal.toDto() = DecalItemDto(id, pageNumber, decalNumber, x, y, color, shape)
}
