package com.example.gunpladecal.config

import com.example.gunpladecal.app.domain.ManualId
import org.springframework.context.annotation.Configuration
import org.springframework.format.FormatterRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig : WebMvcConfigurer {
    override fun addFormatters(registry: FormatterRegistry) {
        registry.addConverter(String::class.java, ManualId::class.java) { ManualId.fromB62(it) }
    }
}
