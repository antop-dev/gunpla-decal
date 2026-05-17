package com.example.gunpladecal

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class GunplaDecalApplication

fun main(args: Array<String>) {
    runApplication<GunplaDecalApplication>(*args)
}
