package com.example.sensitivedetection.ambiguity.workflow;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.example.sensitivedetection.ambiguity.workflow.node.AmbiguitySummarizeNode;
import com.example.sensitivedetection.ambiguity.workflow.node.CacheLoadNode;
import com.example.sensitivedetection.ambiguity.workflow.node.CacheSaveNode;
import com.example.sensitivedetection.ambiguity.workflow.node.GateNode;
import com.example.sensitivedetection.ambiguity.workflow.node.PostProcessNode;
import com.example.sensitivedetection.ambiguity.workflow.node.Stage1Node;
import com.example.sensitivedetection.ambiguity.workflow.node.Stage2Node;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

@Configuration
public class AmbiguityGraphConfig {

    @Bean
    public CompiledGraph ambiguityGraph(
            CacheLoadNode cacheLoadNode,
            GateNode gateNode,
            Stage1Node stage1Node,
            Stage2Node stage2Node,
            PostProcessNode postProcessNode,
            CacheSaveNode cacheSaveNode,
            AmbiguitySummarizeNode ambiguitySummarizeNode) throws GraphStateException {

        KeyStrategyFactory keyStrategyFactory = () -> Map.<String, KeyStrategy>of(
                "results", new ReplaceStrategy(),
                "batchNo", new ReplaceStrategy()
        );

        StateGraph graph = new StateGraph(keyStrategyFactory)
                .addNode("cacheLoad", node_async(cacheLoadNode))
                .addNode("gate", node_async(gateNode))
                .addNode("stage1", node_async(stage1Node))
                .addNode("stage2", node_async(stage2Node))
                .addNode("postProcess", node_async(postProcessNode))
                .addNode("cacheSave", node_async(cacheSaveNode))
                .addNode("summarize", node_async(ambiguitySummarizeNode))
                .addEdge(START, "cacheLoad")
                .addEdge("cacheLoad", "gate")
                .addEdge("gate", "stage1")
                .addEdge("stage1", "stage2")
                .addEdge("stage2", "postProcess")
                .addEdge("postProcess", "cacheSave")
                .addEdge("cacheSave", "summarize")
                .addEdge("summarize", END);

        return graph.compile();
    }
}
