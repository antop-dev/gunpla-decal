package com.example.gunpladecal.app.repository

import com.example.gunpladecal.app.domain.JapaneseCharUsage
import org.springframework.data.jpa.repository.JpaRepository

interface JapaneseCharUsageRepository : JpaRepository<JapaneseCharUsage, Long> {
    fun findByCharacter(character: String): JapaneseCharUsage?
    fun findAllByOrderByCountDesc(): List<JapaneseCharUsage>
}
