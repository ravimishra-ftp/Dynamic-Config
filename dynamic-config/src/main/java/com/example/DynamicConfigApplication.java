package com.example;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@Slf4j
@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(value = {"com.example"})
public class DynamicConfigApplication {

  public static void main(String... args) {
    log.info("Booting DynamicConfigApplication...");
    SpringApplication.run(DynamicConfigApplication.class, args);
  }
}
