package fr.epita.assistants.ping.domain.executor;

import java.io.File;

public interface FeatureExecutor {
    /**
     * tool name for ex git
     */
    String name();

    void execute(File projectRoot, Object request);
}