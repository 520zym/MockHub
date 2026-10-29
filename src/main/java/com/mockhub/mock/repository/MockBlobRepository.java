package com.mockhub.mock.repository;

import com.mockhub.common.model.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Server-owned file capabilities: a team lease or a retained same-team reference is required. */
@Repository
public class MockBlobRepository {
    private final JdbcTemplate jdbc;
    public MockBlobRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void registerLease(String teamId,String path,long expiresAt) {
        requirePath(teamId,path);
        new ApiLifecycleRepository(jdbc).write(() -> {
            jdbc.update("INSERT OR IGNORE INTO mock_blob(file_path,team_id,lease_expires_at) VALUES(?,?,?)",path,teamId,expiresAt);
            if(jdbc.update("UPDATE mock_blob SET lease_expires_at=MAX(lease_expires_at,?) WHERE file_path=? AND team_id=?",
                    expiresAt,path,teamId)!=1) throw denied();
            return null;
        });
    }
    public void requireUsable(String teamId,String path) {
        requirePath(teamId,path);long now=System.currentTimeMillis();
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM mock_blob b WHERE b.file_path=? AND b.team_id=? "
                + "AND (b.lease_expires_at>? OR EXISTS(SELECT 1 FROM mock_blob_ref r WHERE r.file_path=b.file_path "
                + "AND (r.expires_at IS NULL OR r.expires_at>?)))",Integer.class,path,teamId,now,now);
        if(count==null||count!=1) throw denied();
    }
    private void requirePath(String team,String path) {
        if(team==null||path==null||!path.startsWith(team+"/")||path.indexOf('\\')>=0||path.contains("/../")||path.contains("/./")) throw denied();
    }
    private BizException denied() { return new BizException(40925,"ATTACHMENT_ACCESS_DENIED: 附件未获当前团队授权或上传租约已过期，请重新上传"); }
}
