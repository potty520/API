package com.example.apisystem.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@MapperScan("com.example.apisystem.repository")
public class AppConfig {
}
