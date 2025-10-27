package fr.epita.assistants.ping.domain.executor;

import fr.epita.assistants.ping.dto.ProjectDTOs.RunCommand;
import fr.epita.assistants.ping.utils.Logger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class GitFeatureExecutor implements FeatureExecutor {

    @Inject
    Logger logger;

    @Override
    public String name() {
        return "git";
    }

    @Override
    public void execute(File projectRoot, Object request) {
        if (!(request instanceof RunCommand)) {
            logger.error("GitFeatureExecutor received invalid request type: " + request.getClass().getName());
            throw new IllegalArgumentException("Invalid request type for git executor");
        }

        RunCommand runCommand = (RunCommand) request;// assume its correct
        
        logger.info("Executing git command: " + runCommand.command + " in  " + projectRoot.getAbsolutePath());

        try {
            switch (runCommand.command.toLowerCase()) {
                case "init":
                    executeGitInit(projectRoot);
                    break;
                case "add":
                    executeGitAdd(projectRoot, runCommand.params);
                    break;
                case "commit":
                    executeGitCommit(projectRoot, runCommand.params);
                    break;
                default:
                    logger.error("unknown git command: " + runCommand.command);
                    throw new IllegalArgumentException("unknown git command: " + runCommand.command);
            }
            
            logger.info("Git command executed , success!: " + runCommand.command);
            
        } catch (Exception e) {
            logger.error("Git command failed: ," + e.getMessage());
            throw new RuntimeException("Git command execution failed: " + e.getMessage(), e);
        }
    }

    private void executeGitInit(File projectRoot) throws IOException, InterruptedException {
        logger.debug("Inits git repo in: " + projectRoot.getAbsolutePath());
        
        List<String> command = List.of("git", "init");
        executeCommand(command, projectRoot);
    }

    private void executeGitAdd(File projectRoot, List<String> params) throws IOException, InterruptedException {
        if (params == null || params.isEmpty()) {
            logger.error("Git add command needs file parameters");
            throw new IllegalArgumentException("Git add needs file parameters");
        }

        logger.debug("Adding files to git: " +  params + " in: " + projectRoot.getAbsolutePath());
        
        // check files exist
        for (String param : params) {
            if (param.contains("..") || param.startsWith("/")) {
                logger.error("Invalid file path  " + param);
                throw new IllegalArgumentException("Invalid file path: " + param);
            }
        }

        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("add");
        command.addAll(params);
        
        executeCommand(command, projectRoot);
    }

    private void executeGitCommit(File projectRoot, List<String> params) throws IOException, InterruptedException {
        if (params == null || params.isEmpty()) {
            logger.error("Git commit command requires message parameter");
            throw new IllegalArgumentException("Git commit requires a message");
        }

        String message = String.join(" ", params);
        logger.debug("Committing with message: '" + message + "' in: " + projectRoot.getAbsolutePath());
        
        List<String> command = List.of("git", "commit", "-m", message);
        executeCommand(command, projectRoot);
    }

    private void executeCommand(List<String> command, File workingDir) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDir);
        processBuilder.redirectErrorStream(true);

        logger.debug("Executing command: " + command  + " in  " + workingDir.getAbsolutePath());

        Process process = processBuilder.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            logger.error("Git command failed with " + exitCode + ": " + String.join(" ", command));
            throw new RuntimeException("Git command failed with: " + exitCode);
        }
        logger.debug("Command executed with success");
    }
}