package com.example.gunpladecal.app.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import java.time.OffsetDateTime

@JacksonXmlRootElement(localName = "urlset")
data class Sitemap(
    @field:JacksonXmlProperty(isAttribute = true, localName = "xmlns")
    val xmlns: String = "https://www.sitemaps.org/schemas/sitemap/0.9",
    @field:JacksonXmlProperty(localName = "url")
    @field:JacksonXmlElementWrapper(useWrapping = false)
    val urls: List<SitemapUrl> = emptyList(),
) {
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
