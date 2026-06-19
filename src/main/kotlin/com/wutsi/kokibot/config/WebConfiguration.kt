package com.wutsi.kokibot.config

import com.wutsi.kokibot.MultiBootstrap
import com.wutsi.kokibot.servlet.FilesServlet
import org.springframework.boot.web.servlet.ServletRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
open class WebConfiguration : WebMvcConfigurer {

    @Bean
    open fun filesServlet(multi: MultiBootstrap): ServletRegistrationBean<FilesServlet> {
        return ServletRegistrationBean(FilesServlet(multi), "/files/*")
    }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        // Serve static resources from /static
        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
    }

    override fun addViewControllers(registry: ViewControllerRegistry) {
        // Map root to index.html
        registry.addViewController("/").setViewName("forward:/index.html")
    }
}
