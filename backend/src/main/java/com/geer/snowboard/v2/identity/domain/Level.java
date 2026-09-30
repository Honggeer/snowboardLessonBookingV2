package com.geer.snowboard.v2.identity.domain;

public enum Level {
    BEGINNER("零基础"), NOVICE("入门"), ADVANCED("进阶");

    private final String label;

    Level(String label) { this.label = label; }

    public String label() { return label; }

    public static Level fromLabel(String label) {
        for (Level level : values()) {
            if (level.label.equals(label)) return level;
        }
        throw new IllegalArgumentException("Invalid level");
    }
}
