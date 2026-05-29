package com.example.gunpladecal.app.repository

import com.example.gunpladecal.app.domain.Manual
import org.springframework.data.jpa.repository.JpaRepository

interface ManualRepository : JpaRepository<Manual, Long> {
    fun findAllByOrderByIdDesc(): List<Manual>

    fun findAllByPublishedTrueOrderByIdDesc(): List<Manual>
}
