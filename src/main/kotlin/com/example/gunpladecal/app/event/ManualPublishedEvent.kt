package com.example.gunpladecal.app.event

import java.time.LocalDateTime

data class ManualPublishedEvent(
    val id: Long,
    val published: Boolean,
    val createdAt: LocalDateTime,
)
