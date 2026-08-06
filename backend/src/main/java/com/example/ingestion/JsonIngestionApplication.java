package com.example.ingestion;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@MapperScan("com.example.ingestion.mapper")
@SpringBootApplication
public class JsonIngestionApplication {
    public static void main(String[] args) {
        SpringApplication.run(JsonIngestionApplication.class, args);
    }
}
