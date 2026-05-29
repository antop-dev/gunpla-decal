package com.example.gunpladecal.config

import net.menoita.sitemap.config.SitemapProperties
import net.menoita.sitemap.core.SitemapEndpointScanner
import net.menoita.sitemap.core.SitemapHolder
import net.menoita.sitemap.core.SitemapLocaleResolver
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

/**
 * When used with an actuator, two [RequestMappingHandlerMapping]s are entered, causing the following error.
 * Inject directly.
 *
 * Parameter 0 of method sitemapEndpointScanner in net.menoita.sitemap.config.SitemapAutoConfiguration required a single bean, but 2 were found:
 * 	- requestMappingHandlerMapping: defined by method 'requestMappingHandlerMapping' in class path resource [org/springframework/boot/autoconfigure/web/servlet/WebMvcAutoConfiguration$EnableWebMvcConfiguration.class]
 * 	- controllerEndpointHandlerMapping: defined by method 'controllerEndpointHandlerMapping' in class path resource [org/springframework/boot/actuate/autoconfigure/endpoint/web/servlet/WebMvcEndpointManagementContextConfiguration.class]
 */
@Configuration
class SitemapConfig {
    @Bean
    fun sitemapEndpointScanner(
        @Qualifier("requestMappingHandlerMapping") handlerMapping: RequestMappingHandlerMapping,
        sitemapHolder: SitemapHolder,
        properties: SitemapProperties,
        localeResolver: SitemapLocaleResolver,
    ): SitemapEndpointScanner = SitemapEndpointScanner(handlerMapping, sitemapHolder, properties, localeResolver)
}
