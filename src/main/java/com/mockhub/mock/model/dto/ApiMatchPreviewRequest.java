package com.mockhub.mock.model.dto;

import java.util.List;
import java.util.Map;

/** A sample request and an optional, unsaved response configuration. Never persisted. */
public class ApiMatchPreviewRequest {
    private String teamId;
    private String apiId;
    private String operationName;
    private String responseMode;
    private List<Response> responses;
    private Sample sample;

    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }
    public String getApiId() { return apiId; }
    public void setApiId(String apiId) { this.apiId = apiId; }
    public String getOperationName() { return operationName; }
    public void setOperationName(String operationName) { this.operationName = operationName; }
    public String getResponseMode() { return responseMode; }
    public void setResponseMode(String responseMode) { this.responseMode = responseMode; }
    public List<Response> getResponses() { return responses; }
    public void setResponses(List<Response> responses) { this.responses = responses; }
    public Sample getSample() { return sample; }
    public void setSample(Sample sample) { this.sample = sample; }

    /** clientKey is opaque; index, not id/key uniqueness, identifies the selected draft tab. */
    public static class Response extends ApiResponseDTO {
        private String clientKey;
        public String getClientKey() { return clientKey; }
        public void setClientKey(String clientKey) { this.clientKey = clientKey; }
    }

    public static class Sample {
        private String body;
        private Map<String, String> query;
        private Map<String, String> headers;
        private String contentType;
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
        public Map<String, String> getQuery() { return query; }
        public void setQuery(Map<String, String> query) { this.query = query; }
        public Map<String, String> getHeaders() { return headers; }
        public void setHeaders(Map<String, String> headers) { this.headers = headers; }
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
    }
}
