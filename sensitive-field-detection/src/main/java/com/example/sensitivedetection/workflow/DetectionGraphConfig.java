package com.example.sensitivedetection.workflow;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.example.sensitivedetection.workflow.node.KeywordMatchNode;
import com.example.sensitivedetection.workflow.node.LlmDetectNode;
import com.example.sensitivedetection.workflow.node.QualityCheckNode;
import com.example.sensitivedetection.workflow.node.SummarizeNode;
import com.example.sensitivedetection.workflow.node.TableCheckNode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

@Configuration
public class DetectionGraphConfig {

    @Bean
    public CompiledGraph detectionGraph(
            TableCheckNode tableCheckNode,
            QualityCheckNode qualityCheckNode,
            KeywordMatchNode keywordMatchNode,
            LlmDetectNode llmDetectNode,
            SummarizeNode summarizeNode) throws GraphStateException {

        // 定义状态 schema，所有 key 使用替换策略
        KeyStrategyFactory keyStrategyFactory = () -> Map.<String, KeyStrategy>of(
                "results", new ReplaceStrategy(),
                "tableResults", new ReplaceStrategy(),
                "batchNo", new ReplaceStrategy(),
                "hasUndecided", new ReplaceStrategy()
        );

        StateGraph graph = new StateGraph(keyStrategyFactory)
                // 节点
                .addNode("tableCheck", node_async(tableCheckNode))
                .addNode("qualityCheck", node_async(qualityCheckNode))
                .addNode("keywordMatch", node_async(keywordMatchNode))
                .addNode("llmDetect", node_async(llmDetectNode))
                .addNode("summarize", node_async(summarizeNode))
                // 边：START → 表级质检 → 字段质检 → 关键词
                .addEdge(START, "tableCheck")
                .addEdge("tableCheck", "qualityCheck")
                .addEdge("qualityCheck", "keywordMatch")
                // 条件边：关键词 → 有未决字段则走 LLM，否则直接汇总
                .addConditionalEdges("keywordMatch",
                        edge_async(state -> {
                            Boolean hasUndecided = (Boolean) state.value("hasUndecided").orElse(false);
                            return hasUndecided ? "llmDetect" : "summarize";
                        }),
                        Map.of("llmDetect", "llmDetect", "summarize", "summarize"))
                // 边：LLM → 汇总 → END
                .addEdge("llmDetect", "summarize")
                .addEdge("summarize", END);

        return graph.compile();
    }
}
