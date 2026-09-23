package com.codgo.ulock.audit;

/** What an audited action was performed on. */
public record AuditTarget(String type, String id) {

    public static final String TENANT = "TENANT";
    public static final String USER = "USER";
    public static final String ROLE = "ROLE";
    public static final String PERMISSION = "PERMISSION";
    public static final String MACHINE_CLIENT = "MACHINE_CLIENT";

    public static AuditTarget of(String type, Object id) {
        return new AuditTarget(type, id == null ? null : id.toString());
    }
}
