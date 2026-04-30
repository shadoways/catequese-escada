package com.catequese.catequeseapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.runApplication

@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
class CatequeseapiApplication

fun main(args: Array<String>) {
	runApplication<CatequeseapiApplication>(*args)
}

