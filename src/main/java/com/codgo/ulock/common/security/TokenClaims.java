package com.codgo.ulock.common.security;

/** Claim names carried by uLock-issued access tokens. */
public final class TokenClaims {

    /** Tenant of the authenticated user. */
    public static final String TENANT_ID = "tid";
    /** Names of the user's roles. */
    public static final String ROLES = "roles";
    /** Present only on machine-client tokens. */
    public static final String CLIENT_ID = "client_id";
    public static final String SCOPE = "scope";

    /** Scope granted to machine clients for calling the authorization check. */
    public static final String AUTHZ_CHECK_SCOPE = "authz:check";

    private TokenClaims() {}
}
