package fr.epita.assistants.ping.utils;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class Logger {
    private static final String RESET_TEXT = "\u001B[0m";
    private static final String RED_TEXT = "\u001B[31m";
    private static final String GREEN_TEXT = "\u001B[32m";
    private static final String YELLOW_TEXT = "\u001B[33m";
    private static final String BLUE_TEXT = "\u001B[34m";

    @ConfigProperty(name = "LOG_FILE")
    Optional<String> logFile;

    @ConfigProperty(name = "ERROR_LOG_FILE")
    Optional<String> errorLogFile;

    private static String timestamp() {
        return new SimpleDateFormat("dd/MM/yy - HH:mm:ss")
                .format(Calendar.getInstance().getTime());
    }

    public void info(String message) {
        String logMessage = GREEN_TEXT + "[" + timestamp() + "] INFO: " + message + RESET_TEXT;
        System.out.println(logMessage);
        
        if (logFile.isPresent() && !logFile.get().trim().isEmpty()) {
            writeToFile(logFile.get(), logMessage);
        }
    }

    public void error(String message) {
        String logMessage = RED_TEXT + "[" + timestamp() + "] ERROR: " + message + RESET_TEXT;
        System.err.println(logMessage);
        
        if (errorLogFile.isPresent() && !errorLogFile.get().trim().isEmpty()) {
            writeToFile(errorLogFile.get(), logMessage);
        }
    }

    public void warn(String message) {
        String logMessage = YELLOW_TEXT + "[" + timestamp() + "] WARN: " + message + RESET_TEXT;
        System.out.println(logMessage);
        
        if (logFile.isPresent() && !logFile.get().trim().isEmpty()) {
            writeToFile(logFile.get(), logMessage);
        }
    }

    public void debug(String message) {
        String logMessage = BLUE_TEXT + "[" + timestamp() + "] DEBUG: " + message + RESET_TEXT;
        System.out.println(logMessage);
        
        if (logFile.isPresent() && !logFile.get().trim().isEmpty()) {
            writeToFile(logFile.get(), logMessage);
        }
    }

    private void writeToFile(String filename, String message) {
        try (FileWriter fw = new FileWriter(filename, true);
             PrintWriter pw = new PrintWriter(fw)) {
            String cleanMessage = message.replaceAll("\u001B\\[[;\\d]*m", "");
            pw.println(cleanMessage);
        } catch (IOException e) {
            String cleanMessage = message.replaceAll("\u001B\\[[;\\d]*m", "");
            if (filename.equals(errorLogFile.orElse(""))) {
                System.err.println("Failed to write to error log file: " + e.getMessage());
                System.err.println(cleanMessage);
            } else {
                System.out.println("Failed to write to log file: " + e.getMessage());
                System.out.println(cleanMessage);
            }
        }
    }

    public void logProjectOperation(String userId, String projectId, String operation, String details) {
        String message = String.format("Project Operation - User: %s, Project: %s, Operation: %s, Details: %s", 
            userId, projectId, operation, details);
        info(message);
    }
}