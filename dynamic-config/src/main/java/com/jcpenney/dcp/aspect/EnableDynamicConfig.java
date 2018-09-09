/*
 * Copyright (c) 2017 JCPenney Co. All rights reserved.
 */

package com.jcpenney.dcp.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface EnableDynamicConfig {
}
