package dev.visherryz.vigeyserupdater;

public record UpdateStatus(String id, State state, String detail) {
    public enum State { UNKNOWN, CHECKING, UP_TO_DATE, AVAILABLE, INSTALLING, INSTALLED, FAILED }
}
