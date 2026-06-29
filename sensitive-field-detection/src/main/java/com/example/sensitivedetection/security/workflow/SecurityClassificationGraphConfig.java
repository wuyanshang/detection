package com.example.sensitivedetection.security.workflow;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.example.sensitivedetection.security.workflow.node.AiMatchNode;
import com.example.sensitivedetection.security.workflow.node.Bm25SearchNode;
import com.example.sensitivedetection.security.workflow.node.ExemptionCheckNode;
import com.example.sensitivedetection.security.workflow.node.MergeDedupNode;
import com.example.sensitivedetection.security.workflow.node.ResultSaveNode;
import com.example.sensitivedetection.security.workflow.node.SynonymReplaceNode;
import com.example.sensitivedetection.security.workflow.node.VectorSearchNode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * 安全分类分级工作流编排（对应设计文档 8.1）。
 *
 * 流程：
 *   exemptionCheck → synonymReplace ─┬→ vectorSearch ─┐
 *                                    └→ bm25Search  ─┴→ mergeDedup → aiMatch → resultSave
 *
 * 并行说明（文档 8.1）：
 *  - synonymReplace 后 fan-out 出 vectorSearch 与 bm25Search 两条边 → 框架并行执行；
 *  - 两路写入【不同的】state 键（vectorResults / bm25Results），各用 ReplaceStrategy，互不覆盖；
 *  - 二者均不写 result 键，避免并行写同一对象造成竞态；
 *  - mergeDedup 是 fan-in 汇聚点（两条入边），框架在两路都完成后才执行（barrier），
 *    它从 vectorResults / bm25Results 两个键读取结果并合并回 result。
 *  - 免检命中后，各节点通过 result.isExempt() 短路（不做实际计算）。
 */
@Configuration
public class SecurityClassificationGraphConfig {

    @Bean
    public CompiledGraph securityClassificationGraph(
            ExemptionCheckNode exemptionCheckNode,
            SynonymReplaceNode synonymReplaceNode,
            VectorSearchNode vectorSearchNode,
            Bm25SearchNode bm25SearchNode,
            MergeDedupNode mergeDedupNode,
            AiMatchNode aiMatchNode,
            ResultSaveNode resultSaveNode) throws GraphStateException {

        KeyStrategyFactory keyStrategyFactory = () -> Map.<String, KeyStrategy>of(
                "result", new ReplaceStrategy(),
                "vectorResults", new ReplaceStrategy(),
                "bm25Results", new ReplaceStrategy()
        );

        StateGraph graph = new StateGraph(keyStrategyFactory)
                .addNode("exemptionCheck", node_async(exemptionCheckNode))
                .addNode("synonymReplace", node_async(synonymReplaceNode))
                .addNode("vectorSearch", node_async(vectorSearchNode))
                .addNode("bm25Search", node_async(bm25SearchNode))
                .addNode("mergeDedup", node_async(mergeDedupNode))
                .addNode("aiMatch", node_async(aiMatchNode))
                .addNode("resultSave", node_async(resultSaveNode))
                .addEdge(START, "exemptionCheck")
                .addEdge("exemptionCheck", "synonymReplace")
                // fan-out：并行执行向量检索与 BM25 检索
                .addEdge("synonymReplace", "vectorSearch")
                .addEdge("synonymReplace", "bm25Search")
                // fan-in：两路都完成后汇聚到 mergeDedup
                .addEdge("vectorSearch", "mergeDedup")
                .addEdge("bm25Search", "mergeDedup")
                .addEdge("mergeDedup", "aiMatch")
                .addEdge("aiMatch", "resultSave")
                .addEdge("resultSave", END);

        return graph.compile();
    }
}
