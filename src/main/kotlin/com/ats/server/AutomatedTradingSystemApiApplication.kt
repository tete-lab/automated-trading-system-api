package com.ats.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@EnableJpaAuditing //날짜자동입ㄺ
@SpringBootApplication
class AutomatedTradingSystemApiApplication

fun main(args: Array<String>) {
	runApplication<AutomatedTradingSystemApiApplication>(*args)
}
