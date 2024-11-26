/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.core.action;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.DataStream;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.injection.guice.Inject;
import org.elasticsearch.protocol.xpack.XPackUsageRequest;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.elasticsearch.xpack.core.datastreams.DataStreamFeatureSetUsage;

import java.util.Map;

public class DataStreamUsageTransportAction extends XPackUsageFeatureTransportAction {

    @Inject
    public DataStreamUsageTransportAction(
        TransportService transportService,
        ClusterService clusterService,
        ThreadPool threadPool,
        ActionFilters actionFilters,
        IndexNameExpressionResolver indexNameExpressionResolver
    ) {
        super(
            XPackUsageFeatureAction.DATA_STREAMS.name(),
            transportService,
            clusterService,
            threadPool,
            actionFilters,
            indexNameExpressionResolver
        );
    }

    @Override
    protected void masterOperation(
        Task task,
        XPackUsageRequest request,
        ClusterState state,
        ActionListener<XPackUsageFeatureResponse> listener
    ) {
        final Map<String, DataStream> dataStreams = state.metadata().dataStreams();
        long backingIndicesCounter = 0;
        long failureStoreEnabledCounter = 0;
        long failureIndicesCounter = 0;
        for (DataStream ds : dataStreams.values()) {
            backingIndicesCounter += ds.getIndices().size();
            if (DataStream.isFailureStoreFeatureFlagEnabled()) {
                // TODO(pete): Figure out right behaviour here - this is for the /_xpack/usage API and determines the value of
                // data_streams.failure_store.enabled_count, and it's not clear whether that should be 'explicitly enabled' or
                // 'effectively enabled with the current cluster settings'... or even whether we should expose both - thread started at
                // https://elastic.slack.com/archives/C076436CY2K/p1732642356228629:
                if (ds.isFailureStoreEnabled()) {
                    failureStoreEnabledCounter++;
                }
                if (ds.getFailureIndices().getIndices().isEmpty() == false) {
                    failureIndicesCounter += ds.getFailureIndices().getIndices().size();
                }
            }
        }
        final DataStreamFeatureSetUsage.DataStreamStats stats = new DataStreamFeatureSetUsage.DataStreamStats(
            dataStreams.size(),
            backingIndicesCounter,
            failureStoreEnabledCounter,
            failureIndicesCounter
        );
        final DataStreamFeatureSetUsage usage = new DataStreamFeatureSetUsage(stats);
        listener.onResponse(new XPackUsageFeatureResponse(usage));
    }
}
