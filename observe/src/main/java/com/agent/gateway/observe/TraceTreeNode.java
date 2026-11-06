package com.agent.gateway.observe;

import java.util.ArrayList;
import java.util.List;

/**
 * 树形 Trace 节点（API 返回）。
 * <p>所属模块：observe。
 *
 * @param span 本节点 Span
 * @param children 子节点
 */
public record TraceTreeNode(SpanRecord span, List<TraceTreeNode> children) {

    /**
     * 构造空子列表节点。
     */
    public TraceTreeNode(SpanRecord span) {
        this(span, new ArrayList<>());
    }
}
