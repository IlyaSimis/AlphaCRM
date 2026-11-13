package ru.quietclinic.billing;

record TerminalEventView(boolean accepted, boolean duplicate, String paymentStatus) {}
