package com.mockhub.auth.model;

/** Only displayName is writable; username, roles and status are never accepted as profile updates. */
public class UpdateProfileRequest {
    private String displayName;
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}
