/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.cluster.metadata;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.regex.Regex;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.core.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * This class holds the data stream global settings for enabling the failure store based on data stream name pattern matching. It defines,
 * validates and monitors the settings.
 */
public class DataStreamFailureStoreGlobalEnablingSettings {

    private static final Logger logger = LogManager.getLogger(DataStreamFailureStoreGlobalEnablingSettings.class);

    // TODO(pete): Finalize name
    public static final Setting<List<String>> FAILURE_STORE_ENABLING_PATTERNS = Setting.stringListSetting(
        "data_streams.failure_store.enabled_patterns",
        Setting.Property.Dynamic,
        Setting.Property.NodeScope
    );

    @Nullable
    private Predicate<String> enablingMatcher;

    private DataStreamFailureStoreGlobalEnablingSettings() {}

    /**
     * Creates an instance and initialises the cluster settings listeners.
     */
    public static DataStreamFailureStoreGlobalEnablingSettings create(ClusterSettings clusterSettings) {
        DataStreamFailureStoreGlobalEnablingSettings dataStreamGlobalRetentionSettings = new DataStreamFailureStoreGlobalEnablingSettings();
        clusterSettings.initializeAndWatch(FAILURE_STORE_ENABLING_PATTERNS, dataStreamGlobalRetentionSettings::setEnablingPatterns);
        return dataStreamGlobalRetentionSettings;
    }

    public boolean failureStoreEnabledForDataStreamName(String name) {
        return enablingMatcher != null && enablingMatcher.test(name);
    }

    private void setEnablingPatterns(List<String> patterns) {
        enablingMatcher = Regex.simpleMatcher(patterns.toArray(String[]::new));
        logger.info("Updated data string name patterns for enabling failure store to [{}]", patterns);
    }
}
