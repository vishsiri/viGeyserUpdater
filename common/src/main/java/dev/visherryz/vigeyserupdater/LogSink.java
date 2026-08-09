package dev.visherryz.vigeyserupdater;

public interface LogSink {
    void info(String message);
    void warn(String message);
    void error(String message, Throwable error);
}
