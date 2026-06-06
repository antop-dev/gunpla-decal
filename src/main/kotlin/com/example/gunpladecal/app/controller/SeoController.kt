package com.example.gunpladecal.app.controller

import com.example.gunpladecal.app.dto.Sitemap
import com.example.gunpladecal.app.repository.ManualRepository
import com.example.gunpladecal.app.util.Base62
import com.example.gunpladecal.config.AppProperties
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.rometools.rome.feed.synd.SyndContentImpl
import com.rometools.rome.feed.synd.SyndEntryImpl
import com.rometools.rome.feed.synd.SyndFeedImpl
import com.rometools.rome.io.SyndFeedOutput
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.ZoneId
import java.util.Date

@RestController
class SeoController(
    private val appProperties: AppProperties,
    private val manualRepository: ManualRepository,
) {
    private val xmlMapper =
        XmlMapper().apply {
            enable(ToXmlGenerator.Feature.WRITE_XML_DECLARATION)
            registerModules(KotlinModule.Builder().build(), JavaTimeModule())
        }

    @GetMapping("/robots.txt", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun robotsTxt(): String {
        val baseUrl = appProperties.baseUrl
        return """
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
            
            Sitemap: $baseUrl/sitemap.xml
            
            # RSS / Atom Feed
            Feed: $baseUrl/rss.xml
            Feed: $baseUrl/atom.xml
            """.trimIndent()
    }

    @GetMapping("/sitemap.xml", produces = ["application/xml;charset=UTF-8"])
    fun sitemap(): String {
        val urls =
            manualRepository.findAllByPublishedTrueOrderByIdDesc().map {
                Sitemap.SitemapUrl(
                    loc = appProperties.baseUrl + "/" + Base62.encode(it.id),
                    lastmod = it.updatedAt.atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime(),
                    changefreq = "daily",
                    priority = 0.5,
                )
            }
        return xmlMapper.writeValueAsString(Sitemap(urls = urls))
    }

    @GetMapping("/rss.xml", produces = ["application/rss+xml;charset=UTF-8"])
    fun rss(): String = buildFeed("rss_2.0")

    @GetMapping("/atom.xml", produces = ["application/atom+xml;charset=UTF-8"])
    fun atom(): String = buildFeed("atom_1.0")

    private fun buildFeed(feedType: String): String {
        val baseUrl = appProperties.baseUrl
        val feed =
            SyndFeedImpl().apply {
                this.feedType = feedType
                title = "건담프라 데칼 메뉴얼"
                link = baseUrl
                description = "건담프라 데칼 메뉴얼 목록"
                entries =
                    manualRepository.findAllByPublishedTrueOrderByIdDesc().map { manual ->
                        SyndEntryImpl().apply {
                            title = "[${manual.grade}] ${manual.modelNumber} ${manual.productName}"
                            link = "$baseUrl/${Base62.encode(manual.id * 23)}"
                            // DB ID 직접 노출 방지를 위해 * 23 으로 난독화. ManualController 에서 / 23 으로 역산
                            publishedDate =
                                Date.from(manual.createdAt.atZone(ZoneId.systemDefault()).toInstant())
                            description =
                                SyndContentImpl().apply {
                                    type = "text/plain"
                                    value = "[${manual.grade}] ${manual.modelNumber} ${manual.productName}"
                                }
                        }
                    }
            }
        return SyndFeedOutput().outputString(feed)
    }
}
