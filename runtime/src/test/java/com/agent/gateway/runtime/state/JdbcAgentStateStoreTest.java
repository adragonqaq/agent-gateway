package com.agent.gateway.runtime.state;

import io.agentscope.core.state.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcAgentStateStoreTest {

    private JdbcAgentStateStore store;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:as_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        new ResourceDatabasePopulator(new ClassPathResource("schema-h2.sql")).execute(ds);
        jdbc = new JdbcTemplate(ds);
        store = new JdbcAgentStateStore(jdbc);
    }

    @Test
    void saveLoadRoundTripAndCrossTenantEmpty() {
        DemoState value = new DemoState();
        value.setText("hello");
        store.save("tA", "c1", "agent_state", value);
        assertTrue(store.exists("tA", "c1"));
        assertEquals("hello", store.get("tA", "c1", "agent_state", DemoState.class).orElseThrow().getText());
        assertFalse(store.exists("tB", "c1"));
        assertTrue(store.get("tB", "c1", "agent_state", DemoState.class).isEmpty());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM gw_agentscope_state WHERE tenant_id = ?", Integer.class, "tB"));
    }

    /** 测试用 POJO State。 */
    public static class DemoState implements State {
        private String text;

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }
}
