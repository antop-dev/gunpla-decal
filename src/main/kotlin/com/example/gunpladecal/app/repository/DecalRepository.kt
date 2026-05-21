package com.example.gunpladecal.app.repository

import com.example.gunpladecal.app.domain.Decal
import org.springframework.data.jpa.repository.JpaRepository

interface DecalRepository : JpaRepository<Decal, Long> {
    fun findByManualIdOrderByDecalNumber(manualId: Long): List<Decal>
}
