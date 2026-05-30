package com.example.gunpladecal.app.seo

import com.example.gunpladecal.app.event.ManualPublishedEvent
import com.example.gunpladecal.app.repository.ManualRepository
import com.example.gunpladecal.app.util.Base62
import net.menoita.sitemap.config.SitemapProperties
import net.menoita.sitemap.core.SitemapHolder
import net.menoita.sitemap.model.ChangeFrequency
import net.menoita.sitemap.model.SitemapUrl
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class SitemapPopulator(
    private val sitemapProperties: SitemapProperties,
    private val manualRepository: ManualRepository,
    private val sitemapHolder: SitemapHolder,
) : CommandLineRunner {
    override fun run(vararg args: String) {
        manualRepository.findAllByPublishedTrueOrderByIdDesc().forEach { manual ->
            sitemapHolder.add(
                SitemapUrl
                    // DB ID 직접 노출 방지를 위해 * 23 으로 난독화. ManualController 에서 / 23 으로 역산
                    .builder("${sitemapProperties.baseUrl}/${Base62.encode(manual.id * 23)}")
                    .priority(0.5)
                    .changefreq(ChangeFrequency.ALWAYS)
                    .lastmod(manual.createdAt)
                    .build(),
            )
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onPublishedChanged(event: ManualPublishedEvent) {
        val loc = "${sitemapProperties.baseUrl}/${Base62.encode(event.id * 23)}"
        if (event.published) {
            sitemapHolder.add(
                SitemapUrl
                    .builder(loc)
                    .priority(0.5)
                    .changefreq(ChangeFrequency.ALWAYS)
                    .lastmod(event.createdAt)
                    .build(),
            )
        } else {
            sitemapHolder.remove(loc)
        }
    }
}
