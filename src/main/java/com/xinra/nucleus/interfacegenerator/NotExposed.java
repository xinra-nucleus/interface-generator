package com.xinra.nucleus.interfacegenerator;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotated methods will not be included in interfaces generated
 * using {@link GenerateInterface}.
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface NotExposed {}
