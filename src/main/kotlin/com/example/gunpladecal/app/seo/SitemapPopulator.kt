package com.example.gunpladecal.app.seo

import com.example.gunpladecal.app.repository.ManualRepository
import com.example.gunpladecal.app.util.Base62
import net.menoita.sitemap.config.SitemapProperties
import net.menoita.sitemap.core.SitemapHolder
import net.menoita.sitemap.model.ChangeFrequency
import net.menoita.sitemap.model.SitemapUrl
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

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
}
