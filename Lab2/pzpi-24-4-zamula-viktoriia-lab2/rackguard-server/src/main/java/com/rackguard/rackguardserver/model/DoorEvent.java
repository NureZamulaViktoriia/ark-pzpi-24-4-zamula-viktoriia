package com.rackguard.rackguardserver.model;

import java.time.LocalDateTime;
public record DoorEvent(Integer eventId, Integer doorId, Integer userId, Integer requestId,
                        String eventType, LocalDateTime eventTime, boolean isAnomaly, String note) {}