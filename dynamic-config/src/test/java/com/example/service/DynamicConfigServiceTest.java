package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.config.ConfigProps;
import com.netflix.appinfo.InstanceInfo;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.netflix.eureka.EurekaDiscoveryClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RunWith(MockitoJUnitRunner.class)
public class DynamicConfigServiceTest {

  @Mock
  private ApplicationContext context;

  @Mock
  private ConfigurableEnvironment configurableEnvironment;

  @Mock
  private ConfigProps configProps;

  @InjectMocks
  private DynamicConfigService service;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  //@Test
  @Ignore
  public void testExecuteOnInstances() {
    List<ServiceInstance> instances = new ArrayList<>();
    InstanceInfo instanceInfo = mock(InstanceInfo.class);
    RestTemplate restTemplate = mock(RestTemplate.class);
    service = Mockito.spy(DynamicConfigService.class);
    instances.add(new EurekaDiscoveryClient.EurekaServiceInstance(instanceInfo));
    when(service.getRestTemplate()).thenReturn(restTemplate);
    ReflectionTestUtils.setField(service, "configProps", configProps);
    when(configProps.getMaxTotalConnections()).thenReturn(2);
    when(restTemplate.exchange(any(), any(), any(), any(ParameterizedTypeReference.class))).thenReturn(new ResponseEntity<Map<String, Object>>(HttpStatus.OK));
    Map<String, Object> config = service.executeOnInstances("ops","/v1/config/order-service-v1",
            HttpMethod.GET,null, instances, "getConfig");
    assertThat(config).isNotNull();
  }

  //@Test(expected = ApplicationException.class)
  @Ignore
  public void testExecuteOnInstancesException() {
    List<ServiceInstance> instances = new ArrayList<>();
    InstanceInfo instanceInfo = mock(InstanceInfo.class);
    service = Mockito.spy(DynamicConfigService.class);
    instances.add(new EurekaDiscoveryClient.EurekaServiceInstance(instanceInfo));
    ReflectionTestUtils.setField(configProps, "maxTotalConnections", 2);
    when(service.getRestTemplate()).thenThrow(new RuntimeException("Oops! Failed to update config."));
    Map<String, Object> config = service.executeOnInstances("ops","/v1/config/order-service-v1", HttpMethod.GET,null, instances, "getConfig");
  }

  @Test
  public void testGetConfigurationForOps() {
    Map<String, Object> configBeans = new HashMap<>();
    ConfigProps configProp = new ConfigProps();
    configBeans.put("", configProp);
    when(context.getBean("configProps")).thenReturn(configProp);
    when(context.getBeansWithAnnotation(Configuration.class)).thenReturn(configBeans);
    when(context.getBeanDefinitionNames()).thenReturn(new String[]{"configProps"});
    when(configProps.getBasePackage()).thenReturn("com.jcpenney.dcp");
    Map<String, Object> configuration = service.getConfig("ops", false);
    assertThat(configuration).isNotNull();
    assertEquals(1, configuration.size());
  }

  @Test
  public void testGetConfigurationForDcp() {
    Map<String, Object> configBeans = new HashMap<>();
    ConfigProps config = new ConfigProps();
    configBeans.put("", config);
    when(context.getBeanDefinitionNames()).thenReturn(new String[]{"dynamicConfigService"});
    when(context.getBean("dynamicConfigService")).thenReturn(new DynamicConfigService());
    when(context.getBean("configProps")).thenReturn(config);
    when(context.getBeansWithAnnotation(Configuration.class)).thenReturn(configBeans);
    when(configProps.getBasePackage()).thenReturn("com.jcpenney.dcp");
    Map<String, Object> configuration = service.getConfig("dcp", false);
    assertThat(configuration).isNotNull();
    assertEquals(5, configuration.size());
  }

  @Test
  public void testUpdateConfiguration() {
    MutablePropertySources mutablePropertySources = mock(MutablePropertySources.class);
    when(context.getBeanDefinitionNames()).thenReturn(new String[]{"dynamicConfigService", "configProps"});
    when(context.getBean("dynamicConfigService")).thenReturn(new DynamicConfigService());
    when(context.getBean("configProps")).thenReturn(new ConfigProps());
    when(configurableEnvironment.getPropertySources()).thenReturn(mutablePropertySources);
    when(configProps.getBasePackage()).thenReturn("com.jcpenney");
    boolean result = service.updateConfig("dcp", "ConfigProps.connectionTimeout", "2");
    assertThat(result).isTrue();
  }

  @Test(expected = RuntimeException.class)
  public void testUpdateConfigurationIfInvalidKey() {
    ReflectionTestUtils.setField(configProps, "basePackage", "com.jcpenney.dcp");
    when(context.getBeanDefinitionNames()).thenReturn(new String[]{"dynamicConfigService", "commonFeatureConfiguration"});
    when(context.getBean("dynamicConfigService")).thenReturn(new DynamicConfigService());
    when(context.getBean("commonFeatureConfiguration")).thenReturn(new ConfigProps());
    when(configurableEnvironment.getPropertySources()).thenReturn(new MutablePropertySources());
    service.updateConfig("ops", "xyz", "abc");
  }

  @Test
  public void testSetLogLevel() {
    boolean result = service.updateLog("com.jcpenney.dcp", "DEBUG");
    assertThat(result).isTrue();
  }

  @Test(expected = RuntimeException.class)
  public void testSetLogLevelIfInvalidLevel() {
    service.updateLog("com.jcpenney.dcp", "XYZ");
  }

  @Test(expected = RuntimeException.class)
  public void testSetLogLevelIfInvalidPackage() {
    service.updateLog("abc", "INFO");
  }

}