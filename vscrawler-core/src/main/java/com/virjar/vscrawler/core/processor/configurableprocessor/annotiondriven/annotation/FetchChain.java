package com.virjar.vscrawler.core.processor.configurableprocessor.annotiondriven.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by virjar on 2017/12/10.<br/>
 * 链式表达式
 *   支持链式规则 $css{} $xpath{} $regex{} $jsonpath{} $stringrule{}
 *
 * @author virjar
 * @since 0.2.1
 */
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface FetchChain {
    String value();
    //所有抽取注解都应该有这个元素,用来处理集合元素泛型擦除问题
    Class elementType() default Object.class;
}
