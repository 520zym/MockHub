package com.mockhub.files.model;

/** 文件服务器持久化元数据；文件内容独立保存在 data/file-server。 */
public class StoredFile {
    private String fileId;
    private String teamId;
    private String fileName;
    private String alias;
    private String contentType;
    private long size;
    private String uploadedAt;
    private long downloadCount;
    private long transferredBytes;
    private String lastDownloadedAt;
    private java.util.List<String> tags = new java.util.ArrayList<String>();

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }

    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public String getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(String uploadedAt) { this.uploadedAt = uploadedAt; }

    public long getDownloadCount() { return downloadCount; }
    public void setDownloadCount(long downloadCount) { this.downloadCount = downloadCount; }

    public long getTransferredBytes() { return transferredBytes; }
    public void setTransferredBytes(long transferredBytes) { this.transferredBytes = transferredBytes; }

    public String getLastDownloadedAt() { return lastDownloadedAt; }
    public void setLastDownloadedAt(String lastDownloadedAt) { this.lastDownloadedAt = lastDownloadedAt; }

    public java.util.List<String> getTags() { return tags; }
    public void setTags(java.util.List<String> tags) { this.tags = tags; }
}
