package com.rackguard.rackguardserver.model;

public record User(Integer userId, String name, String email, String passwordHash,
                   String role, String rfidTag, boolean isActive) {}
