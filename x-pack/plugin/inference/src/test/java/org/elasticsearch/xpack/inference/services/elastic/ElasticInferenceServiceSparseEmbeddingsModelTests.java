/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.services.elastic;

import org.elasticsearch.inference.EmptySecretSettings;
import org.elasticsearch.inference.EmptyTaskSettings;
import org.elasticsearch.inference.TaskType;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xpack.inference.services.elasticsearch.ElserModels;

public class ElasticInferenceServiceSparseEmbeddingsModelTests extends ESTestCase {

    public void testCreateURI_ThrowError_OnMissingURIScheme() {
        expectThrows(IllegalArgumentException.class, () -> createModel("www.missing-scheme-gateway-url.com"));
    }

    public void testCreateURI_ThrowError_OnWrongURIScheme() {
        expectThrows(IllegalArgumentException.class, () -> createModel("file://www.missing-scheme-gateway-url.com"));
    }

    public void testCreateURI_DoesNotThrowError_ForHTTP() {
        var scheme = "http";

        try {
            createModel(scheme + "://www.valid-gateway-url.com");
        } catch (Exception e) {
            fail(e, "Should not throw exception for " + "[" + scheme + "]");
        }
    }

    public void testCreateURI_DoesNotThrowError_ForHTTPS() {
        var scheme = "https";

        try {
            createModel(scheme + "://www.valid-gateway-url.com");
        } catch (Exception e) {
            fail(e, "Should not throw exception for " + "[" + scheme + "]");
        }
    }

    public static ElasticInferenceServiceSparseEmbeddingsModel createModel(String url) {
        return createModel(url, null);
    }

    public static ElasticInferenceServiceSparseEmbeddingsModel createModel(String url, Integer maxInputTokens) {
        return new ElasticInferenceServiceSparseEmbeddingsModel(
            "id",
            TaskType.SPARSE_EMBEDDING,
            "service",
            new ElasticInferenceServiceSparseEmbeddingsServiceSettings(ElserModels.ELSER_V2_MODEL, maxInputTokens, null),
            EmptyTaskSettings.INSTANCE,
            EmptySecretSettings.INSTANCE,
            new ElasticInferenceServiceComponents(url)
        );
    }
}
