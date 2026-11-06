package com.agent.gateway.observe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpanRecorderTest {

    private JdbcTemplate jdbc;
    private SpanRecorder recorder;
    private TraceQueryService query;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:span_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        new ResourceDatabasePopulator(new ClassPathResource("schema-h2.sql")).execute(ds);
        jdbc = new JdbcTemplate(ds);
        jdbc.update("INSERT INTO gw_tenant (id, name) VALUES (?, ?)", "tA", "A");
        recorder = new SpanRecorder(jdbc);
        query = new TraceQueryService(jdbc);
    }

    @Test
    void startAndEndBuildsTreeWithToolChild() {
        String traceId = TraceIds.newTraceId();
        String run = recorder.start("tA", traceId, null, "conv1", "task1", "chat.run", SpanKind.RUN, SpanStatus.OK, null);
        String tool = recorder.start("tA", traceId, run, "conv1", "task1", "demo_weather", SpanKind.TOOL, SpanStatus.OK, null);
        recorder.ok("tA", tool);
        recorder.ok("tA", run);

        Map<String, Object> view = query.getTrace("tA", traceId);
        @SuppressWarnings("unchecked")
        List<SpanRecord> spans = (List<SpanRecord>) view.get("spans");
        assertEquals(2, spans.size());
        @SuppressWarnings("unchecked")
        List<TraceTreeNode> tree = (List<TraceTreeNode>) view.get("tree");
        assertEquals(1, tree.size());
        assertEquals("chat.run", tree.get(0).span().name());
        assertEquals(1, tree.get(0).children().size());
        assertEquals("demo_weather", tree.get(0).children().get(0).span().name());
        assertNotNull(tree.get(0).span().durationMs());
    }

    @Test
    void listByConversationReturnsSummary() {
        String traceId = TraceIds.newTraceId();
        String run = recorder.start("tA", traceId, null, "convX", "task9", "chat.run", SpanKind.RUN, SpanStatus.OK, null);
        recorder.start("tA", traceId, run, "convX", "task9", "demo_ping", SpanKind.TOOL, SpanStatus.OK, null);
        recorder.ok("tA", run);

        List<Map<String, Object>> list = query.listByConversation("tA", "convX", 10);
        assertFalse(list.isEmpty());
        assertEquals(traceId, list.get(0).get("traceId"));
        assertEquals(1L, list.get(0).get("toolCount"));
    }
}
