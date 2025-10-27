package fr.epita.assistants.ping.presentation.rest;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import lombok.Getter;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import io.smallrye.jwt.build.Jwt;
import fr.epita.assistants.ping.service.UserService;
import fr.epita.assistants.ping.utils.Logger;

import fr.epita.assistants.ping.dto.newUserRequest;
import fr.epita.assistants.ping.data.model.UserModel;
import fr.epita.assistants.ping.dto.LoginRequest;

import java.util.List;
import java.util.UUID;

import fr.epita.assistants.ping.data.model.UserModel;
import fr.epita.assistants.ping.dto.LoginRequest;
import fr.epita.assistants.ping.dto.newUserRequest;
import fr.epita.assistants.ping.service.UserService;
import fr.epita.assistants.ping.utils.Logger;
import io.smallrye.jwt.build.Jwt;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.smallrye.jwt.build.JwtClaimsBuilder;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api/user")
@Produces(MediaType.APPLICATION_JSON) 
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    @Inject
    UserService userService;

    @Inject
    Logger logger;

    @Context
    SecurityContext securityContext;

    @Inject
    JsonWebToken jwt;
    @POST
    @RolesAllowed("admin")
    public Response createUser(newUserRequest userRequest) 
    {
        String adminInfo = getCurrentUserInfo();
        String adminId = getCurrentUserId();
        logger.info("POST /api/user - Admin creating user - Request from: " + adminInfo + " (ID: " + adminId + ")" +
                   ", login: " + (userRequest != null ? userRequest.login : "null"));

        try {
            if (userRequest == null || userRequest.login == null || userRequest.password == null) {
                logger.error("Invalid user request - missing required fields from admin: " + adminInfo + " (ID: " + adminId + ")");
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of("message", "Invalid user request")).build();
            }

            if (userRequest.login.isBlank() || userRequest.password.isBlank()) {
                logger.error("Login and password cannot be empty - login: " + userRequest.login + 
                            " from admin: " + adminInfo + " (ID: " + adminId + ")");
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of("message", "Login and password cannot be empty")).build();
            }

            if (userRequest.isAdmin == null) {
                userRequest.isAdmin = false;
                logger.debug("Setting default admin status to false for user: " + userRequest.login + 
                            " by admin: " + adminInfo + " (ID: " + adminId + ")");
            }

            int dotCount = userRequest.login.length() - userRequest.login.replace(".", "").length();
            int underscoreCount = userRequest.login.length() - userRequest.login.replace("_", "").length();

            if (dotCount + underscoreCount != 1) {
                logger.error("Invalid login format - login must contain exactly one dot or underscore: " + 
                            userRequest.login + " from admin: " + adminInfo + " (ID: " + adminId + ")");
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of("message", "Login must contain exactly one dot or underscore")).build();
            }

            String[] parts = userRequest.login.split("[._]");
            if (parts.length != 2) {
                logger.error("Invalid login format - incorrect split result: " + userRequest.login + 
                            " from admin: " + adminInfo + " (ID: " + adminId + ")");
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of("message", "Login format incorrect")).build();
            }

            if (userService.getUserByLogin(userRequest.login) != null) {
                logger.warn("Attempt to create user with existing login: " + userRequest.login + 
                           " by admin: " + adminInfo + " (ID: " + adminId + ")");
                return Response.status(Response.Status.CONFLICT)
                    .entity(java.util.Map.of("message", "Login is already taken")).build();
            }

            // Generate the display name in uppercase format before creating user
            String displayName = parts[0].toUpperCase() + " " + parts[1].toUpperCase();
            logger.debug("Generated display name: " + displayName + " for login: " + userRequest.login + 
                        " by admin: " + adminInfo + " (ID: " + adminId + ")");

            UserModel user = userService.createUser(userRequest.login, userRequest.password, userRequest.isAdmin);

            if (user == null) {
                logger.error("Failed to create user: " + userRequest.login + " by admin: " + adminInfo + " (ID: " + adminId + ")");
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(java.util.Map.of("message", "Failed to create user")).build();
            }

            // Ensure the display name is set correctly
            user.setDisplayName(displayName);
            
            // Verify that the display name exists and is not null
            if (user.getDisplayName() == null || user.getDisplayName().isBlank()) {
                logger.error("Display name is null or empty after setting for user: " + userRequest.login + 
                            " by admin: " + adminInfo + " (ID: " + adminId + ")");
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(java.util.Map.of("message", "Failed to set display name")).build();
            }

            logger.info("User created successfully - ID: " + user.getId() + ", login: " + user.getLogin() + 
                       ", displayName: " + user.getDisplayName() + " by admin: " + adminInfo + " (ID: " + adminId + ")");

            return Response.ok(user).build();

        } catch (Exception e) {
            logger.error("Unexpected error creating user: " + e.getMessage() + " from admin: " + adminInfo + " (ID: " + adminId + ")");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(java.util.Map.of("message", "Internal server error")).build();
        }
    }

    @Path("/login")
    @POST
    public Response login(LoginRequest loginRequest) {
        logger.info("POST /api/user/login - Login attempt for: " + 
                   (loginRequest != null ? loginRequest.login : "null"));

        try {
            if (loginRequest == null || loginRequest.login == null || loginRequest.password == null) {
                logger.error("Invalid login request - missing credentials");
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of("message", "Invalid login request")).build();
            }

            if (loginRequest.login.isBlank() || loginRequest.password.isBlank()) {
                logger.error("Empty login credentials provided for login: " + loginRequest.login);
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of("message", "Login and password cannot be empty")).build();
            }

            var user = userService.getUserByLogin(loginRequest.login);
            if (user == null || !user.getPassword().equals(loginRequest.password)) {
                logger.warn("Failed login attempt for: " + loginRequest.login);
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("message", "Invalid credentials")).build();
            }
            if (user.getId() == null || user.getLogin() == null || user.getDisplayName() == null) {
                logger.error("User data incomplete - ID: " + user.getId() + 
                            ", login: " + user.getLogin() + 
                            ", displayName: " + user.getDisplayName());
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(java.util.Map.of("message", "User data is incomplete")).build();
            }

            String token = generateJwtToken(user.getId(), user.getLogin(), user.getDisplayName(), user.getIsAdmin());

            logger.info("Successful login for user: " + user.getLogin() + " (ID: " + user.getId() + ")");
            return Response.ok(java.util.Map.of("token", token)).build();

        } catch (Exception e) {
            logger.error("Unexpected error during login: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(java.util.Map.of("message", "Internal server error")).build();
        }
    }

    @GET
    @Path("/all")
    @RolesAllowed("admin")
    public Response getAllUsers() {
        String adminInfo = getCurrentUserInfo();
        String adminId = getCurrentUserId();
        logger.info("GET /api/user/all - Admin requesting all users - Request from: " + adminInfo + " (ID: " + adminId + ")");
        
        try {
            List<UserModel> users = userService.getAllUsers();
            logger.info("Retrieved " + users.size() + " users for admin: " + adminInfo + " (ID: " + adminId + ")");
            return Response.ok(users).build();
        } catch (Exception e) {
            logger.error("Error retrieving users for admin: " + adminInfo + " (ID: " + adminId + ") - " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(java.util.Map.of("message", "Internal server error")).build();
        }
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({"admin", "user"})
    public Response getUserById(@PathParam("id") String userId) {
        String userInfo = getCurrentUserInfo();
        String currentUserId = getCurrentUserId();
        logger.info("GET /api/user/" + userId + " - Request from: " + userInfo + " (ID: " + currentUserId + ")");
        
        try {
            UUID id = UUID.fromString(userId);
            UserModel user = userService.getUserById(id);
            
            if (user == null) {
                logger.warn("User not found with ID: " + userId + " requested by: " + userInfo + " (ID: " + currentUserId + ")");
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(java.util.Map.of("message", "User not found")).build();
            }
            
            if (securityContext == null || securityContext.getUserPrincipal() == null) {
                logger.error("Security context is null for get user request - userId: " + userId + 
                " by: " + userInfo + " (ID: " + currentUserId + ")");
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("message", "Unauthorized")).build();
            }

            UUID currentUserIdUUID = UUID.fromString(currentUserId);
            UserModel currentUser = userService.getUserById(currentUserIdUUID);
            
            if (currentUser == null) {
                logger.error("Current user not found for get user request - currentUserId: " + currentUserId + 
                            " requesting userId: " + userId);
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("message", "Unauthorized")).build();
            }

            // ONLY ADMIN aand THE USER THEMSELVES CAN ACCESS THEIR DATA
            boolean isAdmin = currentUser.getIsAdmin();
            boolean isSameUser = currentUserIdUUID.equals(id);

            if (!isAdmin && !isSameUser) {
                logger.warn("Unauthorized user access attempt - User: " + currentUser.getLogin() + 
                           " (ID: " + currentUserId + ") tried to access user: " + user.getLogin() + 
                           " (ID: " + userId + ")");
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(java.util.Map.of("message", "Access denied")).build();
            }
            
            logger.info("User found: " + user.getLogin() + " (ID: " + user.getId() + 
                       ") requested by: " + userInfo + " (ID: " + currentUserId + 
                       ") - isAdmin: " + isAdmin + ", isSameUser: " + isSameUser + ")");
            return Response.ok(user).build();
            
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format: " + userId + " from: " + userInfo + " (ID: " + currentUserId + ")");
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(java.util.Map.of("message", "Invalid user ID format")).build();
        } catch (Exception e) {
            logger.error("Error retrieving user: " + userId + " by: " + userInfo + " (ID: " + currentUserId + ") - " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(java.util.Map.of("message", "Internal server error")).build();
        }
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed({"admin", "user"})
    public Response updateUser(@PathParam("id") String userId, UpdateUserRequest updateRequest) {
        String userInfo = getCurrentUserInfo();
        String currentUserId = getCurrentUserId();
        logger.info("PUT /api/user/" + userId + " - Update request from: " + userInfo + " (ID: " + currentUserId + ")");
        
        try {
            UUID id = UUID.fromString(userId);
            UserModel user = userService.getUserById(id);
            
            if (user == null) {
                logger.warn("Attempt to update non-existent user with ID: " + userId + 
                           " by: " + userInfo + " (ID: " + currentUserId + ")");
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(java.util.Map.of("message", "User not found")).build();
            }

            if (securityContext == null || securityContext.getUserPrincipal() == null) {
                logger.error("Security context is null for update request - userId: " + userId + 
                            " by: " + userInfo + " (ID: " + currentUserId + ")");
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("message", "Unauthorized")).build();
            }

            //  current user making the request (only allow him and admin)
            UUID currentUserIdUUID = UUID.fromString(currentUserId);
            UserModel currentUser = userService.getUserById(currentUserIdUUID);
            
            if (currentUser == null) {
                logger.error("Current user not found for update request - currentUserId: " + currentUserId + 
                            " updating userId: " + userId);
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("message", "Unauthorized")).build();
            }

            boolean isAdmin = currentUser.getIsAdmin();
            boolean isSameUser = currentUserIdUUID.equals(id);

            if (!isAdmin && !isSameUser) {
                logger.warn("Unauthorized user update attempt - User: " + currentUser.getLogin() + 
                           " (ID: " + currentUserId + ") tried to update user: " + user.getLogin() + 
                           " (ID: " + userId + ")");
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(java.util.Map.of("message", "Access denied")).build();
            }
            
            if (updateRequest == null) {
                logger.error("Empty update request for user ID: " + userId + " from: " + userInfo + " (ID: " + currentUserId + ")");
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of("message", "Update request cannot be empty")).build();
            }
            
            UserModel updatedUser = userService.updateUser(user, 
                updateRequest.displayName, 
                updateRequest.password, 
                updateRequest.avatar);
            
            logger.info("User updated successfully: " + user.getLogin() + " (ID: " + user.getId() + 
                       ") by: " + userInfo + " (ID: " + currentUserId + 
                       ") - isAdmin: " + isAdmin + ", isSameUser: " + isSameUser + ")");
            return Response.ok(updatedUser).build();
            
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format: " + userId + " from: " + userInfo + " (ID: " + currentUserId + ")");
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(java.util.Map.of("message", "Invalid user ID format")).build();
        } catch (Exception e) {
            logger.error("Error updating user: " + userId + " by: " + userInfo + " (ID: " + currentUserId + ") - " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(java.util.Map.of("message", "Internal server error")).build();
        }
    }

    @GET
    @Path("/refresh")
    @RolesAllowed({"admin", "user"})
    public Response refreshToken() {
        String userInfo = getCurrentUserInfo();
        String currentUserId = getCurrentUserId();
        logger.info("GET /api/user/refresh - Token refresh request from: " + userInfo + " (ID: " + currentUserId + ")");
    
        try {
            if (securityContext == null || securityContext.getUserPrincipal() == null) {
                logger.warn("Unauthorized token refresh attempt by: " + userInfo + " (ID: " + currentUserId + ")");
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(java.util.Map.of("message", "Unauthorized")).build();
            }
            
            String userIdStr = getCurrentUserId();
            UUID userId = UUID.fromString(userIdStr);
            UserModel user = userService.getUserById(userId);
            
            if (user == null) {
                logger.warn("User not found for token refresh - ID: " + userId + " by: " + userInfo + " (ID: " + currentUserId + ")");
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(java.util.Map.of("message", "User not found")).build();
            }
    
            String token = generateJwtToken(user.getId(), user.getLogin(), user.getDisplayName(), user.getIsAdmin());
            logger.info("Token refreshed successfully for user: " + user.getLogin() + " (ID: " + user.getId() + ")");
            return Response.ok(java.util.Map.of("token", token)).build();
    
        } catch (Exception e) {
            logger.error("Token refresh failed for user: " + userInfo + " (ID: " + currentUserId + ") - " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(java.util.Map.of("message", "Internal server error")).build();
        }
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("admin")
    public Response deleteUser(@PathParam("id") String userId) {
        String adminInfo = getCurrentUserInfo();
        String adminId = getCurrentUserId();
        logger.info("DELETE /api/user/" + userId + " - Delete request from admin: " + adminInfo + " (ID: " + adminId + ")");
        
        try {
            UUID id = UUID.fromString(userId);
            boolean deleted = userService.removeUser(id);
            
            if (deleted) {
                logger.info("User deleted successfully with ID: " + userId + " by admin: " + adminInfo + " (ID: " + adminId + ")");
                return Response.ok(java.util.Map.of("message", "User deleted successfully")).build();
            } else {
                logger.warn("Attempt to delete non-existent user with ID: " + userId + 
                           " by admin: " + adminInfo + " (ID: " + adminId + ")");
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(java.util.Map.of("message", "User not found")).build();
            }
            
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format: " + userId + " from admin: " + adminInfo + " (ID: " + adminId + ")");
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(java.util.Map.of("message", "Invalid user ID format")).build();
        } catch (Exception e) {
            logger.error("Error deleting user: " + userId + " by admin: " + adminInfo + " (ID: " + adminId + ") - " + e.getMessage());
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

    private String getCurrentUserId() {
        try {
            if (jwt != null && jwt.getSubject() != null) {
                return jwt.getSubject();
            }
        } catch (Exception e) {
            logger.error("Could not get user ID from JWT: " + e.getMessage());
        }
        return "unknown";
    }

    public static class UpdateUserRequest {
        public String displayName;
        public String password;
        public String avatar;
    }

    private String generateJwtToken(UUID userId, String login, String displayName, Boolean isAdmin) {
        return Jwt.claims()
                .issuer("ping-api")
                .subject(userId.toString())
                .upn(login)
                .claim("login", login)
                .claim("displayName", displayName)
                .groups(isAdmin ? java.util.Set.of("admin", "user") : java.util.Set.of("user"))
                .expiresAt(java.time.Instant.now().plusSeconds(3600))
                .jws()
                .sign();
    }
}