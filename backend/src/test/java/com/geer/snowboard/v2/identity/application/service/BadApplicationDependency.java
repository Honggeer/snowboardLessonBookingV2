package com.geer.snowboard.v2.identity.application.service;

import java.sql.Connection;

/** Deliberately invalid test fixture: application services cannot use JDBC. */
public class BadApplicationDependency {
    Connection connection;
}
