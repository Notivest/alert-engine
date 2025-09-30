package com.notivest.alertengine

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class AlertEngineApplication

fun main(args: Array<String>) {
	runApplication<AlertEngineApplication>(*args)
}
