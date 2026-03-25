package com.chat.base.markdown;

import io.noties.prism4j.Prism4j;

/**
 * Java 工厂类：解决 Kotlin 无法引用注解处理器生成的 WKGrammarLocatorDef 的问题
 */
public class WKPrism4jFactory {
    public static Prism4j create() {
        return new Prism4j(new WKGrammarLocatorDef());
    }
}
