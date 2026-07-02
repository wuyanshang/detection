package com.example.sensitivedetection.classify2.workflow;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.example.sensitivedetection.classify2.workflow.node.Bm25SearchV2Node;
import com.example.sensitivedetection.classify2.workflow.node.JudgeV2Node;
import com.example.sensitivedetection.classify2.workflow.node.MergeV2Node;
import com.example.sensitivedetection.classify2.workflow.node.QueryPrepV2Node;
import com.example.sensitivedetection.classify2.workflow.node.VectorSearchV2Node;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * classify-v2 单字段工作流编排。表级推断在 Workflow 层完成后注入 result，图内只处理单字段。
 *
 * 流程：
 *   queryPrep ─┬→ vectorSearch ─┐
 *              └→ bm25Search  ─┴→ mergeDedup → judge
 */
@Configuration
public class ClassifyV2GraphConfig {

    @Bean
    public CompiledGraph classifyV2Graph(
            QueryPrepV2Node queryPrepV2Node,
            VectorSearchV2Node vectorSearchV2Node,
            Bm25SearchV2Node bm25SearchV2Node,
            MergeV2Node mergeV2Node,
            JudgeV2Node judgeV2Node) throws GraphStateException {

        KeyStrategyFactory keyStrategyFactory = () -> Map.<String, KeyStrategy>of(
                "result", new ReplaceStrategy(),
                "vectorResults", new ReplaceStrategy(),
                "bm25Results", new ReplaceStrategy()
        );

        StateGraph graph = new StateGraph(keyStrategyFactory)
                .addNode("queryPrep", node_async(queryPrepV2Node))
                .addNode("vectorSearch", node_async(vectorSearchV2Node))
                .addNode("bm25Search", node_async(bm25SearchV2Node))
                .addNode("mergeDedup", node_async(mergeV2Node))
                .addNode("judge", node_async(judgeV2Node))
                .addEdge(START, "queryPrep")
                .addEdge("queryPrep", "vectorSearch")
                .addEdge("queryPrep", "bm25Search")
                .addEdge("vectorSearch", "mergeDedup")
                .addEdge("bm25Search", "mergeDedup")
                .addEdge("mergeDedup", "judge")
                .addEdge("judge", END);

        return graph.compile();
    }
}
