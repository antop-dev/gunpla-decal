package com.example.gunpladecal.app.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "admin")
class Admin(
    @Column(nullable = false, unique = true, length = 50)
    val username: String,
    @Column(nullable = false)
    val password: String,
    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
