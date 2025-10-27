package fr.epita.assistants.ping.repository;

import fr.epita.assistants.ping.data.model.UserModel;
import fr.epita.assistants.ping.utils.Logger;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserRepository implements PanacheRepository<UserModel> {

    @Inject
    Logger logger;

    @Transactional
    public UserModel saveUser(String login, String password, Boolean admin) {
        logger.info("Repository: Creating new user with login: " + login + ", admin: " + admin);
        UserModel entity = new UserModel();
        entity.setLogin(login);
        entity.setPassword(password);
        entity.setIsAdmin(admin != null ? admin : false);
        entity.setAvatar("");
        entity.setDisplayName(formatDisplayName(login));
        persist(entity);
        logger.info("Repository: User persisted successfully with ID: " + entity.getId() + 
                   ", login: " + login);
        return entity;
    }

    @Transactional
    public Optional<UserModel> findUserByLogin(String login) {
        logger.debug("Repository: Searching for user with login: " + login);
        Optional<UserModel> result = find("login", login).firstResultOptional();
        if (result.isPresent()) {
            logger.debug("Repository: User found with login: " + login + 
                        ", ID: " + result.get().getId());
        } else {
            logger.debug("Repository: No user found with login: " + login);
        }
        return result;
    }

    @Transactional
    public Optional<UserModel> findUserById(UUID id) {
        logger.debug("Repository: Searching for user with ID: " + id);
        Optional<UserModel> result = find("id", id).firstResultOptional();
        if (result.isPresent()) {
            logger.debug("Repository: User found with ID: " + id + 
                        ", login: " + result.get().getLogin());
        } else {
            logger.debug("Repository: No user found with ID: " + id);
        }
        return result;
    }

    @Transactional
    public List<UserModel> listUsers() {
        logger.info("Repository: Retrieving all users");
        List<UserModel> users = listAll();
        logger.info("Repository: Retrieved " + users.size() + " users");
        return users;
    }

    @Transactional
    public boolean removeUser(UUID id) {
        logger.info("Repository: Attempting to remove user with ID: " + id);
        Optional<UserModel> userOpt = findUserById(id);
        if (userOpt.isPresent()) {
            logger.info("Repository: User found, deleting user: " + userOpt.get().getLogin());
            userOpt.ifPresent(this::delete);
            boolean deleted = findUserById(id).isEmpty();
            if (deleted) {
                logger.info("Repository: User successfully deleted with ID: " + id);
            } else {
                logger.error("Repository: Failed to delete user with ID: " + id);
            }
            return deleted;
        } else {
            logger.warn("Repository: Attempted to delete non-existent user with ID: " + id);
            return false;
        }
    }

    @Transactional
    public UserModel modifyUser(UserModel user, String displayName, String password, String avatar) {
        logger.info("Repository: Modifying user with ID: " + user.getId() + 
                   ", login: " + user.getLogin());
        if (displayName != null && !displayName.isBlank()) {
            logger.debug("Repository: Updating display name from '" + user.getDisplayName() + 
                        "' to '" + displayName + "'");
            user.setDisplayName(displayName);
        }
        if (password != null && !password.isBlank()) {
            logger.debug("Repository: Updating password for user: " + user.getLogin());
            user.setPassword(password);
        }
        if (avatar != null) {
            logger.debug("Repository: Updating avatar for user: " + user.getLogin());
            user.setAvatar(avatar);
        }
        UserModel result = getEntityManager().merge(user);
        logger.info("Repository: User modified successfully: " + user.getLogin());
        return result;
    }

    @Transactional
    public long totalUserCount() {
        logger.debug("Repository: Counting total users");
        long count = count();
        logger.debug("Repository: Total user count: " + count);
        return count;
    }

    // helper function to format display name from login
    private String formatDisplayName(String login) {
        String[] parts = login.split("[._]");
        if (parts.length == 2) {
            return capitalize(parts[0]) + " " + capitalize(parts[1]);
        }
        return login;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}