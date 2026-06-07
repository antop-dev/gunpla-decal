package com.example.gunpladecal.config

import com.example.gunpladecal.app.domain.ManualId
import com.fasterxml.jackson.databind.module.SimpleModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JacksonConfig {
    @Bean
    fun manualIdModule(): SimpleModule =
        SimpleModule().apply {
            addSerializer(ManualId::class.java, ManualIdSerializer())
            addDeserializer(ManualId::class.java, ManualIdDeserializer())
        }
}
