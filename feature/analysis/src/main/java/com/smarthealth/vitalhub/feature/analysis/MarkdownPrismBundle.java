package com.smarthealth.vitalhub.feature.analysis;

import io.noties.prism4j.annotations.PrismBundle;

@PrismBundle(
        includeAll = true,
        grammarLocatorClassName = ".MarkdownGrammarLocator"
)
final class MarkdownPrismBundle {
    private MarkdownPrismBundle() {
    }
}
