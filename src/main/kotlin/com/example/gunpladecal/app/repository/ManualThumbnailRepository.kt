package com.example.gunpladecal.app.repository

import com.example.gunpladecal.app.domain.ManualThumbnail
import org.springframework.data.jpa.repository.JpaRepository

interface ManualThumbnailRepository : JpaRepository<ManualThumbnail, Long> {
    fun findByManualIdOrderByPageNumber(manualId: Long): List<ManualThumbnail>

    fun findByManualIdAndPageNumber(
        manualId: Long,
        pageNumber: Int,
    ): ManualThumbnail?

    fun existsByManualId(manualId: Long): Boolean
}
