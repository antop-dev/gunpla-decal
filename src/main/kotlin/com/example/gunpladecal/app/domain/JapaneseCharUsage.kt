package com.example.gunpladecal.app.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** 일본어 문자별 사용 횟수를 저장하는 엔티티 */
@Entity
@Table(name = "japanese_char_usage")
class JapaneseCharUsage(
    @Column(nullable = false, unique = true)
    var character: String,
    @Column(nullable = false)
    var count: Int = 0,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
)
