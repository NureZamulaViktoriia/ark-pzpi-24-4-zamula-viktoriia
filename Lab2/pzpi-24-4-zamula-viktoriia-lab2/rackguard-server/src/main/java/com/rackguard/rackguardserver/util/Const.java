package com.rackguard.rackguardserver.util;

import java.util.Set;

public final class Const {
    private Const() {}

    public static final String ADMIN = "Admin", VISITOR = "Visitor";

    public static final String CREATED = "Created", APPROVED = "Approved", REJECTED = "Rejected",
            CANCELED = "Canceled", EXPIRED = "Expired", COMPLETED = "Completed", CONFIRMED = "ConfirmedLocally";

    public static final String OPENED = "DoorOpened", CLOSED = "DoorClosed", TAMPER = "Tamper",
            TEMPERATURE = "Temperature", EXIT_BUTTON = "ExitButton", COMMAND_ISSUED = "CommandIssued",
            RFID_GRANTED = "RfidGranted", RFID_DENIED = "RfidDenied";

    public static final Set<String> DEVICE_EVENTS =
            Set.of(OPENED, CLOSED, "ReedState", TAMPER, TEMPERATURE, "Failure", EXIT_BUTTON);

    public static final int OPEN_TOO_LONG_SECONDS = 60;      // BR-13
    public static final int AUTHORIZATION_WINDOW_SECONDS = 15;
}
