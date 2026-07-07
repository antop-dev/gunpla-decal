package ai.antop.gunpla.app.repository

import ai.antop.gunpla.app.domain.JapaneseCharUsage
import org.springframework.data.jpa.repository.JpaRepository

interface JapaneseCharUsageRepository : JpaRepository<JapaneseCharUsage, Long> {
    fun findByCharacter(character: String): JapaneseCharUsage?

    fun findTop20ByCountGreaterThanOrderByCountDesc(count: Int): List<JapaneseCharUsage>
}
