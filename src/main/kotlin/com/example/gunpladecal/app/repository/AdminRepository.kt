package com.example.gunpladecal.app.repository

import com.example.gunpladecal.app.domain.Admin
import org.springframework.data.jpa.repository.JpaRepository

interface AdminRepository : JpaRepository<Admin, Long> {
    fun findByUsername(username: String): Admin?
}
