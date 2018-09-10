package com.example.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.config.ConfigProps;
import com.example.service.DynamicConfigService;
import com.netflix.appinfo.InstanceInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.netflix.eureka.EurekaDiscoveryClient.EurekaServiceInstance;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.http.HttpServletRequest;

@RunWith(MockitoJUnitRunner.class)
public class DynamicConfigControllerTest {

  @Mock
  private DiscoveryClient discoveryClient;

  @Mock
  private DynamicConfigService configService;

  @Mock
  private ConfigProps configProps;

  @InjectMocks
  private DynamicConfigController controller;

  Map<String, String> serviceIdAndContextPath;
  List<ServiceInstance> instances;
  InstanceInfo instanceInfo;
  HttpServletRequest request;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    serviceIdAndContextPath = new HashMap<>();
    serviceIdAndContextPath.put("best-coupon-service-v1", "/");
    serviceIdAndContextPath.put("order-service-v1", "order-api");
    serviceIdAndContextPath.put("bag-service-v1", "bag-rs");
    instanceInfo = mock(InstanceInfo.class);
    request = mock(HttpServletRequest.class);
    instances = new ArrayList<>();
    instances.add(new EurekaServiceInstance(instanceInfo));
    when(configProps.getServiceIdAndContextPath()).thenReturn(serviceIdAndContextPath);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
  }

  @Test
  public void testGetConfigForMasterNodeOrderService() {
    when(instanceInfo.getIPAddr()).thenReturn("157.49.249.116");
    when(discoveryClient.getInstances(any())).thenReturn(instances);
    when(configService.executeOnInstances("ops","order-api/config/order-service-v1", HttpMethod.GET, null, instances,
            "getConfig")).thenReturn(new TreeMap<>());
    ResponseEntity<Map<String, Object>> config = controller.getConfig(request, "order-service-v1", false, false);
    assertThat(config).isNotNull();
    verify(configService, times(1)).executeOnInstances("ops","order-api/config/order-service-v1?includeYml=false", HttpMethod.GET, null, instances, "getConfig");
  }

  @Test
  public void testGetConfigForMasterNodeBestCoupon() {
    when(instanceInfo.getIPAddr()).thenReturn("157.49.249.116");
    when(discoveryClient.getInstances(any())).thenReturn(instances);
    when(configService.executeOnInstances("ops","//config/best-coupon-service-v1", HttpMethod.GET, null, instances, "getConfig")).thenReturn(new TreeMap<>());
    ResponseEntity<Map<String, Object>> config = controller.getConfig(request, "best-coupon-service-v1", false, false);
    assertThat(config).isNotNull();
    verify(configService, times(1)).executeOnInstances("ops","//config/best-coupon-service-v1?includeYml=false", HttpMethod.GET, null, instances, "getConfig");
  }

  @Test
  public void testGetConfigForMasterNodeForOtherThanOrderAndBestCouponService() {
    when(instanceInfo.getIPAddr()).thenReturn("157.49.249.116");
    when(discoveryClient.getInstances(any())).thenReturn(instances);
    when(configService.executeOnInstances("ops","bag-rs/config/bag-service-v1", HttpMethod.GET, null, instances,"getConfig")).thenReturn(new TreeMap<>());
    ResponseEntity<Map<String, Object>> config = controller.getConfig(request, "bag-service-v1", false, false);
    assertThat(config).isNotNull();
    verify(configService, times(1)).executeOnInstances("ops","bag-rs/config/bag-service-v1?includeYml=false", HttpMethod.GET, null, instances, "getConfig");
  }

  @Test
  public void testGetConfigForSlaveNode() {
    when(instanceInfo.getIPAddr()).thenReturn("157.49.249.116");
    when(discoveryClient.getInstances(any())).thenReturn(instances);
    when(configService.getConfig("ops", false)).thenReturn(new TreeMap<>());
    when(configService.getConfig("ops", false)).thenReturn(new TreeMap<>());
    ResponseEntity<Map<String, Object>> config = controller.getConfig(request, "order-service-v1", false, true);
    assertThat(config).isNotNull();
    verify(configService, times(1)).getConfig("ops", false);
  }

  @Test
  public void testUpdateConfigForMasterNode() {
    when(instanceInfo.getIPAddr()).thenReturn("157.49.249.116");
    when(discoveryClient.getInstances(any())).thenReturn(instances);
    when(configService.executeOnInstances("ops","order-api/config/order-service-v1", HttpMethod.PUT, "abc:xyz", instances, "updateConfig")).thenReturn(new TreeMap<>());
    ResponseEntity response = controller.updateConfig(request, "abc:xyz","order-service-v1", false);
    assertThat(response).isNotNull();
    assertThat(response.getStatusCodeValue()).isEqualTo(201);
    verify(configService, times(1)).executeOnInstances("ops","order-api/config/order-service-v1", HttpMethod.PUT, "abc:xyz", instances, "updateConfig");
  }

  @Test
  public void testUpdateConfigForSlaveNode() {
    when(instanceInfo.getIPAddr()).thenReturn("157.49.249.116");
    when(discoveryClient.getInstances(any())).thenReturn(instances);
    when(configService.updateConfig("ops", "abc", "xyz")).thenReturn(true);
    ResponseEntity response = controller.updateConfig(request, "abc:xyz","order-service-v1", true);
    assertThat(response).isNotNull();
    assertThat(response.getStatusCodeValue()).isEqualTo(201);
    verify(configService, times(1)).updateConfig("ops", "abc", "xyz");
  }

  @Test
  public void testUpdateLogConfigForMasterNode() {
    when(instanceInfo.getIPAddr()).thenReturn("157.49.249.116");
    when(discoveryClient.getInstances(any())).thenReturn(instances);
    when(configService.executeOnInstances("ops","order-api/config/log/order-service-v1", HttpMethod.PUT, "abc:xyz", instances,"updateLog")).thenReturn(new TreeMap<>());
    ResponseEntity response = controller.updateLog(request, "abc:xyz","order-service-v1", "157.49.249.116", false);
    assertThat(response).isNotNull();
    assertThat(response.getStatusCodeValue()).isEqualTo(201);
    verify(configService, times(1)).executeOnInstances("ops","order-api/config/log/order-service-v1", HttpMethod.PUT, "abc:xyz", instances, "updateLog");
  }

  @Test
  public void testUpdateLogConfigForSlaveNode() {
    when(instanceInfo.getIPAddr()).thenReturn("157.49.249.116");
    when(discoveryClient.getInstances(any())).thenReturn(instances);
    when(configService.updateLog("abc", "xyz")).thenReturn(true);
    ResponseEntity response = controller.updateLog(request, "abc:xyz","order-service-v1", "157.49.249.116", true);
    assertThat(response).isNotNull();
    assertThat(response.getStatusCodeValue()).isEqualTo(201);
    verify(configService, times(1)).updateLog("abc", "xyz");
  }

}