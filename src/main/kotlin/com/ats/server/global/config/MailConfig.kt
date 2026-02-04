package com.ats.server.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import java.util.*


@Configuration
class MailConfig {
    @Bean
    fun javaMailService(): JavaMailSender {
        val javaMailSender = JavaMailSenderImpl()

        javaMailSender.setHost("smtp.gmail.com")
        javaMailSender.setUsername("ktgstar@gmail.com")
        javaMailSender.setPassword("uxadshrvxgoahkcu")

        javaMailSender.setPort(465)

        javaMailSender.setJavaMailProperties(this.mailProperties)

        return javaMailSender
    }

    private val mailProperties: Properties
        get() {
            val properties = Properties()
            properties.setProperty("mail.transport.protocol", "smtp")
            properties.setProperty("mail.smtp.auth", "true")
            properties.setProperty("mail.smtp.starttls.enable", "true")
            properties.setProperty("mail.debug", "true")
            properties.setProperty("mail.smtp.ssl.trust", "smtp.gmail.com")
            properties.setProperty("mail.smtp.ssl.enable", "true")
            return properties
        }
}