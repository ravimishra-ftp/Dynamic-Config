package com.example.controller;


import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import com.example.service.DynamicConfigService;
import com.example.config.ConfigProps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.netflix.eureka.EurekaDiscoveryClient.EurekaServiceInstance;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

@Slf4j
@RestController
@RequestMapping(path = "/v1/config")
@ConditionalOnProperty(
    name = {"ribbon.eureka.enabled", "dynamicConfig.apiEnabled"}, havingValue = "true", matchIfMissing = false)
public class DynamicConfigController {

  private static final String ANY_IP_ADDRESS = "ANY";
  private static final String LOG_OK_PATTERN = "op={}, status=OK, Client={}, ipAddress={}, serviceId={}, desc={}";

  @Autowired
  private ConfigProps configProps;

  @Autowired
  private DiscoveryClient discoveryClient;

  @Autowired
  private DynamicConfigService configService;

  @GetMapping(path = "/{serviceId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, Object>> getConfig(HttpServletRequest request,
      @PathVariable("serviceId") String id,
      @RequestParam(value = "includeYml", required = false) boolean includeYml,
      @RequestHeader(value = "isSlaveInstance", required = false) boolean isSlave) {
    String op = "getConfig";
    Map<String, Object> response;
    String clientKey = getClientKey(request);

    if (isSlave) {
      response = configService.getConfig(clientKey, includeYml);
    } else {
      log.info(LOG_OK_PATTERN, op, clientKey, getClientIp(request), id, "Getting all configs");
      List<ServiceInstance> ultimateInstances = getUltimateInstances(id, ANY_IP_ADDRESS);
      String url = getUrl(linkTo(methodOn(this.getClass()).getConfig(request, id, includeYml, isSlave)), id);
      response = configService.executeOnInstances(clientKey, url, HttpMethod.GET, null, ultimateInstances, op);
    }

    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @PutMapping(path = "/{serviceId}",
      consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity updateConfig(HttpServletRequest request,
      @RequestBody String prop,
      @PathVariable("serviceId") String id,
      @RequestHeader(value = "isSlaveInstance", required = false) boolean isSlave) {
    String op = "updateConfig";
    Map.Entry<String, String> entry = extractKeyAndValue(prop).entrySet().iterator().next();
    String clientKey = getClientKey(request);

    if (isSlave) {
      configService.updateConfig(clientKey, entry.getKey(), entry.getValue());
    } else {
      log.info(LOG_OK_PATTERN, op, clientKey, getClientIp(request), id, prop);
      List<ServiceInstance> ultimateInstances = getUltimateInstances(id, null);
      String url = getUrl(linkTo(methodOn(this.getClass()).updateConfig(request, prop, id, isSlave)), id);
      configService.executeOnInstances(clientKey, url, HttpMethod.PUT, prop, ultimateInstances, op);
    }

    return new ResponseEntity(HttpStatus.CREATED);
  }

  @PutMapping(path = {"/log/{serviceId}", "/log/{serviceId}/{ipAddress:.+}"},
      consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity updateLog(HttpServletRequest request,
      @RequestBody String prop,
      @PathVariable("serviceId") String id,
      @PathVariable(value = "ipAddress", required = false) String ip,
      @RequestHeader(value = "isSlaveInstance", required = false) boolean isSlave) {
    String op = "updateLog";
    Map.Entry<String, String> entry = extractKeyAndValue(prop).entrySet().iterator().next();
    String clientKey = getClientKey(request);

    if (isSlave) {
      configService.updateLog(entry.getKey(), entry.getValue());
    } else {
      log.info(LOG_OK_PATTERN, op, clientKey, getClientIp(request), id, prop);
      List<ServiceInstance> ultimateInstances = getUltimateInstances(id, ip);
      String url = getUrl(linkTo(methodOn(this.getClass()).updateLog(request, prop, id, ip, isSlave)), id);
      configService.executeOnInstances(clientKey, url, HttpMethod.PUT, prop, ultimateInstances, op);
    }

    return new ResponseEntity(HttpStatus.CREATED);
  }

  private Map<String, String> extractKeyAndValue(String prop) {
    Map<String, String> keyValue = new HashMap<>();
    if (!StringUtils.isEmpty(prop)) {
      String propKey = StringUtils.substringBefore(prop, ":");
      String propValue = StringUtils.substringAfter(prop, ":");
      if (!StringUtils.isEmpty(propKey) && !StringUtils.isEmpty(propValue)) {
        keyValue.put(propKey.trim(), propValue.trim());
      } else {
        throw new RuntimeException("Oops! Either key or value not available."); //NOPMD
      }
    } else {
      throw new RuntimeException("Oops! Property to update is not available."); //NOPMD
    }
    return keyValue;
  }

  private List<ServiceInstance> getUltimateInstances(String serviceId, String ipAddress) {
    List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
    if (CollectionUtils.isEmpty(instances)) {
      throw new RuntimeException("Oops! Please enter correct service id."); //NOPMD
    }
    if (!StringUtils.isEmpty(ipAddress)) {
      ServiceInstance serviceInstance = instances.stream()
          .filter(instance -> ((EurekaServiceInstance) instance).getInstanceInfo().getIPAddr().equals(ipAddress))
          .findFirst().orElseGet(() -> {
            if (ANY_IP_ADDRESS.equals(ipAddress)) {
              return instances.iterator().next();
            } else {
              throw new RuntimeException("Oops! IP not found for the service."); //NOPMD
            }
          });
      return Collections.singletonList(serviceInstance);
    }
    return instances;
  }

  private String getClientKey(HttpServletRequest request) {
    String header = request.getHeader("X-Client-AppId");
    return header != null ? StringUtils.substringBefore(header, ":") : "ops";
  }

  private String getUrl(ControllerLinkBuilder linkBuilder, String id) {
    Map<String, String> serviceIdAndContextPath = configProps.getServiceIdAndContextPath();
    if (!CollectionUtils.isEmpty(serviceIdAndContextPath)) {
      URI uri = linkBuilder.toUri();
      String url = uri.getPath().replaceFirst("/[^/]*", serviceIdAndContextPath.get(id));
      if (uri.getQuery() != null) {
        url = StringUtils.join(url, "?", uri.getQuery());
      }
      return url;
    } else {
      throw new RuntimeException("Oops! Failed to load context path for service ids."); //NOPMD
    }
  }

  private static String getClientIp(HttpServletRequest request) {
    String ip = null;
    if (request != null) {
      ip = request.getHeader("X-FORWARDED-FOR");
      if (StringUtils.isEmpty(ip)) {
        ip = request.getRemoteAddr();
      }
    }
    return ip;
  }

}
