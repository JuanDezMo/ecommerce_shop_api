package com.shoplab.api.user;

/** Estado de la cuenta. Un usuario LOCKED no puede hacer login (423). */
public enum UserStatus {
    ACTIVE,
    LOCKED
}
