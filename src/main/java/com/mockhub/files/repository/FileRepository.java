package com.mockhub.files.repository;

import com.mockhub.common.model.PageResult;
import com.mockhub.files.model.StoredFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

@Repository
public class FileRepository {
    private final JdbcTemplate jdbc;
    public FileRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final RowMapper<StoredFile> MAPPER = (rs, row) -> {
        StoredFile f = new StoredFile();
        f.setFileId(rs.getString("file_id")); f.setTeamId(rs.getString("team_id"));
        f.setFileName(rs.getString("file_name")); f.setAlias(rs.getString("alias"));
        f.setContentType(rs.getString("content_type")); f.setSize(rs.getLong("file_size"));
        f.setUploadedAt(rs.getString("uploaded_at")); f.setDownloadCount(rs.getLong("download_count"));
        f.setTransferredBytes(rs.getLong("transferred_bytes")); f.setLastDownloadedAt(rs.getString("last_downloaded_at"));
        return f;
    };

    public void insert(StoredFile f) {
        jdbc.update("INSERT INTO file_server_file(file_id,team_id,file_name,content_type,file_size,uploaded_at) VALUES(?,?,?,?,?,?)",
                f.getFileId(), f.getTeamId(), f.getFileName(), f.getContentType(), f.getSize(), f.getUploadedAt());
    }

    public StoredFile find(String id) {
        List<StoredFile> files = jdbc.query("SELECT f.* FROM file_server_file f JOIN team t ON t.id=f.team_id WHERE file_id=? AND deleted=0", MAPPER, id);
        if (files.isEmpty()) return null;
        StoredFile f = files.get(0);
        f.setTags(jdbc.queryForList("SELECT tag FROM file_server_tag WHERE file_id=? ORDER BY tag", String.class, id));
        return f;
    }

    public PageResult<StoredFile> list(String teamId, String userId, String keyword, String tag, int page, int size) {
        StringBuilder where = new StringBuilder(" FROM file_server_file f JOIN team t ON t.id=f.team_id WHERE f.deleted=0");
        List<Object> args = new ArrayList<>();
        if (teamId != null && !teamId.isEmpty()) { where.append(" AND f.team_id=?"); args.add(teamId); }
        if (userId != null) { where.append(" AND f.team_id IN (SELECT team_id FROM user_team WHERE user_id=?)"); args.add(userId); }
        if (keyword != null && !keyword.trim().isEmpty()) {
            where.append(" AND (instr(lower(f.file_id),lower(?))>0 OR instr(lower(f.file_name),lower(?))>0 OR instr(lower(f.alias),lower(?))>0)");
            for (int i=0; i<3; i++) args.add(keyword.trim());
        }
        if (tag != null && !tag.trim().isEmpty()) {
            where.append(" AND EXISTS(SELECT 1 FROM file_server_tag ft WHERE ft.file_id=f.file_id AND ft.tag=?)"); args.add(tag.trim());
        }
        long count = jdbc.queryForObject("SELECT count(*)" + where, args.toArray(), Long.class);
        args.add(size); args.add(((long) page - 1) * size);
        List<StoredFile> items = jdbc.query("SELECT f.*" + where + " ORDER BY uploaded_at DESC,file_id LIMIT ? OFFSET ?", args.toArray(), MAPPER);
        for (StoredFile f : items) f.setTags(jdbc.queryForList("SELECT tag FROM file_server_tag WHERE file_id=? ORDER BY tag", String.class, f.getFileId()));
        return PageResult.of(items, count, page, size);
    }

    @Transactional
    public void update(String id, String alias, List<String> tags) {
        jdbc.update("UPDATE file_server_file SET alias=? WHERE file_id=? AND deleted=0", alias, id);
        jdbc.update("DELETE FROM file_server_tag WHERE file_id=?", id);
        for (String tag : tags) jdbc.update("INSERT INTO file_server_tag(file_id,tag) VALUES(?,?)", id, tag);
    }

    @Transactional
    public void markDeleted(List<String> ids) {
        for (String id : ids) {
            jdbc.update("UPDATE file_server_file SET deleted=1 WHERE file_id=?", id);
            jdbc.update("DELETE FROM file_server_tag WHERE file_id=?", id);
        }
    }

    public List<String> pendingDeletion() {
        return jdbc.queryForList("SELECT file_id FROM file_server_file WHERE deleted=1 LIMIT 500", String.class);
    }
    public void finishDeletion(String id) { jdbc.update("DELETE FROM file_server_file WHERE file_id=? AND deleted=1", id); }
    public void recordTransfer(String id, long bytes, String time) {
        jdbc.update("UPDATE file_server_file SET download_count=download_count+1,transferred_bytes=transferred_bytes+?,last_downloaded_at=? WHERE file_id=? AND deleted=0", bytes, time, id);
    }
}
