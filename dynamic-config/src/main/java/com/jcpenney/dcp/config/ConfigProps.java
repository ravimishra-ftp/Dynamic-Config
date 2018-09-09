/*
 * Copyright (c) 2017 JCPenney Co. All rights reserved.
 */

package com.jcpenney.dcp.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Data
@Configuration
@ConfigurationProperties("dynamicConfig")
public class ConfigProps {

  private Map<String, String> serviceIdAndContextPath;

  @Value("${dynamicConfig.requestScopeEnabled:false}")
  private boolean requestScopeEnabled;
  @Value("${dynamicConfig.basePackage:com.jcpenney.dcp}")
  private String basePackage;
  @Value("${dynamicConfig.totalConnections:2}")
  private int maxTotalConnections;
  @Value("${dynamicConfig.readTimeout:2000}")
  private int readTimeout;
  @Value("${dynamicConfig.connectionTimeout:1000}")
  private int connectionTimeout;

}
