package com.rackguard.rackguardserver.model;

public record Door(Integer doorId, Integer roomId, String name, String esp32DeviceId) {}
