package dev.visherryz.vigeyserupdater.install;

@FunctionalInterface
public interface FaultInjector {
    FaultInjector NONE = checkpoint -> {};
    void checkpoint(String checkpoint) throws Exception;
}
