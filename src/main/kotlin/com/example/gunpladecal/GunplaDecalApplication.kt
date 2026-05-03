package com.example.gunpladecal

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@EnableAsync
@ConfigurationPropertiesScan
class GunplaDecalApplication

fun main(args: Array<String>) {
    runApplication<GunplaDecalApplication>(*args)
}
