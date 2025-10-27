package fr.epita.assistants.ping.presentation.rest;

import java.io.File;
import java.util.List;
import java.util.UUID;

import fr.epita.assistants.ping.data.model.ProjectModel;
import fr.epita.assistants.ping.data.model.UserModel;
import fr.epita.assistants.ping.domain.executor.GitFeatureExecutor;
import fr.epita.assistants.ping.dto.ProjectDTOs;
import fr.epita.assistants.ping.dto.ProjectDTOs.AddUser;
import fr.epita.assistants.ping.dto.ProjectDTOs.CreateProject;
import fr.epita.assistants.ping.dto.ProjectDTOs.ProjectInfo;
import fr.epita.assistants.ping.dto.ProjectDTOs.RemoveUser;
import fr.epita.assistants.ping.dto.ProjectDTOs.RunCommand;
import fr.epita.assistants.ping.dto.ProjectDTOs.UpdateProject;
import fr.epita.assistants.ping.repository.ProjectRepository;
import fr.epita.assistants.ping.service.ProjectService;
import fr.epita.assistants.ping.service.UserService;
import fr.epita.assistants.ping.utils.Logger;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api/projects") 
@Produces(MediaType.APPLICATION_JSON) // if you want to know what this does 
@Consumes(MediaType.APPLICATION_JSON) // go to UserRessource.java

public class ProjectResource {

    @Inject
    UserService userService;

    @Inject
    ProjectRepository projectRepo;

    @Inject
    ProjectService projectService;

    @Inject
    Logger logger;

    @Context
    SecurityContext securityContext;

    @Inject
    JsonWebToken jwt;

    @Inject
    GitFeatureExecutor gitExecutor; 

    @GET 
    @RolesAllowed({"admin", "user"})
    @Transactional
    public Response getProjects(@QueryParam("onlyOwned") Boolean onlyOwned) {
        // @QueryParam is used to get the value of the query parameter from the URL (here we get boolean)
        
        if (onlyOwned == null) {
            onlyOwned = false; 
        }
        String userInfo = getCurrentUserInfo();
        logger.info("GET /api/projects - Request from: " + userInfo + ", onlyOwned: " + onlyOwned);

        try {
            if (securityContext == null || securityContext.getUserPrincipal() == null) {
                logger.error("Unauthorized access attempt from: " + userInfo);
                return Response.status(Response.Status.UNAUTHORIZED).entity(java.util.Map.of("message", "Unauthorized")).build();
            }

            String currentUserIdStr = jwt.getSubject(); // Get UUID from JWT 'sub' claim
            UUID currentUserId = UUID.fromString(currentUserIdStr);
            UserModel currentUser = userService.getUserById(currentUserId);
            
            if (currentUser == null) {
                logger.error("User not found: " + currentUserId);
                return Response.status(Response.Status.UNAUTHORIZED).entity(java.util.Map.of("message", "User not found")).build();
            }

            List<ProjectModel> projects = projectService.getProjectsByUser(currentUser, onlyOwned);
            // this fetches the proj using service
            
            List<ProjectInfo> projectInfos = projects.stream()
                .map(ProjectDTOs::makeProjectInfo)
                .toList();

            logger.info("Successfully retrieved " + projects.size() + " projects for user: " + currentUser.getLogin());
            return Response.ok(projectInfos).build();

        } catch (Exception e) {
            logger.error("Error retrieving projects for user: " + userInfo + " - " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(java.util.Map.of("message", "Internal server error")).build();
        }
    }

    @POST 
    @RolesAllowed({"admin", "user"})
    @Transactional
    public Response createProject(CreateProject createRequest) {
        String userInfo = getCurrentUserInfo();
        logger.info("POST /api/projects - Create project request from: " + userInfo);
    
        try {
            if (createRequest == null || createRequest.name == null || createRequest.name.trim().isEmpty()) {
                logger.error("Invalid project creation request from: " + userInfo);
                return Response.status(Response.Status.BAD_REQUEST).entity(java.util.Map.of("message", "Project name is required")).build();
            }

            if (securityContext == null || securityContext.getUserPrincipal() == null) {
                logger.error("Unauthorized project creation attempt from: " + userInfo);
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("message", "Unauthorized")).build();
            }
    
            String currentUserIdStr = jwt.getSubject(); // Get UUID from JWT 'sub' claim
            UUID currentUserId = UUID.fromString(currentUserIdStr);
            UserModel currentUser = userService.getUserById(currentUserId);
            
            if (currentUser == null) {
                logger.error("User not found during project creation: " + currentUserIdStr);
                return Response.status(Response.Status.UNAUTHORIZED).entity(java.util.Map.of("message", "User not found")).build();
            }
    
            ProjectModel newProject = projectService.createProject(createRequest.name.trim(), currentUser);
            
            if (newProject == null) {
                logger.error("Failed to create project for user: " + currentUser.getLogin());
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(java.util.Map.of("message", "Failed to create project")).build();
            }
    
            ProjectInfo projectInfo = ProjectDTOs.makeProjectInfo(newProject);
            
            logger.info("Successfully created project: " + newProject.name + " (ID: " + newProject.id + ") for user: " + 
            currentUser.getLogin());
            
            return Response.ok(projectInfo).build();
    
        } catch (Exception e) {
            logger.error("Error creating project for user: " + userInfo + " - " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(java.util.Map
            .of("message", "Internal server error")).build();
        }
    }

    @GET
    @Path("/all")
    @RolesAllowed({"admin"})
    @Transactional
    public Response getAllProjects() {
        String userInfo = getCurrentUserInfo();
        logger.info("GET /api/projects/all - Request from: " + userInfo);
    
        try {
            if (securityContext == null || securityContext.getUserPrincipal() == null) {
                logger.error("Unauthorized access attempt to /all from: " + userInfo);
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("message", "Unauthorized")).build();
            }
    
            String currentUserIdStr = jwt.getSubject(); // Get UUID from JWT 'sub' claim
            UUID currentUserId = UUID.fromString(currentUserIdStr);
            UserModel currentUser = userService.getUserById(currentUserId);
            
            if (currentUser == null) {
                logger.error("User not found for /all request: " + currentUserIdStr + " from: " + userInfo);
                return Response.status(Response.Status.UNAUTHORIZED).entity(java.util.Map.of("message", "User not found")).build();
            }
    
            if (!currentUser.getIsAdmin()) {
                logger.warn("Non-admin user attempted to access /all: " + currentUser.getLogin());
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(java.util.Map.of("message", "Admin access required")).build();
            }
    
            List<ProjectModel> projects = projectService.getAllProjects();
            List<ProjectInfo> projectInfos = projects.stream().map(ProjectDTOs::makeProjectInfo).toList();
            
            logger.info("Successfully retrieved all " + projects.size() + " projects for admin: " + currentUser.getLogin());
            return Response.ok(projectInfos).build();
    
        } catch (Exception e) {
            logger.error("Error retrieving all projects for admin: " + userInfo + " - " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(java.util.Map.of("message", "Internal server error")).build();
        }
    }

    @PUT
    @Path("/{id}")
    // Only admin and owner of the project can update it
    @RolesAllowed({"admin", "user"})
    @Transactional
    public Response updateProject(@PathParam("id") String projectIdStr, UpdateProject updateRequest) {
        String userInfo = getCurrentUserInfo();
        logger.info("PUT /api/projects/" + projectIdStr + " - Update request from: " + userInfo);

        try {
            if (updateRequest == null) {
                logger.error("PUT /api/projects/" + projectIdStr + " - Update request is null from: " + userInfo);
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of("message", "Update request cannot be null")).build();
            }

            if (updateRequest.name == null && updateRequest.newOwnerId == null) {
                logger.error("PUT /api/projects/" + projectIdStr + " - Both name and newOwnerId are null from: " + userInfo);
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of("message", "Both name and new owner are null")).build();
            }

            UUID projectId = UUID.fromString(projectIdStr);
            
            if (securityContext == null || securityContext.getUserPrincipal() == null) {
                logger.error("Unauthorized update attempt for project " + projectId + " by: " + userInfo);
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("message", "Unauthorized")).build();
            }

            String currentUserIdStr = jwt.getSubject(); // Get UUID from JWT 'sub' claim
            UUID currentUserId = UUID.fromString(currentUserIdStr);
            UserModel currentUser = userService.getUserById(currentUserId);
            
            if (currentUser == null) {
                logger.error("User not found during project update: " + currentUserIdStr);
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("message", "User not found")).build();
            }

            ProjectModel project = projectService.getProjectById(projectId);
            if (project == null) {
                logger.error("Project with ID " + projectId + " not found for update by: " + userInfo);
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(java.util.Map.of("message", "Project not found")).build();
            }

            boolean isAdmin = currentUser.getIsAdmin();
            boolean isOwner = project.owner.getId().equals(currentUser.getId());

            if (!isAdmin && !isOwner) {
                logger.warn("Unauthorized project update attempt - User: " + currentUser.getLogin() + 
                           " is not owner or admin for project: " + project.name + " (ID: " + projectId + ")");
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(java.util.Map.of("message", "Only project owner or admin can update project")).build();
            }

            UserModel newOwner = null;
            if (updateRequest.newOwnerId != null) {
                newOwner = userService.getUserById(updateRequest.newOwnerId);
                if (newOwner == null) {
                    logger.error("New owner with ID " + updateRequest.newOwnerId + " not found during project update by: " + userInfo);
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(java.util.Map.of("message", "New owner not found")).build();
                }
                logger.info("Project update - changing owner from " + project.owner.getLogin() + 
                           " to " + newOwner.getLogin() + " for project: " + project.name);
            }

            String newName = (updateRequest.name != null && !updateRequest.name.trim().isEmpty()) 
                ? updateRequest.name.trim() : null;
            
            ProjectModel updatedProject = projectRepo.modifyProject(project, newName, newOwner);
            
            ProjectInfo projectInfo = ProjectDTOs.makeProjectInfo(updatedProject);
            
            logger.info("Project updated successfully - ID: " + projectId + 
                       ", Updated by: " + currentUser.getLogin() + 
                       ", New name: " + (newName != null ? newName : "unchanged") +
                       ", New owner: " + (newOwner != null ? newOwner.getLogin() : "unchanged"));
            
            return Response.ok(projectInfo).build();

        } catch (IllegalArgumentException e) {
            logger.error("Invalid project ID format: " + projectIdStr + " from: " + userInfo);
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(java.util.Map.of("message", "Invalid project ID format")).build();
        } catch (Exception e) {
            logger.error("Error updating project " + projectIdStr + " by " + userInfo + ": " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(java.util.Map.of("message", "Internal server error")).build();
        }
    }

    @DELETE
    @Path("/{id}")
    // admin and owner of the project can delete it
    @RolesAllowed({"admin", "user"})
    @Transactional
    public Response deleteProject(@PathParam("id") String projectIdStr) {
        String userInfo = getCurrentUserInfo();
        logger.info("DELETE /api/projects/" + projectIdStr + " - Delete request from: " + userInfo);

        try {
            UUID projectId = UUID.fromString(projectIdStr);
            
            if (securityContext == null || securityContext.getUserPrincipal() == null) {
                logger.error("Unauthorized delete attempt for project " + projectId + " by: " + userInfo);
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("message", "Unauthorized")).build();
            }

            String currentUserIdStr = jwt.getSubject(); // Get UUID from JWT 'sub' claim
            UUID currentUserId = UUID.fromString(currentUserIdStr);
            UserModel currentUser = userService.getUserById(currentUserId);
            
            if (currentUser == null) {
                logger.error("User not found during project deletion: " + currentUserIdStr);
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("message", "User not found")).build();
            }

            ProjectModel project = projectService.getProjectById(projectId);
            if (project == null) {
                logger.error("Project with ID " + projectId + " not found for deletion by: " + userInfo);
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(java.util.Map.of("message", "Project not found")).build();
            }

            boolean isAdmin = currentUser.getIsAdmin();
            boolean isOwner = project.owner.getId().equals(currentUser.getId());

            if (!isAdmin && !isOwner) {
                logger.warn("Unauthorized project deletion attempt - User: " + currentUser.getLogin() + 
                           " is not owner or admin for project: " + project.name + " (ID: " + projectId + ")");
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(java.util.Map.of("message", "Only project owner or admin can delete project")).build();
            }

            String projectName = project.name;
            String ownerLogin = project.owner.getLogin();

            boolean deleted = projectRepo.removeProject(projectId);
            
            if (!deleted) {
                logger.error("Failed to delete project " + projectName + " (ID: " + projectId + ") by: " + userInfo);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(java.util.Map.of("message", "Failed to delete project")).build();
            }

            logger.info("Project deleted successfully - Name: " + projectName + 
                       " (ID: " + projectId + "), Owner: " + ownerLogin + 
                       ", Deleted by: " + currentUser.getLogin());
            
            // Return 204 No Content
            return Response.noContent().build();

        } catch (IllegalArgumentException e) {
            logger.error("Invalid project ID format: " + projectIdStr + " from: " + userInfo);
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(java.util.Map.of("message", "Invalid project ID format")).build();
        } catch (Exception e) {
            logger.error("Error deleting project " + projectIdStr + " by " + userInfo + ": " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(java.util.Map.of("message", "Internal server error")).build();
        }
    }

    @GET 
    @Path("/{id}")
    // Get a specific project based on its id.
    // A user can access this endpoint if he is a member of the project or the owner.
    // An admin can access this endpoint in any case.
    @RolesAllowed({"admin", "user"})
    @Transactional
    public Response getProjectById(@PathParam("id") String projectIdStr) {
        String userInfo = getCurrentUserInfo();
        logger.info("GET /api/projects/" + projectIdStr + " - Request from: " + userInfo);

        try {
            UUID projectId = UUID.fromString(projectIdStr);
            
            if (securityContext == null || securityContext.getUserPrincipal() == null) {
                logger.error("Unauthorized access attempt to project " + projectId + " by: " + userInfo);
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("message", "Unauthorized")).build();
            }

            String currentUserIdStr = jwt.getSubject(); // Get UUID from JWT 'sub' claim
            UUID currentUserId = UUID.fromString(currentUserIdStr);
            UserModel currentUser = userService.getUserById(currentUserId);
            
            if (currentUser == null) {
                logger.error("User not found during project access: " + currentUserIdStr);
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("message", "User not found")).build();
            }

            ProjectModel project = projectService.getProjectById(projectId);
            if (project == null) {
                logger.error("Project with ID " + projectId + " not found, requested by: " + userInfo);
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(java.util.Map.of("message", "Project not found")).build();
            }

            boolean isAdmin = currentUser.getIsAdmin();
            boolean isMember = project.members.stream()
                .anyMatch(member -> member.getId().equals(currentUser.getId()));
            boolean isOwner = project.owner.getId().equals(currentUser.getId());

            if (!isAdmin && !isMember && !isOwner) {
                logger.warn("Unauthorized project access attempt - User: " + currentUser.getLogin() + 
                           ", Project: " + project.name + " (ID: " + projectId + ")");
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(java.util.Map.of("message", "Not authorized to access this project")).build();
            }

            // Convert to DTO and return
            ProjectInfo projectInfo = ProjectDTOs.makeProjectInfo(project);
            
            logger.info("Project access granted - User: " + currentUser.getLogin() + 
                       ", Project: " + project.name + " (ID: " + projectId + ")");
            
            return Response.ok(projectInfo).build();

        } catch (IllegalArgumentException e) {
            logger.error("Invalid project ID format: " + projectIdStr + " from: " + userInfo);
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(java.util.Map.of("message", "Invalid project ID format")).build();
        } catch (Exception e) {
            logger.error("Error accessing project " + projectIdStr + " by " + userInfo + ": " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(java.util.Map.of("message", "Internal server error")).build();
        }
    }

    // NOW its the member management part

    @POST
    @Path("/{id}/add-user")
    @RolesAllowed({"admin", "user"})
    @Transactional
    public Response addUser(@PathParam("id") String projectIdStr, AddUser addUserRequest) {
        String userInfo = getCurrentUserInfo();
        logger.info("POST /api/projects/" + projectIdStr + "/add-user - Request from: " + userInfo);

        try {
            // Convert and validate project ID from URL path
            UUID projectId = UUID.fromString(projectIdStr);
            
            // Validate request body, must contain userId
            if (addUserRequest == null || addUserRequest.userId == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of("message", "userId is required")).build();
            }

            // Check authentication, ensure user has valid JWT token
            if (securityContext == null || securityContext.getUserPrincipal() == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("message", "Unauthorized")).build();
            }

            // Get current user making the request
            String currentUserIdStr = jwt.getSubject(); // Get UUID from JWT 'sub' claim
            UUID currentUserId = UUID.fromString(currentUserIdStr);
            UserModel currentUser = userService.getUserById(currentUserId);

            // Find the project
            ProjectModel project = projectService.getProjectById(projectId);
            if (project == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(java.util.Map.of("message", "Project not found")).build();
            }

            // Check permissions: admin OR member/owner of project
            boolean isAdmin = currentUser.getIsAdmin();
            boolean isMember = project.members.stream()
                .anyMatch(member -> member.getId().equals(currentUser.getId()));
            boolean isOwner = project.owner.getId().equals(currentUser.getId());

            if (!isAdmin && !isMember && !isOwner) {
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(java.util.Map.of("message", "Not authorized")).build();
            }

            // Find user to add
            UserModel userToAdd = userService.getUserById(addUserRequest.userId);
            if (userToAdd == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(java.util.Map.of("message", "User not found")).build();
            }

            // Check if already member (409 Conflict)
            boolean alreadyMember = project.members.stream()
                .anyMatch(member -> member.getId().equals(userToAdd.getId()));
            if (alreadyMember) {
                return Response.status(Response.Status.CONFLICT)
                    .entity(java.util.Map.of("message", "User is already a member")).build();
            }

            // Add user to project via repository
            boolean success = projectRepo.addMemberToProject(projectId, userToAdd);
            if (!success) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(java.util.Map.of("message", "Failed to add user")).build();
            }

            // Return 204 No Content (success)
            return Response.noContent().build();

        } catch (IllegalArgumentException e) {
            // Invalid UUID format
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(java.util.Map.of("message", "Invalid project ID")).build();
        } catch (Exception e) {
            logger.error("Error adding user to project " + projectIdStr + " by " + userInfo + ": " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(java.util.Map.of("message", "Internal server error")).build();
        }
    }

    @POST
    @Path("/{id}/remove-user")
    @RolesAllowed({"admin", "user"})
    @Transactional
    public Response removeUser(@PathParam("id") String projectIdStr, RemoveUser removeUserRequest) {
        String userInfo = getCurrentUserInfo();
        logger.info("POST /api/projects/" + projectIdStr + "/remove-user - Request from: " + userInfo);

        try {
            UUID projectId = UUID.fromString(projectIdStr);
            
            if (removeUserRequest == null || removeUserRequest.userId == null) {
                logger.error("Invalid remove user request - missing userId for project " + projectId + " by: " + userInfo);
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of("message", "userId is required")).build();
            }

            if (securityContext == null || securityContext.getUserPrincipal() == null) {
                logger.error("Unauthorized remove user attempt for project " + projectId + " by: " + userInfo);
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("message", "Unauthorized")).build();
            }

            String currentUserIdStr = jwt.getSubject(); // Get UUID from JWT 'sub' claim
            UUID currentUserId = UUID.fromString(currentUserIdStr);
            UserModel currentUser = userService.getUserById(currentUserId);

            if (currentUser == null) {
                logger.error("User not found during remove user operation: " + currentUserIdStr);
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("message", "User not found")).build();
            }

            ProjectModel project = projectService.getProjectById(projectId);
            if (project == null) {
                logger.error("Project not found for remove user operation - ID: " + projectId + " by: " + userInfo);
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(java.util.Map.of("message", "Project not found")).build();
            }

            boolean isAdmin = currentUser.getIsAdmin();
            boolean isOwner = project.owner.getId().equals(currentUser.getId());

            if (!isAdmin && !isOwner) {
                logger.warn("Unauthorized remove user attempt - User: " + currentUser.getLogin() + 
                           " is not owner or admin for project: " + project.name + " (ID: " + projectId + ")");
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(java.util.Map.of("message", "Only project owner or admin can remove members")).build();
            }

            UserModel userToRemove = userService.getUserById(removeUserRequest.userId);
            if (userToRemove == null) {
                logger.error("User to remove not found - ID: " + removeUserRequest.userId + 
                            " for project: " + project.name + " by: " + userInfo);
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(java.util.Map.of("message", "User to remove not found")).build();
            }

            if (project.owner.getId().equals(userToRemove.getId())) {
                logger.warn("Attempt to remove project owner - User: " + userToRemove.getLogin() + 
                           " from project: " + project.name + " by: " + currentUser.getLogin());
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(java.util.Map.of("message", "Cannot remove project owner")).build();
            }

            boolean isMember = project.members.stream()
                .anyMatch(member -> member.getId().equals(userToRemove.getId()));
            if (!isMember) {
                logger.warn("User to remove is not a member - User: " + userToRemove.getLogin() + 
                           " from project: " + project.name + " by: " + currentUser.getLogin());
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(java.util.Map.of("message", "User is not a member of the project")).build();
            }

            boolean success = projectRepo.removeMemberFromProject(projectId, userToRemove);
            if (!success) {
                logger.error("Failed to remove user " + userToRemove.getLogin() + 
                            " from project " + project.name + " by: " + currentUser.getLogin());
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(java.util.Map.of("message", "Failed to remove user")).build();
            }

            logger.info("User removed successfully - Removed: " + userToRemove.getLogin() + 
                       " from project: " + project.name + " (ID: " + projectId + ") by: " + currentUser.getLogin());
            
            // Return 204 No Content (success)
            return Response.noContent().build();

        } catch (IllegalArgumentException e) {
            logger.error("Invalid project ID format: " + projectIdStr + " from: " + userInfo);
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(java.util.Map.of("message", "Invalid project ID")).build();
        } catch (Exception e) {
            logger.error("Error removing user from project " + projectIdStr + " by " + userInfo + ": " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(java.util.Map.of("message", "Internal server error")).build();
        }
    }

    @POST
    @Path("/{id}/exec")
    @RolesAllowed({"admin", "user"})
    @Transactional
    public Response executeFeature(@PathParam("id") String projectIdStr, RunCommand execRequest) {
        String userInfo = getCurrentUserInfo();
        logger.info("POST /api/projects/" + projectIdStr + "/exec - Execute feature request from: " + userInfo);

        try {
            if (execRequest == null || execRequest.feature == null || execRequest.command == null) {
                logger.error("Invalid execution request - missing feature or command for project " + projectIdStr + " by: " + userInfo);
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of("message", "Feature and command are required")).build();
            }

            UUID projectId = UUID.fromString(projectIdStr);
            
            if (securityContext == null || securityContext.getUserPrincipal() == null) {
                logger.error("Unauthorized exec attempt for project " + projectId + " by: " + userInfo);
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("message", "Unauthorized")).build();
            }

            String currentUserIdStr = jwt.getSubject(); // Get UUID from JWT 'sub' claim
            UUID currentUserId = UUID.fromString(currentUserIdStr);
            UserModel currentUser = userService.getUserById(currentUserId);

            if (currentUser == null) {
                logger.error("User not found during exec: " + currentUserIdStr);
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("message", "User not found")).build();
            }

            ProjectModel project = projectService.getProjectById(projectId);
            if (project == null) {
                logger.error("Project not found for exec - ID: " + projectId + " by: " + userInfo);
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(java.util.Map.of("message", "Project not found")).build();
            }

            boolean isAdmin = currentUser.getIsAdmin();
            boolean isMember = project.members.stream()
                .anyMatch(member -> member.getId().equals(currentUser.getId()));
            boolean isOwner = project.owner.getId().equals(currentUser.getId());

            if (!isAdmin && !isMember && !isOwner) {
                logger.warn("Unauthorized exec attempt - User: " + currentUser.getLogin() + 
                        " not allowed for project: " + project.name + " (ID: " + projectId + ")");
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(java.util.Map.of("message", "Not authorized on this project")).build();
            }

            // Validate feature
            if (!"git".equalsIgnoreCase(execRequest.feature)) {
                logger.error("Unsupported feature: " + execRequest.feature + " for project " + project.name + " by: " + userInfo);
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of("message", "Unsupported feature: " + execRequest.feature)).build();
            }

            File projectDir = new File(project.path);
            logger.info("Using project directory: " + projectDir.getAbsolutePath());
            if (!projectDir.exists() || !projectDir.isDirectory()) {
                logger.error("Project directory does not exist: " + project.path + " for project: " + project.name);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(java.util.Map.of("message", "Project directory not found")).build();
            }

            logger.info("Executing " + execRequest.feature + " " + execRequest.command + 
                    " on project: " + project.name + " by user: " + currentUser.getLogin());

            // Execute the command
            gitExecutor.execute(projectDir, execRequest);

            logger.info("Feature execution successful - Feature: " + execRequest.feature + 
                    ", Command: " + execRequest.command + ", Project: " + project.name + 
                    " (ID: " + projectId + "), User: " + currentUser.getLogin());

            // Return 204 No Content (success)
            return Response.noContent().build();

        } catch (IllegalArgumentException e) {
            if (projectIdStr != null && !projectIdStr.matches("[a-fA-F0-9-]{36}")) {
                logger.error("Invalid project ID format: " + projectIdStr + " from: " + userInfo);
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of("message", "Invalid project ID")).build();
            } else {
                logger.error("Invalid command parameters for project " + projectIdStr + " by " + userInfo + ": " + e.getMessage());
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of("message", e.getMessage())).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error during exec for project " + projectIdStr + " by " + userInfo + ": " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(java.util.Map.of("message", "Internal server error")).build();
        }
    }

    private String getCurrentUserInfo() {
        try {
            if (securityContext != null && securityContext.getUserPrincipal() != null) {
                return securityContext.getUserPrincipal().getName();
            }
        } catch (Exception e) {
            logger.error("Could not get user info: " + e.getMessage());
        }
        return "anonymous";
    }
}