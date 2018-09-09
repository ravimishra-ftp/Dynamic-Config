package com.jcpenney.dcp.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.jcpenney.dcp.config.ConfigProps;
import com.jcpenney.dcp.editor.BigDecimalEditor;
import com.jcpenney.dcp.internal.client.InternalApiEncryption;
import com.netflix.appinfo.InstanceInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.logging.LogLevel;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.netflix.eureka.EurekaDiscoveryClient.EurekaServiceInstance;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.ClassUtils;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@ConditionalOnProperty(
    name = {"ribbon.eureka.enabled", "dynamicConfig.apiEnabled"}, havingValue = "true", matchIfMissing = false)
public class DynamicConfigService {

  private static final String CGLIB_CLASS_SEPARATOR = "$$";
  private static final String CGLIB_RENAMED_FIELD_PREFIX = "CGLIB$";

  @Autowired
  private ConfigProps configProps;

  @Autowired
  private ApplicationContext applicationContext;

  @Autowired
  private ConfigurableEnvironment configurableEnvironment;

  static {
    //Registering custom type to convert from String to target type
    PropertyEditorManager.registerEditor(BigDecimal.class, BigDecimalEditor.class);
  }

  public Map<String, Object> executeOnInstances(
          String clientKey, String url, HttpMethod method, String body, List<ServiceInstance> instances, String op) {
    Map<String, Object> responseList = new TreeMap<>();
    ResponseEntity<Map<String, Object>> response;
    RestTemplate restTemplate = getRestTemplate();
    String appName = null;
    String httpUrl = null;

    for (ServiceInstance instance : instances) {
      InstanceInfo instanceInfo = ((EurekaServiceInstance) instance).getInstanceInfo();
      try {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("isSlaveInstance", "true");
        headers.set("X-Client-AppId", clientKey + ":" + InternalApiEncryption.hashWithSHA256(clientKey));
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        httpUrl = instanceInfo.getHomePageUrl() + url;
        appName = instanceInfo.getAppName();
        response = restTemplate.exchange(httpUrl, method, entity,
            new ParameterizedTypeReference<Map<String, Object>>() {
            });
        log.info("op={}, status=OK, appName={}, url={}", op, appName, httpUrl);
        if (response != null && response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
          responseList.putAll(response.getBody());
        }
      } catch (Exception ex) {
        log.error("op={}, status=KO, appName={}, url={}, Error={}", op, appName, httpUrl, ex);
        throw new RuntimeException("Instance Execution: " + ex.getMessage()); //NOPMD
      }
    }
    return responseList;
  }

  public Map<String, Object> getConfig(String clientKey, boolean includeYml) {
    Map<String, Object> configs = new TreeMap<>();
    getConfigMap(clientKey).forEach((key, field) -> {
      try {
        Object ultimateTarget = getUltimateTargetBeanOfField(field);
        boolean accessibility = field.isAccessible();
        field.setAccessible(true);
        Object value = field.get(ultimateTarget);
        field.setAccessible(accessibility);
        configs.put(key, value);
      } catch (Exception ex) {
        log.warn("op=getConfig, status=KO, desc=Failed for configs field={}, error={}", field.getName(), ex);
      }
    });
    if (includeYml) {
      configs.put("ZZZ - APPLICATION YML PROPERTIES - ZZZ" ,getYmlConfig());
    }
    return configs;
  }

  private Map<String, Map<String, Object>> getYmlConfig() {
    MutablePropertySources propertySources = configurableEnvironment.getPropertySources();
    Map<String, Map<String, Object>> applicationConfig = new HashMap<>();
    propertySources.forEach(propertySource -> {
      if (propertySource.getName().contains("applicationConfig") && propertySource instanceof MapPropertySource) {
        applicationConfig.put(propertySource.getName(), ((MapPropertySource) propertySource).getSource());
      }
    });
    return applicationConfig;
  }

  public boolean updateConfig(String clientKey, String propKey, String propValue) {
    Map<String, Field> configs = getConfigMap(clientKey);
    boolean isUpdated = false;
    if (configs.containsKey(propKey)) {
      try {
        Field field = configs.get(propKey);
        boolean accessibility = field.isAccessible();
        PropertyEditor editor = PropertyEditorManager.findEditor(field.getType());
        editor.setAsText(propValue);
        Object ultimateTarget = getUltimateTargetBeanOfField(field);
        field.setAccessible(true);
        field.set(ultimateTarget, editor.getValue());
        field.setAccessible(accessibility);
        isUpdated = true;
      } catch (Exception ex) {
        log.error("op=updateConfig, status=KO, desc=Config update failed.", ex);
        throw new RuntimeException("Oops! Failed to update config."); //NOPMD
      }
    } else {
      for (Map.Entry<String, Map<String, Object>> entry : getYmlConfig().entrySet()) {
        if (entry.getValue().containsKey(propKey)) {
          entry.getValue().put(propKey, propValue);
          MutablePropertySources propertySources = configurableEnvironment.getPropertySources();
          propertySources.replace(entry.getKey(), new MapPropertySource(entry.getKey(), entry.getValue()));
          isUpdated = true;
        }
      }
    }
    if (!isUpdated) {
      throw new RuntimeException("Oops! Please enter correct key."); //NOPMD
    }
    return true;
  }

  public boolean updateLog(String packageName, String logLevel) {
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    if (loggerContext.exists(packageName) != null && EnumUtils.isValidEnum(LogLevel.class, logLevel)) {
      try {
        Logger logger = loggerContext.getLogger(packageName);
        Level level = Level.toLevel(logLevel, logger.getLevel());
        logger.setLevel(level);
      } catch (Exception ex) {
        log.error("op=updateLog, status=KO, desc=Log update failed.", ex);
        throw new RuntimeException("Oops! Failed to update log level."); //NOPMD
      }
    } else {
      throw new RuntimeException("Oops! Incorrect log level or package."); //NOPMD
    }
    return true;
  }

  private Object getUltimateTargetBeanOfField(Field field) {
    Object bean;
    try {
      bean = applicationContext.getBean(StringUtils.uncapitalize(field.getDeclaringClass().getSimpleName()));
    } catch (Exception ex) {
      bean = applicationContext.getBean(field.getDeclaringClass());
    }
    bean = getUltimateTargetBean(bean);
    return bean;
  }

  private Object getUltimateTargetBean(Object bean) {
    try {
      if (AopUtils.isAopProxy(bean) && (bean instanceof Advised)) {
        return getUltimateTargetBean(((Advised) bean).getTargetSource().getTarget());
      }
    } catch (Exception ex) {
      log.warn("op=getUltimateTargetBean, status=KO, desc=Failed to get target bean, error={}", ex);
    }
    return bean;
  }

  private Map<String, Field> getUltimateTargetField(List<Field> fields, boolean isDcpClient) {
    return Optional.ofNullable(fields)
        .orElseGet(Collections::emptyList).stream()
        .filter(field -> field.getDeclaringClass().getCanonicalName() != null
            && field.getDeclaringClass().getCanonicalName().startsWith(configProps.getBasePackage())
            && !field.getName().startsWith(CGLIB_CLASS_SEPARATOR)
            && !field.getName().startsWith(CGLIB_RENAMED_FIELD_PREFIX)
            && !Modifier.isFinal(field.getModifiers())
            && isValidField(field, isDcpClient))
        .collect(Collectors.toMap(field -> field.getDeclaringClass().getSimpleName() + "." + field.getName(),
            field -> field, (oldValue, newValue) -> oldValue));
  }

  private boolean isValidField(Field field, boolean isDcpClient) {
    //Filtering only Boolean or boolean fields for ops client
    return isDcpClient ? BeanUtils.isSimpleValueType(field.getType())
            : ClassUtils.isAssignable(Boolean.class, field.getType());
  }

  private Map<String, Field> getConfigMap(String clientKey) {
    boolean isDcpClient = !StringUtils.isEmpty(clientKey) && "dcp".equals(clientKey);
    Map<String, Field> configs = new HashMap<>();
    for (String beanName : applicationContext.getBeanDefinitionNames()) {
      Object bean = applicationContext.getBean(beanName);
      if (bean != null) {
        configs.putAll(getUltimateTargetField(
                FieldUtils.getFieldsListWithAnnotation(bean.getClass(), Value.class), isDcpClient));
      }
    }
    Map<String, Object> beansWithAnnotation = applicationContext.getBeansWithAnnotation(Configuration.class);
    beansWithAnnotation.forEach((key, value) ->
            configs.putAll(getUltimateTargetField(FieldUtils.getAllFieldsList(value.getClass()), isDcpClient)));
    return configs;
  }

  public RestTemplate getRestTemplate() {
    RestTemplate pingRestTemplate = new RestTemplate(
        clientHttpRequestFactory(new HttpComponentsClientHttpRequestFactory(httpClient())));
    pingRestTemplate.setErrorHandler(new DefaultResponseErrorHandler());
    return pingRestTemplate;
  }

  private ClientHttpRequestFactory clientHttpRequestFactory(HttpComponentsClientHttpRequestFactory httpClientFactory) {
    httpClientFactory.setReadTimeout(configProps.getReadTimeout());
    httpClientFactory.setConnectTimeout(configProps.getConnectionTimeout());
    return httpClientFactory;
  }

  private CloseableHttpClient httpClient() {
    PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
    connectionManager.setMaxTotal(configProps.getMaxTotalConnections());
    connectionManager.closeIdleConnections(30, TimeUnit.SECONDS);
    return HttpClients.custom().setConnectionManager(connectionManager).build();
  }
}
