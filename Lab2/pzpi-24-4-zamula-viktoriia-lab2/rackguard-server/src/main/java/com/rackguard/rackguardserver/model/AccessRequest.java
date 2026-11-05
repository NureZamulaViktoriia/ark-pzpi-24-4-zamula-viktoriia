package com.rackguard.rackguardserver.model;

import java.time.LocalDateTime;
public record AccessRequest(Integer requestId, Integer userId, Integer roomId, Integer approverId,
                            String purpose, LocalDateTime startTime, LocalDateTime endTime, String status) {}


