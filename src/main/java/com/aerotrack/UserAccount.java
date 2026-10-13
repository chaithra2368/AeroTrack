package com.aerotrack;

/** Small model object: stores the authenticated user's identity and role. */
public record UserAccount(String username, String role) {
    public boolean isAdministrator() { return "ADMINISTRATOR".equals(role); }
    public boolean canOperate() {
        return isAdministrator() || "OPERATOR".equals(role);
    }
    public boolean canAnalyze() {
        return canOperate() || "ANALYST".equals(role);
    }
}
