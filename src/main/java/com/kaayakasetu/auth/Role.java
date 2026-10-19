package com.kaayakasetu.auth;

/** The two kinds of account. Spring Security turns these into the authorities ROLE_WORKER / ROLE_EMPLOYER. */
public enum Role {
    WORKER,
    EMPLOYER
}
