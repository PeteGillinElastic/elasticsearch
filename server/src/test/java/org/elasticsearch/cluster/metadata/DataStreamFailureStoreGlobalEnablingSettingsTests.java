/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.cluster.metadata;

import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.test.ESTestCase;

import java.util.stream.Stream;

import static com.carrotsearch.randomizedtesting.generators.RandomStrings.randomAsciiAlphanumOfLengthBetween;
import static org.elasticsearch.cluster.metadata.DataStreamFailureStoreGlobalEnablingSettings.FAILURE_STORE_ENABLING_PATTERNS;
import static org.hamcrest.Matchers.is;

public class DataStreamFailureStoreGlobalEnablingSettingsTests extends ESTestCase {

    public void testFailureStoreEnabledForDataStreamName_defaultSettings() {
        DataStreamFailureStoreGlobalEnablingSettings settings = DataStreamFailureStoreGlobalEnablingSettings.create(
            ClusterSettings.createBuiltInClusterSettings()
        );

        // The default should return false for any input.
        // The following will include some illegal names, but it's still valid to test how the method treats them.
        Stream.generate(() -> randomAsciiAlphanumOfLengthBetween(random(), 1, 20)).limit(100).forEach(name -> {
            assertThat(settings.failureStoreEnabledForDataStreamName(name, false), is(false));
            assertThat(settings.failureStoreEnabledForDataStreamName(name, true), is(false));
        });
        Stream.generate(() -> randomUnicodeOfLengthBetween(1, 20)).limit(100).forEach(name -> {
            assertThat(settings.failureStoreEnabledForDataStreamName(name, false), is(false));
            assertThat(settings.failureStoreEnabledForDataStreamName(name, true), is(false));
        });
    }

    public void testFailureStoreEnabledForDataStreamName_exactMatches() {
        DataStreamFailureStoreGlobalEnablingSettings settings = DataStreamFailureStoreGlobalEnablingSettings.create(
            ClusterSettings.createBuiltInClusterSettings(
                // Match exactly 'foo' and 'bar' — whitespace should be stripped:
                Settings.builder().put(FAILURE_STORE_ENABLING_PATTERNS.getKey(), "  foo  , bar  ").build()
            )
        );

        assertThat(settings.failureStoreEnabledForDataStreamName("foo", false), is(true));
        assertThat(settings.failureStoreEnabledForDataStreamName("bar", false), is(true));
        assertThat(settings.failureStoreEnabledForDataStreamName("food", false), is(false));
        assertThat(settings.failureStoreEnabledForDataStreamName("tbar", false), is(false));
        assertThat(settings.failureStoreEnabledForDataStreamName(".foo", false), is(false));
        assertThat(settings.failureStoreEnabledForDataStreamName("barf", false), is(false));
    }

    public void testFailureStoreEnabledForDataStreamName_wildcardMatches() {
        DataStreamFailureStoreGlobalEnablingSettings settings = DataStreamFailureStoreGlobalEnablingSettings.create(
            ClusterSettings.createBuiltInClusterSettings(
                Settings.builder().put(FAILURE_STORE_ENABLING_PATTERNS.getKey(), "  foo*  , *bar  ,  a*z  ").build()
            )
        );

        // These tests aren't exhaustive as the library used is tested thoroughly, but they provide a basic check of the correct usage:
        assertThat(settings.failureStoreEnabledForDataStreamName("foo", false), is(true));
        assertThat(settings.failureStoreEnabledForDataStreamName("bar", false), is(true));
        assertThat(settings.failureStoreEnabledForDataStreamName("food", false), is(true));
        assertThat(settings.failureStoreEnabledForDataStreamName("tbar", false), is(true));
        assertThat(settings.failureStoreEnabledForDataStreamName("az", false), is(true));
        assertThat(settings.failureStoreEnabledForDataStreamName("a123z", false), is(true));
        assertThat(settings.failureStoreEnabledForDataStreamName(".foo", false), is(false));
        assertThat(settings.failureStoreEnabledForDataStreamName("barf", false), is(false));
    }

    public void testFailureStoreEnabledForDataStreamName_excludesInternal() {
        DataStreamFailureStoreGlobalEnablingSettings settings = DataStreamFailureStoreGlobalEnablingSettings.create(
            ClusterSettings.createBuiltInClusterSettings(
                // Match exactly 'foo' and 'bar' — whitespace should be stripped:
                Settings.builder().put(FAILURE_STORE_ENABLING_PATTERNS.getKey(), "  foo  , bar  ").build()
            )
        );

        // Should return false for exact matches, or for anything else, when isInternal is true:
        assertThat(settings.failureStoreEnabledForDataStreamName("foo", true), is(false));
        assertThat(settings.failureStoreEnabledForDataStreamName("bar", true), is(false));
        assertThat(settings.failureStoreEnabledForDataStreamName("zzz", true), is(false));
    }

    public void testFailureStoreEnabledForDataStreamName_respondsToSettingsChange() {
        ClusterSettings clusterSettings = ClusterSettings.createBuiltInClusterSettings(
            Settings.builder().put(FAILURE_STORE_ENABLING_PATTERNS.getKey(), "foo").build()
        );
        DataStreamFailureStoreGlobalEnablingSettings settings = DataStreamFailureStoreGlobalEnablingSettings.create(clusterSettings);

        assertThat(settings.failureStoreEnabledForDataStreamName("foo", false), is(true));
        assertThat(settings.failureStoreEnabledForDataStreamName("bar", false), is(false));

        clusterSettings.applySettings(Settings.builder().put(FAILURE_STORE_ENABLING_PATTERNS.getKey(), "bar").build());

        assertThat(settings.failureStoreEnabledForDataStreamName("foo", false), is(false));
        assertThat(settings.failureStoreEnabledForDataStreamName("bar", false), is(true));
    }
}
