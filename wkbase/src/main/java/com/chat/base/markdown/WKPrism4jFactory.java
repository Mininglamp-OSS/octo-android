/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
