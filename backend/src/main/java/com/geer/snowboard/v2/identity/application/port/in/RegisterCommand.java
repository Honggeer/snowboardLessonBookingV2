package com.geer.snowboard.v2.identity.application.port.in;

public record RegisterCommand(String name, String level, String email, String password) {}
