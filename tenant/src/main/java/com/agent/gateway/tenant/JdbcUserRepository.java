package com.agent.gateway.tenant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户与项目成员 JDBC 仓库。
 */
@Repository
public class JdbcUserRepository {

    private final JdbcTemplate jdbc;

    public JdbcUserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertUser(UserRecord user) {
        jdbc.update("""
                INSERT INTO gw_user (id, email, password_hash, status)
                VALUES (?, ?, ?, ?)
                """,
                user.id(), user.email(), user.passwordHash(), user.status());
    }

    public Optional<UserRecord> findByEmail(String email) {
        String normalized = normalizeEmail(email);
        List<UserRecord> rows = jdbc.query("""
                SELECT id, email, password_hash, status FROM gw_user WHERE email = ?
                """,
                (rs, i) -> new UserRecord(
                        rs.getString("id"),
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        rs.getString("status")),
                normalized);
        return rows.stream().findFirst();
    }

    public Optional<UserRecord> findById(String userId) {
        List<UserRecord> rows = jdbc.query("""
                SELECT id, email, password_hash, status FROM gw_user WHERE id = ?
                """,
                (rs, i) -> new UserRecord(
                        rs.getString("id"),
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        rs.getString("status")),
                userId);
        return rows.stream().findFirst();
    }

    public void insertMember(ProjectMemberRecord member) {
        TenantIds.require(member.tenantId());
        jdbc.update("""
                INSERT INTO gw_project_member (tenant_id, user_id, role)
                VALUES (?, ?, ?)
                """,
                member.tenantId(), member.userId(), member.role());
    }

    public Optional<ProjectMemberRecord> findMember(String tenantId, String userId) {
        String tid = TenantIds.require(tenantId);
        List<ProjectMemberRecord> rows = jdbc.query("""
                SELECT tenant_id, user_id, role FROM gw_project_member
                WHERE tenant_id = ? AND user_id = ?
                """,
                (rs, i) -> new ProjectMemberRecord(
                        rs.getString("tenant_id"),
                        rs.getString("user_id"),
                        rs.getString("role")),
                tid, userId);
        return rows.stream().findFirst();
    }

    public List<ProjectMemberRecord> listMemberships(String userId) {
        return jdbc.query("""
                SELECT tenant_id, user_id, role FROM gw_project_member
                WHERE user_id = ?
                ORDER BY created_at ASC
                """,
                (rs, i) -> new ProjectMemberRecord(
                        rs.getString("tenant_id"),
                        rs.getString("user_id"),
                        rs.getString("role")),
                userId);
    }

    public static String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase();
    }
}
