package fr.epita.assistants.ping.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import fr.epita.assistants.ping.data.model.ProjectModel;
import fr.epita.assistants.ping.data.model.UserModel;
import fr.epita.assistants.ping.utils.Logger;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ProjectRepository implements PanacheRepository<ProjectModel> {

    @Inject
    Logger logger;

    @Transactional
    public ProjectModel saveProject(String name, UserModel owner, String basePath) 
    {
        if (owner == null) {
            logger.error("saveProject called with null owner");
            throw new IllegalArgumentException("Owner cannot be null");
        }
        logger.info("Repository: Creating new project with name: " + name + ", owner: " + owner.getLogin());
        UserModel managedOwner = getEntityManager().merge(owner); // be sure its managed here

        ProjectModel project = new ProjectModel(name, managedOwner);
        persist(project);

        if (basePath != null && !basePath.trim().isEmpty()) {
            //basePath ends with /
            String normalizedBasePath = basePath.endsWith("/") ? basePath : basePath + "/";
            project.path = normalizedBasePath + project.id.toString();
            logger.debug("Repository: Setting project path to: " + project.path);


            // Merge the updated project with the path
            project = getEntityManager().merge(project);
            
            // createe directory immediately after setting the path
            java.io.File projectDir = new java.io.File(project.path);
            if (!projectDir.exists()) {
                boolean created = projectDir.mkdirs();
                logger.info("Repository: Created project directory: " + project.path + " - success: " + created);
            }
        } else {
            logger.warn("Repository: basePath is null or empty, path not set");
        }

        logger.info("Repository: Project persisted successfully with ID: " + project.id + 
                   ", name: " + project.name + ", path: " + project.path);
        return project;
    }

    @Transactional
    public Optional<ProjectModel> findProjectById(UUID id) {
        logger.debug("Repository: Searching for project with ID: " + id);
        Optional<ProjectModel> result = find("id", id).firstResultOptional();
        if (result.isPresent()) {
            logger.debug("Repository: Project found with ID: " + id + 
                        ", name: " + result.get().name);
        } else {
            logger.debug("Repository: No project found with ID: " + id);
        }
        return result;
    }

    // this is where it gets interesting - finding projects based on user access
    @Transactional
    public List<ProjectModel> findProjectsByUser(UserModel user, boolean onlyOwned) {
        logger.debug("Repository: Finding projects for user: " + user.getLogin() + 
                    ", onlyOwned: " + onlyOwned);
        
        if (onlyOwned) {
            // simple case just projects this user owns
            List<ProjectModel> projects = find("owner", user).list();//finds all projects a user can acces qs owner or member
            logger.debug("Repository: Found " + projects.size() + " owned projects for user: " + user.getLogin());
            return projects;
        } else {
            // trickier case projects where user is owner OR member (using JPQL magic)
            List<ProjectModel> projects = find("owner = ?1 OR ?1 MEMBER OF members", user).list();
            logger.debug("Repository: Found " + projects.size() + " accessible projects for user: " + user.getLogin());
            return projects;
        }
    }

    // admin function to get all projects (careful with this one!)
    @Transactional
    public List<ProjectModel> listAllProjects() {
        logger.info("Repository: Retrieving all projects (admin function)");
        List<ProjectModel> projects = listAll();
        logger.info("Repository: Retrieved " + projects.size() + " total projects");
        return projects;
    }


    @Transactional
    public boolean addMemberToProject(UUID projectId, UserModel user) {
        if (user == null) {
            logger.error("addMemberToProject called with null user");
            throw new IllegalArgumentException("User cannot be null");
        }
        logger.info("Repository: Adding user " + user.getLogin() + " to project " + projectId);
        Optional<ProjectModel> projectOpt = findProjectById(projectId);
        if (projectOpt.isPresent()) 
        {

            ProjectModel project = projectOpt.get();
            if (project.members.stream().anyMatch(member -> member.getId().equals(user.getId()))) 
            {
                logger.warn("Repository: User " + user.getLogin() + " already member of project " + project.name);
                return false; // already a member
            }


            project.members.add(user);
            logger.info("Repository: Successfully added " + user.getLogin() + " to project " + project.name);
            return true;
        }
        logger.error("Repository: Project not found when adding member: " + projectId);
        return false;
    }

    // removing a user from project members (but owner can't be removed)
    @Transactional
    public boolean removeMemberFromProject(UUID projectId, UserModel user) {
        logger.info("Repository: Removing user " + user.getLogin() + " from project " + projectId);
        Optional<ProjectModel> projectOpt = findProjectById(projectId);
        if (projectOpt.isPresent()) {
            ProjectModel project = projectOpt.get();
            if (project.owner.equals(user)) {
                logger.warn("Repository: can't remove project owner " + user.getLogin() + " from project " + project.name);
                return false; // can't remove owner
            }
            boolean removed = project.members.removeIf(member -> member.getId().equals(user.getId()));
            if (removed) {
                logger.info("Repository: Successfully removed " + user.getLogin() + " from project " + project.name);
            } else {
                logger.warn("Repository: User " + user.getLogin() + " was not a member of project " + project.name);
            }
            return removed;
        }
        logger.error("Repository: Project not found when removing member: " + projectId);
        return false;
    }

    // deleting a project completely (this will also clean up the members table automatically)
    @Transactional
    public boolean removeProject(UUID id) {
        logger.info("Repository: Attempting to remove project with ID: " + id);
        Optional<ProjectModel> projectOpt = findProjectById(id);
        if (projectOpt.isPresent()) {
            ProjectModel project = projectOpt.get();
            logger.info("Repository: Project found, deleting project: " + project.name + 
                       " (owner: " + project.owner.getLogin() + ")");
            delete(project);
            //TODO: double check it's actually gone
            boolean deleted = findProjectById(id).isEmpty();
            if (deleted) {
                logger.info("Repository: Project successfully deleted with ID: " + id);
            } else {
                logger.error("Repository: Failed to delete project with ID: " + id);
            }
            return deleted;
        } else {
            logger.warn("Repository: Attempted to delete non-existent project with ID: " + id);
            return false;
        }
    }

    // updating project info (name and/or owner changes)
    @Transactional
    public ProjectModel modifyProject(ProjectModel project, String newName, UserModel newOwner) {
        logger.info("Repository: Modifying project " + project.name + " (ID: " + project.id + ")");
        
        if (newName != null && !newName.isBlank()) {
            logger.debug("Repository: Updating project name from '" + project.name + "' to '" + newName + "'");
            project.name = newName;
        }
        
        if (newOwner != null && !newOwner.equals(project.owner)) {
            logger.debug("Repository: Changing project owner from " + project.owner.getLogin() + 
                        " to " + newOwner.getLogin());
            project.members.add(newOwner); // the owened is automatically a member
            project.owner = newOwner;
        }
        
        ProjectModel result = getEntityManager().merge(project);
        logger.info("Repository: Project modified successfully: " + project.name);
        return result;
    }

    // quick helper to count total projects
    @Transactional
    public long countAllProject() {
        logger.debug("Repository: counting all the  projects");
        long count = count();
        logger.debug("Repository: Total projects is " + count);
        return count;
    }
}