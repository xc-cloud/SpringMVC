package com.small.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)//作用在变量上面
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SmallAutowired {
	String value() default "";
}
