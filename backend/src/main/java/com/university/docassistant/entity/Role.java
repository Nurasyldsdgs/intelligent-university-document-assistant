package com.university.docassistant.entity;

/**
 * Names already carry Spring Security's "ROLE_" prefix, so {@code role.name()} is used as the authority
 * directly and {@code hasRole('ADMIN')} matches {@code ROLE_ADMIN}.
 */
public enum Role {
    ROLE_STUDENT,
    ROLE_TEACHER,
    ROLE_ADMIN
}
