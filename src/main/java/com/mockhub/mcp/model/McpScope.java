package com.mockhub.mcp.model;

/** Deliberately excludes platform administration, team administration and permanent resource purge. */
public enum McpScope {
    API_READ(false), STATS_READ(false), AI_GENERATE(false),
    DRAFT_CREATE(true), DRAFT_UPDATE(true), DRAFT_APPLY(true);
    private final boolean write;
    McpScope(boolean write) { this.write=write; }
    public boolean requiresWrite() { return write; }
}
