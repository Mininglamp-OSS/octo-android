package com.chat.base.markdown;

import io.noties.prism4j.annotations.PrismBundle;

@PrismBundle(
        include = {
                "c", "clike", "cpp", "csharp", "css",
                "dart", "go", "groovy", "java", "javascript",
                "json", "kotlin", "makefile", "markdown", "markup",
                "python", "scala", "sql", "swift", "yaml"
        },
        grammarLocatorClassName = ".WKGrammarLocatorDef"
)
public class WKGrammarLocator {
}
