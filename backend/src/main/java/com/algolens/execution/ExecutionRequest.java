package com.algolens.execution;

import com.algolens.entity.Language;

/** What an executor needs: a language and some source. Nothing user- or transport-specific. */
public record ExecutionRequest(Language language, String code) {
}
