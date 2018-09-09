/*
 * Copyright (c) 2018 JCPenney Co. All rights reserved.
 */

package com.jcpenney.dcp.aspect;

import com.jcpenney.dcp.config.ConfigProps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.lang.reflect.Method;

import javax.servlet.http.HttpServletRequest;

@Slf4j
@Aspect
@Component
public class DynamicConfigAspect {

  private static final String REGEX = "get|is";

  @Autowired
  private ConfigProps configProps;

  @Around("@within(EnableDynamicConfig) && (execution(* get*(..)) || execution(* is*(..)))")
  public Object dynamicConfigChange(ProceedingJoinPoint joinPoint) throws Throwable {
    try {
      if (configProps.isRequestScopeEnabled() && RequestContextHolder.getRequestAttributes() != null) {
        HttpServletRequest request =
            ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String prop = joinPoint.getSignature().getName().replaceFirst(REGEX, "").toLowerCase();
        if (request != null && request.getHeader(prop) != null) {
          String propValue = request.getHeader(prop);
          Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
          PropertyEditor editor = PropertyEditorManager.findEditor(method.getReturnType());
          editor.setAsText(propValue);
          log.info("op=dynamicConfigChange, status=OK, desc=updating config for: {} to: {}", prop, propValue);
          return editor.getValue();
        } else {
          return joinPoint.proceed();
        }
      }
    } catch (Throwable ex) {
      log.warn("op=dynamicConfigChange, status=KO, desc=config update failed, error={}", ExceptionUtils.getMessage(ex));
    }
    return joinPoint.proceed();
  }

}
