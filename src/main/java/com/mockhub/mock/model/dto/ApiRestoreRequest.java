package com.mockhub.mock.model.dto;
public class ApiRestoreRequest {
    private Long expectedRevision;
    private String newPath;
    public Long getExpectedRevision() { return expectedRevision; }
    public void setExpectedRevision(Long value) { expectedRevision=value; }
    public String getNewPath() { return newPath; }
    public void setNewPath(String value) { newPath=value; }
}
