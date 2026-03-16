package com.ai.learningdetection.security;

/**
 * Implemented by both TeacherUserDetails and ParentUserDetails
 * so controllers can extract the entity ID without reflection or casting.
 */
public interface IdentifiablePrincipal {
    String getId();
    String getName();
}

