package com.example.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Data
@Configuration
@ConfigurationProperties("dynamic-config")
public class ConfigProps {

  private Map<String, String> serviceIdAndContextPath;

  @Value("${dynamic-config.requestScopeEnabled:false}")
  private boolean requestScopeEnabled;
  @Value("${dynamic-config.basePackage:com.jcpenney.dcp}")
  private String basePackage;
  @Value("${dynamic-config.totalConnections:2}")
  private int maxTotalConnections;
  @Value("${dynamic-config.readTimeout:2000}")
  private int readTimeout;
  @Value("${dynamic-config.connectionTimeout:1000}")
  private int connectionTimeout;

}
