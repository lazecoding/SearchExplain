/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.action.admin.indices.create;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.master.TransportMasterNodeAction;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.block.ClusterBlockLevel;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.metadata.MetaDataCreateIndexService;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.io.IOException;

/**
 * 创建索引的过程，从 ElasticSearch 集群上来说就是写入 Index 元数据的过程，这一操作由 Master 节点完成。因此，TransportCreateIndexAction 继承了 TransportMasterNodeAction，，并实现了 materOperation 方法。
 * <br>
 * Create index action.
 */
public class TransportCreateIndexAction extends TransportMasterNodeAction<CreateIndexRequest, CreateIndexResponse> {

    private final MetaDataCreateIndexService createIndexService;

    @Inject
    public TransportCreateIndexAction(TransportService transportService, ClusterService clusterService,
                                      ThreadPool threadPool, MetaDataCreateIndexService createIndexService,
                                      ActionFilters actionFilters, IndexNameExpressionResolver indexNameExpressionResolver) {
        super(CreateIndexAction.NAME, transportService, clusterService, threadPool, actionFilters, CreateIndexRequest::new,
            indexNameExpressionResolver);
        this.createIndexService = createIndexService;
    }

    @Override
    protected String executor() {
        // we go async right away
        return ThreadPool.Names.SAME;
    }

    @Override
    protected CreateIndexResponse read(StreamInput in) throws IOException {
        return new CreateIndexResponse(in);
    }

    @Override
    protected ClusterBlockException checkBlock(CreateIndexRequest request, ClusterState state) {
        return state.blocks().indexBlockedException(ClusterBlockLevel.METADATA_WRITE, request.index());
    }

    /**
     * 主节点执行的操作：create index
     */
    @Override
    protected void masterOperation(final CreateIndexRequest request, final ClusterState state,
                                   final ActionListener<CreateIndexResponse> listener) {
        String cause = request.cause();
        if (cause.length() == 0) {
            cause = "api";
        }
        // 获取 Index 名
        final String indexName = indexNameExpressionResolver.resolveDateMathExpression(request.index());
        // 初始化 CreateIndexClusterStateUpdateRequest 属性，用于构建 StateUpdateTask
        final CreateIndexClusterStateUpdateRequest updateRequest =
            new CreateIndexClusterStateUpdateRequest(cause, indexName, request.index())
                .ackTimeout(request.timeout()).masterNodeTimeout(request.masterNodeTimeout())
                .settings(request.settings()).mappings(request.mappings())
                .aliases(request.aliases())
                .waitForActiveShards(request.waitForActiveShards());
        // 执行 CreateIndex
        createIndexService.createIndex(updateRequest, ActionListener.map(listener, response ->
            new CreateIndexResponse(response.isAcknowledged(), response.isShardsAcknowledged(), indexName)));
    }

}
