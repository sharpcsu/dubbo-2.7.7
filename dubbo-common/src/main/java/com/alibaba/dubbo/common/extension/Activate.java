package com.alibaba.dubbo.common.extension;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * See @org.apache.dubbo.common.extension.Activate
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Deprecated
public @interface Activate {

    String[] group() default {};

    String[] value() default {};

    @Deprecated
    String[] before() default {};

    @Deprecated
    String[] after() default {};

    int order() default 0;
}