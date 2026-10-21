package com.mockhub.ai.repository;

import com.mockhub.ai.model.AiConversation;
import com.mockhub.ai.model.AiConversationMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** AI 会话与消息数据访问；消息按 rowid 保证用户/助手顺序。 */
@Repository
public class AiConversationRepository {

    private final JdbcTemplate jdbcTemplate;

    public AiConversationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(AiConversation c) {
        jdbcTemplate.update("INSERT INTO ai_conversation (id, user_id, team_id, title, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                c.getId(), c.getUserId(), c.getTeamId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt());
    }

    public AiConversation find(String id, String userId) {
        List<AiConversation> list = jdbcTemplate.query(
                "SELECT * FROM ai_conversation WHERE id = ? AND user_id = ?",
                CONVERSATION_MAPPER, id, userId);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<AiConversation> listByUser(String userId, int limit) {
        return jdbcTemplate.query(
                "SELECT c.*, "
                        + "(SELECT COUNT(*) FROM ai_conversation_message m WHERE m.conversation_id = c.id) AS message_count, "
                        + "(SELECT m.content FROM ai_conversation_message m WHERE m.conversation_id = c.id ORDER BY m.rowid DESC LIMIT 1) AS preview "
                        + "FROM ai_conversation c WHERE c.user_id = ? ORDER BY c.updated_at DESC, c.created_at DESC LIMIT ?",
                LIST_MAPPER, userId, limit);
    }

    public void updateMeta(String id, String title, String updatedAt) {
        jdbcTemplate.update("UPDATE ai_conversation SET title = COALESCE(?, title), updated_at = ? WHERE id = ?",
                title, updatedAt, id);
    }

    public void insertMessage(AiConversationMessage m) {
        jdbcTemplate.update("INSERT INTO ai_conversation_message "
                        + "(id, conversation_id, role, content, cards, provider, model, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                m.getId(), m.getConversationId(), m.getRole(), m.getContent(),
                m.getCards(), m.getProvider(), m.getModel(), m.getCreatedAt());
    }

    public List<AiConversationMessage> listMessages(String conversationId) {
        return jdbcTemplate.query(
                "SELECT * FROM ai_conversation_message WHERE conversation_id = ? ORDER BY rowid ASC",
                MESSAGE_MAPPER, conversationId);
    }

    public void delete(String id, String userId) {
        jdbcTemplate.update("DELETE FROM ai_conversation_message WHERE conversation_id = ?", id);
        jdbcTemplate.update("DELETE FROM ai_conversation WHERE id = ? AND user_id = ?", id, userId);
    }

    private static final RowMapper<AiConversation> CONVERSATION_MAPPER = new RowMapper<AiConversation>() {
        @Override
        public AiConversation mapRow(ResultSet rs, int rowNum) throws SQLException {
            return mapConversation(rs);
        }
    };

    private static final RowMapper<AiConversation> LIST_MAPPER = new RowMapper<AiConversation>() {
        @Override
        public AiConversation mapRow(ResultSet rs, int rowNum) throws SQLException {
            AiConversation c = mapConversation(rs);
            c.setMessageCount(rs.getInt("message_count"));
            c.setPreview(rs.getString("preview"));
            return c;
        }
    };

    private static AiConversation mapConversation(ResultSet rs) throws SQLException {
        AiConversation c = new AiConversation();
        c.setId(rs.getString("id"));
        c.setUserId(rs.getString("user_id"));
        c.setTeamId(rs.getString("team_id"));
        c.setTitle(rs.getString("title"));
        c.setCreatedAt(rs.getString("created_at"));
        c.setUpdatedAt(rs.getString("updated_at"));
        return c;
    }

    private static final RowMapper<AiConversationMessage> MESSAGE_MAPPER = new RowMapper<AiConversationMessage>() {
        @Override
        public AiConversationMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
            AiConversationMessage m = new AiConversationMessage();
            m.setId(rs.getString("id"));
            m.setConversationId(rs.getString("conversation_id"));
            m.setRole(rs.getString("role"));
            m.setContent(rs.getString("content"));
            m.setCards(rs.getString("cards"));
            m.setProvider(rs.getString("provider"));
            m.setModel(rs.getString("model"));
            m.setCreatedAt(rs.getString("created_at"));
            return m;
        }
    };
}
