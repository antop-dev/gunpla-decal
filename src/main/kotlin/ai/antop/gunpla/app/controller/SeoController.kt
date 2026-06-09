package ai.antop.gunpla.app.controller

import ai.antop.gunpla.app.service.ManualAssemblyService
import ai.antop.gunpla.config.AppProperties
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
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
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Date

/** robots.txt·sitemap.xml·RSS·Atom 피드 등 SEO 관련 엔드포인트를 제공하는 컨트롤러 */
@RestController
class SeoController(
    private val appProperties: AppProperties,
    private val manualAssemblyService: ManualAssemblyService,
) {
    private val xmlMapper =
        XmlMapper().apply {
            enable(ToXmlGenerator.Feature.WRITE_XML_DECLARATION)
            registerModules(KotlinModule.Builder().build(), JavaTimeModule())
        }

    /** robots.txt 반환. /admin·/login 비허용, AI 크롤러·Googlebot 허용, sitemap·피드 링크 포함 */
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

    /** 공개 메뉴얼의 sitemap.xml 반환 (application/xml) */
    @GetMapping("/sitemap.xml", produces = ["application/xml;charset=UTF-8"])
    fun sitemap(): String {
        val urls =
            manualAssemblyService.getManuals(onlyPublished = true).map {
                Sitemap.SitemapUrl(
                    loc = appProperties.baseUrl + "/" + it.id,
                    lastmod = it.updatedAt.atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime(),
                    changefreq = "daily",
                    priority = 0.5,
                )
            }
        return xmlMapper.writeValueAsString(Sitemap(urls = urls))
    }

    /** RSS 2.0 피드 반환 */
    @GetMapping("/rss.xml", produces = ["application/rss+xml;charset=UTF-8"])
    fun rss(): String = buildFeed("rss_2.0")

    /** Atom 1.0 피드 반환 */
    @GetMapping("/atom.xml", produces = ["application/atom+xml;charset=UTF-8"])
    fun atom(): String = buildFeed("atom_1.0")

    /** feedType에 따라 RSS 또는 Atom 피드 문자열 생성 */
    private fun buildFeed(feedType: String): String {
        val baseUrl = appProperties.baseUrl
        val feed =
            SyndFeedImpl().apply {
                this.feedType = feedType
                title = "건담프라 데칼 메뉴얼"
                link = baseUrl
                description = "건담프라 데칼 메뉴얼 목록"
                entries =
                    manualAssemblyService.getManuals(onlyPublished = true).map { manual ->
                        SyndEntryImpl().apply {
                            title = "[${manual.grade}] ${manual.modelNumber} ${manual.productName}"
                            link = "$baseUrl/${manual.id}"
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

/** sitemap.xml의 urlset 루트 요소 */
@JacksonXmlRootElement(localName = "urlset")
data class Sitemap(
    @field:JacksonXmlProperty(isAttribute = true, localName = "xmlns")
    val xmlns: String = "https://www.sitemaps.org/schemas/sitemap/0.9",
    @field:JacksonXmlProperty(localName = "url")
    @field:JacksonXmlElementWrapper(useWrapping = false)
    val urls: List<SitemapUrl> = emptyList(),
) {
    /** sitemap.xml의 개별 url 요소 */
    data class SitemapUrl(
        @field:JacksonXmlProperty(localName = "loc")
        val loc: String,
        @field:JacksonXmlProperty(localName = "lastmod")
        @field:JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
        val lastmod: OffsetDateTime,
        @field:JacksonXmlProperty(localName = "changefreq")
        val changefreq: String = "daily",
        @field:JacksonXmlProperty(localName = "priority")
        val priority: Double = 0.5,
    )
}
