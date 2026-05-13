package com.example.gunpladecal.app.service

import com.example.gunpladecal.app.domain.Manual
import com.example.gunpladecal.app.repository.ManualRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/** Manual 엔티티 단건 조회 캐시 레이어. ManualService와 분리된 별도 빈으로 self-invocation 문제를 방지한다. */
@Component
class ManualEntityCache(private val manualRepository: ManualRepository) {
    @Cacheable(cacheNames = ["gunpla-decal-manual"], key = "#id")
    @Transactional(readOnly = true)
    fun findById(id: Long): Manual =
        manualRepository.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND) }
}
