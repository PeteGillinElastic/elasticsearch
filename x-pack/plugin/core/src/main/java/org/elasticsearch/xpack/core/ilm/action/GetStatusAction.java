/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.core.ilm.action;

import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.ActionType;
import org.elasticsearch.action.support.local.LocalClusterStateRequest;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.core.UpdateForV10;
import org.elasticsearch.tasks.CancellableTask;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.tasks.TaskId;
import org.elasticsearch.xcontent.ToXContentObject;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xpack.core.ilm.OperationMode;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

public class GetStatusAction extends ActionType<GetStatusAction.Response> {
    public static final GetStatusAction INSTANCE = new GetStatusAction();
    public static final String NAME = "cluster:admin/ilm/operation_mode/get";

    protected GetStatusAction() {
        super(NAME);
    }

    public static class Request extends LocalClusterStateRequest {

        public Request(TimeValue masterTimeout) {
            super(masterTimeout);
        }

        /**
         * NB prior to 9.1 this was a TransportMasterNodeAction so for BwC we must remain able to read these requests until
         * we no longer need to support calling this action remotely.
         */
        @UpdateForV10(owner = UpdateForV10.Owner.DATA_MANAGEMENT)
        public Request(StreamInput in) throws IOException {
            super(in, false);
            // Read and ignore ack timeout.
            in.readTimeValue();
        }

        @Override
        public Task createTask(long id, String type, String action, TaskId parentTaskId, Map<String, String> headers) {
            return new CancellableTask(id, type, action, "", parentTaskId, headers);
        }
    }

    public static class Response extends ActionResponse implements ToXContentObject {

        private final OperationMode mode;

        public Response(StreamInput in) throws IOException {
            mode = in.readEnum(OperationMode.class);
        }

        public Response(OperationMode mode) {
            this.mode = mode;
        }

        public OperationMode getMode() {
            return mode;
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            builder.startObject();
            builder.field("operation_mode", mode);
            builder.endObject();
            return builder;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeEnum(mode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mode);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (obj.getClass() != getClass()) {
                return false;
            }
            Response other = (Response) obj;
            return Objects.equals(mode, other.mode);
        }

        @Override
        public String toString() {
            return Strings.toString(this, true, true);
        }

    }
}
