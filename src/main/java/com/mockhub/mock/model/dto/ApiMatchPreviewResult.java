package com.mockhub.mock.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/** Selection metadata only: no response bodies, storage paths, binary data or conditions. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiMatchPreviewResult {
    private String matchedResponseId;
    private String matchedResponseName;
    private String matchedClientKey;
    private Integer matchedIndex;
    private Integer responseCode;
    private String bodyType;
    private String reason;
    private String mode;
    private int enabledCount;
    private boolean usingSavedConfiguration;
    private List<Candidate> candidates = new ArrayList<Candidate>();

    public String getMatchedResponseId() { return matchedResponseId; }
    public void setMatchedResponseId(String value) { matchedResponseId = value; }
    public String getMatchedResponseName() { return matchedResponseName; }
    public void setMatchedResponseName(String value) { matchedResponseName = value; }
    public String getMatchedClientKey() { return matchedClientKey; }
    public void setMatchedClientKey(String value) { matchedClientKey = value; }
    public Integer getMatchedIndex() { return matchedIndex; }
    public void setMatchedIndex(Integer value) { matchedIndex = value; }
    public Integer getResponseCode() { return responseCode; }
    public void setResponseCode(Integer value) { responseCode = value; }
    public String getBodyType() { return bodyType; }
    public void setBodyType(String value) { bodyType = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public String getMode() { return mode; }
    public void setMode(String value) { mode = value; }
    public int getEnabledCount() { return enabledCount; }
    public void setEnabledCount(int value) { enabledCount = value; }
    public boolean isUsingSavedConfiguration() { return usingSavedConfiguration; }
    public void setUsingSavedConfiguration(boolean value) { usingSavedConfiguration = value; }
    public List<Candidate> getCandidates() { return candidates; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Candidate {
        private final String responseId;
        private final String responseName;
        private final String clientKey;
        private final int index;
        private final int responseCode;
        private final String bodyType;
        public Candidate(String responseId, String responseName, String clientKey,
                         int index, int responseCode, String bodyType) {
            this.responseId = responseId;
            this.responseName = responseName;
            this.clientKey = clientKey;
            this.index = index;
            this.responseCode = responseCode;
            this.bodyType = bodyType;
        }
        public String getResponseId() { return responseId; }
        public String getResponseName() { return responseName; }
        public String getClientKey() { return clientKey; }
        public int getIndex() { return index; }
        public int getResponseCode() { return responseCode; }
        public String getBodyType() { return bodyType; }
    }
}
