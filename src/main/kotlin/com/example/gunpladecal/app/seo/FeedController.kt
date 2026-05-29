package com.example.gunpladecal.app.seo

import com.example.gunpladecal.app.repository.ManualRepository
import com.example.gunpladecal.app.util.Base62
import com.rometools.rome.feed.synd.SyndContentImpl
import com.rometools.rome.feed.synd.SyndEntryImpl
import com.rometools.rome.feed.synd.SyndFeedImpl
import com.rometools.rome.io.SyndFeedOutput
import net.menoita.sitemap.config.SitemapProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.ZoneId
import java.util.Date

@RestController
class FeedController(
    private val sitemapProperties: SitemapProperties,
    private val manualRepository: ManualRepository,
) {
    @GetMapping("/rss.xml", produces = ["application/rss+xml;charset=UTF-8"])
    fun rss(): String = buildFeed("rss_2.0")

    @GetMapping("/atom.xml", produces = ["application/atom+xml;charset=UTF-8"])
    fun atom(): String = buildFeed("atom_1.0")

    private fun buildFeed(feedType: String): String {
        val baseUrl = sitemapProperties.baseUrl
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
