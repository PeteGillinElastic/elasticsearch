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
// TODO(pete): Finalize name of settings class
public class DataStreamFailureStoreGlobalEnablingSettings {

    private static final Logger logger = LogManager.getLogger(DataStreamFailureStoreGlobalEnablingSettings.class);

    // TODO(pete): Finalize name of setting itself
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
     *
     * @param clusterSettings The cluster settings to initialize the instance from and to watch for updates to
     */
    public static DataStreamFailureStoreGlobalEnablingSettings create(ClusterSettings clusterSettings) {
        DataStreamFailureStoreGlobalEnablingSettings dataStreamGlobalRetentionSettings = new DataStreamFailureStoreGlobalEnablingSettings();
        clusterSettings.initializeAndWatch(FAILURE_STORE_ENABLING_PATTERNS, dataStreamGlobalRetentionSettings::setEnablingPatterns);
        return dataStreamGlobalRetentionSettings;
    }

    /**
     * Returns whether the settings indicate that the failure store should be enabled by the cluster settings for the given name.
     *
     * @param name The data stream name
     * @param isInternal Whether this is an internal (system or dot-prefix) data stream: if true, this method will always return false
     */
    public boolean failureStoreEnabledForDataStreamName(String name, boolean isInternal) {
        // TODO(pete): Evaluate whether we need to cache this, and where the best place to do that is if so.
        boolean match = enablingMatcher != null && enablingMatcher.test(name);
        boolean ret = isInternal == false && match;
        logger.info("***** failureStoreEnabledForDataStreamName returning !{} && {} == {} for {}", isInternal, match, ret, name);
        return ret;
    }

    private void setEnablingPatterns(List<String> patterns) {
        enablingMatcher = Regex.simpleMatcher(patterns.toArray(String[]::new));
        logger.info("Updated data string name patterns for enabling failure store to [{}]", patterns);
    }
}
