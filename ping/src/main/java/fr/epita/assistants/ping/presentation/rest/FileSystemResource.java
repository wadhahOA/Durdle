package fr.epita.assistants.ping.presentation.rest;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import fr.epita.assistants.ping.data.model.ProjectModel;
import fr.epita.assistants.ping.data.model.UserModel;
import fr.epita.assistants.ping.dto.FSEntryResponse;
import fr.epita.assistants.ping.dto.MoveRequest;
import fr.epita.assistants.ping.dto.PathRequest;
import fr.epita.assistants.ping.service.FileSystemService;
import fr.epita.assistants.ping.service.ProjectService;
import fr.epita.assistants.ping.service.UserService;
import fr.epita.assistants.ping.utils.Logger;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
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

@Path("/api/projects")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"admin", "user"})
public class FileSystemResource {

    @Inject
    FileSystemService fileSystemService;

    @Inject
    ProjectService projectService;

    @Inject
    UserService userService;

    @Inject
    Logger logger;

    @Context
    SecurityContext securityContext;

    @Inject
    JsonWebToken jwt;

    @ConfigProperty(name = "PROJECT_DEFAULT_PATH")
    String basePath;

    @GET
    @Path("{projectId}/files")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @RolesAllowed({"admin", "user"})
    public Response getFile(@PathParam("projectId") UUID projectId,
                           @QueryParam("path") @DefaultValue("") String path) {
        String userInfo = getCurrentUserInfo();
        logger.info("GET /api/projects/" + projectId + "/files - Request from: " + userInfo + ", path: " + path);

        try {
            String currentUserIdStr = jwt.getSubject();
            UUID currentUserId = UUID.fromString(currentUserIdStr);
            UserModel currentUser = userService.getUserById(currentUserId);

            if (currentUser == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("message", "User not found")).build();
            }

            if (!hasProjectAccess(projectId, currentUser)) {
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(java.util.Map.of("message", "Not authorized")).build();
            }

            if (path == null || path.trim().isEmpty()) {
                logger.error("Invalid path parameter for project " + projectId + " by: " + userInfo);
                return Response.status(400).entity("The relative path is invalid").build();
            }

            ProjectModel project = projectService.getProjectById(projectId);
            if (project == null || project.path == null) {
                return Response.status(404).entity("Project not found").build();
            }
            
            java.nio.file.Path file = fileSystemService.resolveSafePath(projectId, path, project.path);
            byte[] content = fileSystemService.readFileBytes(file);
            
            logger.info("Successfully retrieved file for project " + projectId + " by: " + userInfo);
            return Response.ok(content).build();
        } catch (SecurityException e) {
            logger.warn("Path traversal attack detected for project " + projectId + " by: " + userInfo + " - path: " + path);
            return Response.status(403).entity("Path traversal attack detected").build();
        } catch (IOException e) {
            logger.error("File not found for project " + projectId + " by: " + userInfo + " - path: " + path + " - " + e.getMessage());
            return Response.status(404).entity("The project or the relative path could not be found").build();
        } catch (Exception e) {
            logger.error("Unexpected error retrieving file for project " + projectId + " by: " + userInfo + " - " + e.getMessage());
            return Response.status(500).build();
        }
    }

    @POST
    @Path("{projectId}/files")
    @RolesAllowed({"admin", "user"})
    @Transactional
    public Response createFile(@PathParam("projectId") UUID projectId,
                              PathRequest request) {
        String userInfo = getCurrentUserInfo();
        logger.info("POST /api/projects/" + projectId + "/files - Create file request from: " + userInfo);

        try {
            String currentUserIdStr = jwt.getSubject();
            UUID currentUserId = UUID.fromString(currentUserIdStr);
            UserModel currentUser = userService.getUserById(currentUserId);

            if (currentUser == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("message", "User not found")).build();
            }

            if (!hasProjectAccess(projectId, currentUser)) {
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(java.util.Map.of("message", "Not authorized")).build();
            }

            if (request.relativePath == null || request.relativePath.trim().isEmpty()) {
                logger.error("Invalid relative path for file creation in project " + projectId + " by: " + userInfo);
                return Response.status(400).entity("The relative path is invalid").build();
            }

            ProjectModel project = projectService.getProjectById(projectId);
            if (project == null || project.path == null) {
                return Response.status(404).entity("Project not found").build();
            }
            
            java.nio.file.Path file = fileSystemService.resolveSafePath(projectId, request.relativePath, project.path);
            fileSystemService.createFile(file);
            
            logger.info("Successfully created file in project " + projectId + " by: " + userInfo + " - path: " + request.relativePath);
            return Response.status(201).build();
        } catch (SecurityException e) {
            logger.warn("Path traversal attack detected for file creation in project " + projectId + " by: " + userInfo + " - path: " + request.relativePath);
            return Response.status(403).entity("Path traversal attack detected").build();
        } catch (java.nio.file.FileAlreadyExistsException e) {
            logger.warn("File already exists in project " + projectId + " by: " + userInfo + " - path: " + request.relativePath);
            return Response.status(409).entity("The file already exists").build();
        } catch (java.nio.file.NoSuchFileException e) {
            logger.error("Project not found for file creation - ID: " + projectId + " by: " + userInfo);
            return Response.status(404).entity("The project could not be found").build();
        } catch (Exception e) {
            logger.error("Unexpected error creating file in project " + projectId + " by: " + userInfo + " - " + e.getMessage());
            return Response.status(500).build();
        }
    }

    @DELETE
    @Path("{projectId}/files")
    @RolesAllowed({"admin", "user"})
    @Transactional
    public Response deleteFile(@PathParam("projectId") UUID projectId,
                              PathRequest request) {
        String userInfo = getCurrentUserInfo();
        logger.info("DELETE /api/projects/" + projectId + "/files - Delete file request from: " + userInfo);

        try {
            String currentUserIdStr = jwt.getSubject();
            UUID currentUserId = UUID.fromString(currentUserIdStr);
            UserModel currentUser = userService.getUserById(currentUserId);

            if (currentUser == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("message", "User not found")).build();
            }

            if (!hasProjectAccess(projectId, currentUser)) {
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(java.util.Map.of("message", "Not authorized")).build();
            }

            if (request.relativePath == null || request.relativePath.trim().isEmpty()) {
                logger.error("Invalid relative path for file deletion in project " + projectId + " by: " + userInfo);
                return Response.status(400).entity("The relative path is invalid").build();
            }

            ProjectModel project = projectService.getProjectById(projectId);
            if (project == null || project.path == null) {
                return Response.status(404).entity("Project not found").build();
            }
            
            java.nio.file.Path file = fileSystemService.resolveSafePath(projectId, request.relativePath, project.path);
            fileSystemService.deleteFile(file);
            
            logger.info("Successfully deleted file in project " + projectId + " by: " + userInfo + " - path: " + request.relativePath);
            return Response.status(204).build();
        } catch (SecurityException e) {
            logger.warn("Path traversal attack detected for file deletion in project " + projectId + " by: " + userInfo + " - path: " + request.relativePath);
            return Response.status(403).entity("Path traversal attack detected").build();
        } catch (java.nio.file.NoSuchFileException e) {
            logger.error("File not found for deletion in project " + projectId + " by: " + userInfo + " - path: " + request.relativePath);
            return Response.status(404).entity("The project or the file could not be found").build();
        } catch (Exception e) {
            logger.error("Unexpected error deleting file in project " + projectId + " by: " + userInfo + " - " + e.getMessage());
            return Response.status(500).build();
        }
    }

    @PUT
    @Path("{projectId}/files/move")
    @RolesAllowed({"admin", "user"})
    @Transactional
    public Response moveFile(@PathParam("projectId") UUID projectId,
                            MoveRequest request) {
        String userInfo = getCurrentUserInfo();
        logger.info("PUT /api/projects/" + projectId + "/files/move - Move file request from: " + userInfo);

        try {
            String currentUserIdStr = jwt.getSubject();
            UUID currentUserId = UUID.fromString(currentUserIdStr);
            UserModel currentUser = userService.getUserById(currentUserId);

            if (currentUser == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("message", "User not found")).build();
            }

            if (!hasProjectAccess(projectId, currentUser)) {
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(java.util.Map.of("message", "Not authorized")).build();
            }

            if (request.src == null || request.src.trim().isEmpty() || 
                request.dst == null || request.dst.trim().isEmpty()) {
                logger.error("Invalid source or destination path for file move in project " + projectId + " by: " + userInfo);
                return Response.status(400).entity("The source or destination path is invalid").build();
            }

            ProjectModel project = projectService.getProjectById(projectId);
            if (project == null || project.path == null) {
                return Response.status(404).entity("Project not found").build();
            }
            
            java.nio.file.Path srcPath = fileSystemService.resolveSafePath(projectId, request.src, project.path);
            java.nio.file.Path dstPath = fileSystemService.resolveSafePath(projectId, request.dst, project.path);
            fileSystemService.moveFile(srcPath, dstPath);
            
            logger.info("Successfully moved file in project " + projectId + " by: " + userInfo + " - from: " + request.src + " to: " + request.dst);
            return Response.status(204).build();
        } catch (SecurityException e) {
            logger.warn("Path traversal attack detected for file move in project " + projectId + " by: " + userInfo + " - src: " + request.src + " dst: " + request.dst);
            return Response.status(403).entity("Path traversal attack detected").build();
        } catch (java.nio.file.FileAlreadyExistsException e) {
            logger.warn("File already exists for move in project " + projectId + " by: " + userInfo + " - dst: " + request.dst);
            return Response.status(409).entity("The file already exists").build();
        } catch (java.nio.file.NoSuchFileException e) {
            logger.error("Project or file not found for move in project " + projectId + " by: " + userInfo + " - src: " + request.src);
            return Response.status(404).entity("The project could not be found").build();
        } catch (Exception e) {
            logger.error("Unexpected error moving file in project " + projectId + " by: " + userInfo + " - " + e.getMessage());
            return Response.status(500).build();
        }
    }

    @POST
    @Path("{projectId}/files/upload")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @RolesAllowed({"admin", "user"})
    @Transactional
    public Response uploadFile(@PathParam("projectId") UUID projectId,
                              @QueryParam("path") String path,
                              InputStream inputStream) {
        String userInfo = getCurrentUserInfo();
        logger.info("POST /api/projects/" + projectId + "/files/upload - Upload file request from: " + userInfo + ", path: " + path);

        try {
            String currentUserIdStr = jwt.getSubject();
            UUID currentUserId = UUID.fromString(currentUserIdStr);
            UserModel currentUser = userService.getUserById(currentUserId);

            if (currentUser == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("message", "User not found")).build();
            }

            if (!hasProjectAccess(projectId, currentUser)) {
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(java.util.Map.of("message", "Not authorized")).build();
            }

            if (path == null || path.trim().isEmpty()) {
                logger.error("Invalid path parameter for file upload in project " + projectId + " by: " + userInfo);
                return Response.status(400).entity("The relative path is invalid").build();
            }

            ProjectModel project = projectService.getProjectById(projectId);
            if (project == null || project.path == null) {
                return Response.status(404).entity("Project not found").build();
            }
            
            java.nio.file.Path file = fileSystemService.resolveSafePath(projectId, path, project.path);
            fileSystemService.uploadFile(file, inputStream);
            
            logger.info("Successfully uploaded file in project " + projectId + " by: " + userInfo + " - path: " + path);
            return Response.status(201).build();
        } catch (SecurityException e) {
            logger.warn("Path traversal attack detected for file upload in project " + projectId + " by: " + userInfo + " - path: " + path);
            return Response.status(403).entity("Path traversal attack detected").build();
        } catch (java.nio.file.NoSuchFileException e) {
            logger.error("Project not found for file upload - ID: " + projectId + " by: " + userInfo);
            return Response.status(404).entity("The project could not be found").build();
        } catch (Exception e) {
            logger.error("Unexpected error uploading file in project " + projectId + " by: " + userInfo + " - " + e.getMessage());
            return Response.status(500).build();
        }
    }

    @GET
    @Path("{projectId}/folders")
    @RolesAllowed({"admin", "user"})
    @Transactional
    public Response listFolder(@PathParam("projectId") UUID projectId,
                              @QueryParam("path") @DefaultValue("") String path) {
        String userInfo = getCurrentUserInfo();
        logger.info("GET /api/projects/" + projectId + "/folders - List folder request from: " + userInfo + ", path: " + path);

        try {
            String currentUserIdStr = jwt.getSubject();
            UUID currentUserId = UUID.fromString(currentUserIdStr);
            UserModel currentUser = userService.getUserById(currentUserId);

            if (currentUser == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("message", "User not found")).build();
            }

            if (!hasProjectAccess(projectId, currentUser)) {
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(java.util.Map.of("message", "Not authorized")).build();
            }

            ProjectModel project = projectService.getProjectById(projectId);
            if (project == null || project.path == null) {
                return Response.status(404).entity("Project not found").build();
            }

            java.nio.file.Path dir = fileSystemService.resolveSafePath(projectId, path, project.path);
            List<FSEntryResponse> entries = fileSystemService.listFolder(dir);
            
            logger.info("Successfully listed folder in project " + projectId + " by: " + userInfo + " - " + entries.size() + " entries");
            return Response.ok(entries).build();
        } catch (SecurityException e) {
            logger.warn("Path traversal attack detected for folder listing in project " + projectId + " by: " + userInfo + " - path: " + path);
            return Response.status(403).entity("Path traversal attack detected").build();
        } catch (Exception e) {
            logger.error("Error listing folder in project " + projectId + " by: " + userInfo + " - path: " + path + " - " + e.getMessage());
            return Response.status(404).entity("The project or the relative path could not be found").build();
        }
    }

    @POST
    @Path("{projectId}/folders")
    @RolesAllowed({"admin", "user"})
    @Transactional
    public Response createFolder(@PathParam("projectId") UUID projectId,
                                PathRequest request) {
        String userInfo = getCurrentUserInfo();
        logger.info("POST /api/projects/" + projectId + "/folders - Create folder request from: " + userInfo);

        try {
            String currentUserIdStr = jwt.getSubject();
            UUID currentUserId = UUID.fromString(currentUserIdStr);
            UserModel currentUser = userService.getUserById(currentUserId);

            if (currentUser == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("message", "User not found")).build();
            }

            if (!hasProjectAccess(projectId, currentUser)) {
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(java.util.Map.of("message", "Not authorized")).build();
            }

            if (request.relativePath == null || request.relativePath.trim().isEmpty()) {
                logger.error("Invalid relative path for folder creation in project " + projectId + " by: " + userInfo);
                return Response.status(400).entity("The relative path is invalid").build();
            }

            ProjectModel project = projectService.getProjectById(projectId);
            if (project == null || project.path == null) {
                return Response.status(404).entity("Project not found").build();
            }
            
            java.nio.file.Path dir = fileSystemService.resolveSafePath(projectId, request.relativePath, project.path);
            fileSystemService.createFolder(dir);
            
            logger.info("Successfully created folder in project " + projectId + " by: " + userInfo + " - path: " + request.relativePath);
            return Response.status(201).build();
        } catch (SecurityException e) {
            logger.warn("Path traversal attack detected for folder creation in project " + projectId + " by: " + userInfo + " - path: " + request.relativePath);
            return Response.status(403).entity("Path traversal attack detected").build();
        } catch (java.nio.file.FileAlreadyExistsException e) {
            logger.warn("Folder already exists in project " + projectId + " by: " + userInfo + " - path: " + request.relativePath);
            return Response.status(409).entity("The folder already exists").build();
        } catch (java.nio.file.NoSuchFileException e) {
            logger.error("Project not found for folder creation - ID: " + projectId + " by: " + userInfo);
            return Response.status(404).entity("The project could not be found").build();
        } catch (Exception e) {
            logger.error("Unexpected error creating folder in project " + projectId + " by: " + userInfo + " - " + e.getMessage());
            return Response.status(500).build();
        }
    }

    @DELETE
    @Path("{projectId}/folders")
    @RolesAllowed({"admin", "user"})
    @Transactional
    public Response deleteFolder(@PathParam("projectId") UUID projectId,
                                PathRequest request) {
        String userInfo = getCurrentUserInfo();
        logger.info("DELETE /api/projects/" + projectId + "/folders - Delete folder request from: " + userInfo);

        try {
            String currentUserIdStr = jwt.getSubject();
            UUID currentUserId = UUID.fromString(currentUserIdStr);
            UserModel currentUser = userService.getUserById(currentUserId);

            if (currentUser == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("message", "User not found")).build();
            }

            if (!hasProjectAccess(projectId, currentUser)) {
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(java.util.Map.of("message", "Not authorized")).build();
            }

            if (request.relativePath == null || request.relativePath.trim().isEmpty()) {
                logger.error("Invalid relative path for folder deletion in project " + projectId + " by: " + userInfo);
                return Response.status(400).entity("The relative path is invalid").build();
            }

            ProjectModel project = projectService.getProjectById(projectId);
            if (project == null || project.path == null) {
                return Response.status(404).entity("Project not found").build();
            }
            
            java.nio.file.Path dir = fileSystemService.resolveSafePath(projectId, request.relativePath, project.path);
            fileSystemService.deleteFolder(dir);
            
            logger.info("Successfully deleted folder in project " + projectId + " by: " + userInfo + " - path: " + request.relativePath);
            return Response.status(204).build();
        } catch (SecurityException e) {
            logger.warn("Path traversal attack detected for folder deletion in project " + projectId + " by: " + userInfo + " - path: " + request.relativePath);
            return Response.status(403).entity("Path traversal attack detected").build();
        } catch (java.nio.file.NoSuchFileException e) {
            logger.error("Folder not found for deletion in project " + projectId + " by: " + userInfo + " - path: " + request.relativePath);
            return Response.status(404).entity("The project or the folder could not be found").build();
        } catch (Exception e) {
            logger.error("Unexpected error deleting folder in project " + projectId + " by: " + userInfo + " - " + e.getMessage());
            return Response.status(500).build();
        }
    }

    @PUT
    @Path("{projectId}/folders/move")
    @RolesAllowed({"admin", "user"})
    @Transactional
    public Response moveFolder(@PathParam("projectId") UUID projectId,
                              MoveRequest request) {
        String userInfo = getCurrentUserInfo();
        logger.info("PUT /api/projects/" + projectId + "/folders/move - Move folder request from: " + userInfo);

        try {
            String currentUserIdStr = jwt.getSubject();
            UUID currentUserId = UUID.fromString(currentUserIdStr);
            UserModel currentUser = userService.getUserById(currentUserId);

            if (currentUser == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("message", "User not found")).build();
            }

            if (!hasProjectAccess(projectId, currentUser)) {
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(java.util.Map.of("message", "Not authorized")).build();
            }

            if (request.src == null || request.src.trim().isEmpty() || 
                request.dst == null || request.dst.trim().isEmpty()) {
                logger.error("Invalid source or destination path for folder move in project " + projectId + " by: " + userInfo);
                return Response.status(400).entity("The source or destination path is invalid").build();
            }

            ProjectModel project = projectService.getProjectById(projectId);
            if (project == null || project.path == null) {
                return Response.status(404).entity("Project not found").build();
            }
            
            java.nio.file.Path srcPath = fileSystemService.resolveSafePath(projectId, request.src, project.path);
            java.nio.file.Path dstPath = fileSystemService.resolveSafePath(projectId, request.dst, project.path);
            fileSystemService.moveFolder(srcPath, dstPath);
            
            logger.info("Successfully moved folder in project " + projectId + " by: " + userInfo + " - from: " + request.src + " to: " + request.dst);
            return Response.status(204).build();
        } catch (SecurityException e) {
            logger.warn("Path traversal attack detected for folder move in project " + projectId + " by: " + userInfo + " - src: " + request.src + " dst: " + request.dst);
            return Response.status(403).entity("Path traversal attack detected").build();
        } catch (java.nio.file.FileAlreadyExistsException e) {
            logger.warn("Folder already exists for move in project " + projectId + " by: " + userInfo + " - dst: " + request.dst);
            return Response.status(409).entity("The folder already exists").build();
        } catch (java.nio.file.NoSuchFileException e) {
            logger.error("Project or folder not found for move in project " + projectId + " by: " + userInfo + " - src: " + request.src);
            return Response.status(404).entity("The project could not be found or the source folder could not be found").build();
        } catch (Exception e) {
            logger.error("Unexpected error moving folder in project " + projectId + " by: " + userInfo + " - " + e.getMessage());
            return Response.status(500).build();
        }
    }

    // @GET
    // @Path("{projectId}/debug")
    // @RolesAllowed({"admin", "user"})
    // public Response debug() {
    //     String userInfo = getCurrentUserInfo();
    //     logger.info("FileSystemResource debug endpoint accessed by: " + userInfo);
    //     return Response.ok("FileSystemResource debug: OK").build();
    // }

    private boolean hasProjectAccess(UUID projectId, UserModel user) {
        ProjectModel project = projectService.getProjectById(projectId);
        if (project == null) return false;

        boolean isAdmin = user.getIsAdmin();
        boolean isMember = project.members.stream()
            .anyMatch(member -> member.getId().equals(user.getId()));
        boolean isOwner = project.owner.getId().equals(user.getId());

        return isAdmin || isMember || isOwner;
    }

    private String getCurrentUserInfo() {
        try {
            if (securityContext != null && securityContext.getUserPrincipal() != null) {
                return securityContext.getUserPrincipal().getName();
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.error("Could not get user info: " + e.getMessage());
            }
        }
        return "anonymous";
    }
}