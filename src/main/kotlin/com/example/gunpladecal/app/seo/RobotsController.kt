package com.example.gunpladecal.app.seo

import net.menoita.sitemap.config.SitemapProperties
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class RobotsController(
    private val sitemapProperties: SitemapProperties,
) {
    @GetMapping("/robots.txt", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun robotsTxt(): String =
        """
        User-agent: *
        Disallow: /admin
        Disallow: /login
        
        # AI crawlers
        User-agent: GPTBot
        Allow: /
        
        User-agent: ChatGPT-User
        Allow: /
        
        User-agent: ClaudeBot
        Allow: /
        
        User-agent: PerplexityBot
        Allow: /
        
        User-agent: Googlebot
        Allow: /
        
        Sitemap: ${sitemapProperties.baseUrl}/sitemap.xml
        """.trimIndent()
}
