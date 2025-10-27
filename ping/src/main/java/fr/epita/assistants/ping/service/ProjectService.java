package fr.epita.assistants.ping.service;

import java.util.List;
import java.util.UUID;

import fr.epita.assistants.ping.data.model.ProjectModel;
import fr.epita.assistants.ping.data.model.UserModel;
import fr.epita.assistants.ping.repository.ProjectRepository;
import fr.epita.assistants.ping.utils.Logger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ProjectService {

    @Inject
    ProjectRepository projectRepo;
    @Inject
    UserService userService;
    @Inject
    Logger logger;

    @ConfigProperty(name = "PROJECT_DEFAULT_PATH", defaultValue = "/tmp/ping")
    String defaultProjectPath;

    public ProjectModel getProjectById(UUID id) {
        if (id == null) {
            logger.error("getProjectById called with null id parameter");
            return null;
        }
        return projectRepo.findProjectById(id).orElse(null);
    }

    @Transactional
    public ProjectModel createProject(String name, UserModel owner) {
        if (name == null || owner == null) {
            logger.error("createProject called with null parameter(s): name=" + name + ", owner=" + (owner != null ? owner.getLogin() : "null"));
            return null;
        }
        
        // Ensure name is not empty after trimming
        if (name.trim().isEmpty()) {
            logger.error("createProject called with empty name after trimming");
            return null;
        }
        
        try {
            // Use configurable base path - defaulting to /tmp/ping for tests
            String basePath = defaultProjectPath;
            logger.info("Service: Using base path: " + basePath + " for project creation");

            logger.info("Service: Creating project '" + name + "' for owner: " + owner.getLogin());
            
            ProjectModel newproj = projectRepo.saveProject(name.trim(), owner, basePath);
            
            if (newproj == null) {
                logger.error("Repository returned null project");
                return null;
            }

            logger.info("Service: Project created with ID: " + newproj.id + ", path: " + newproj.path);

            // Create project directory if path is set
            if (newproj.path != null && !newproj.path.trim().isEmpty()) {
                java.io.File projectDir = new java.io.File(newproj.path);
                if (!projectDir.exists()) {
                    boolean created = projectDir.mkdirs();
                    if (created) {
                        logger.info("Created project directory: " + newproj.path);
                    } else {
                        logger.error("Failed to create project directory: " + newproj.path);
                        // Continue execution - don't fail project creation for directory issues
                    }
                } else {
                    logger.info("Project directory already exists: " + newproj.path);
                }
            } else {
                logger.warn("Project path is null or empty, skipping directory creation");
            }
            
            return newproj;
            
        } catch (Exception e) {
            logger.error("Exception in createProject: " + e.getMessage());
            return null;
        }
    }

    @Transactional
    public ProjectModel modifyProject(ProjectModel project, String newName, UserModel newOwner) {
        if (project == null) {
            logger.error("modifyProject called with null project parameter");
            return null;
        }
        
        return projectRepo.modifyProject(project, newName, newOwner);
    }

    @Transactional
    public List<ProjectModel> getProjectsByUser(UserModel user, boolean isOwner) {
        if (user == null) {
            logger.error("getProjectsByUser called with null user parameter");
            return List.of();
        }
        return projectRepo.findProjectsByUser(user, isOwner);
    }

    // Add missing method for projects owned by user
    public List<ProjectModel> getProjectsByOwner(UserModel user) {
        if (user == null) {
            logger.error("getProjectsByOwner called with null user parameter");
            return List.of();
        }
        return projectRepo.findProjectsByUser(user, true); // true = only owned projects
    }

    // Add missing method for all projects accessible to user
    public List<ProjectModel> getProjectsForUser(UserModel user) {
        if (user == null) {
            logger.error("getProjectsForUser called with null user parameter");
            return List.of();
        }
        return projectRepo.findProjectsByUser(user, false); // false = all accessible projects (owned + member)
    }

    public List<ProjectModel> getAllProjects() {
        return projectRepo.listAllProjects();
    }
}