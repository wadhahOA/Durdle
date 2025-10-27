package fr.epita.assistants.ping.service;

import fr.epita.assistants.ping.data.model.UserModel;
import fr.epita.assistants.ping.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import fr.epita.assistants.ping.utils.Logger;


import java.util.List;
import java.util.UUID;


@ApplicationScoped
public class UserService {
    @Inject
    Logger logger;


    @Inject
    UserRepository userRepo;

    public UserModel getUserByLogin(String login) {
        if (login == null) {
            logger.error("getUserByLogin called with null login parameter");
            return null;
        }
        return userRepo.findUserByLogin(login).orElse(null);
    }

    public UserModel getUserById(UUID id) {
        if (id == null) {
            logger.error("getUserById called with null id parameter");
            return null;
        }
        return userRepo.findUserById(id).orElse(null);
    }

    public List<UserModel> getAllUsers() {
        
        return userRepo.listUsers();
    }

    @Transactional
    public UserModel createUser(String login, String password, Boolean isAdmin) {
    if (login == null || password == null) {
        logger.error("createUser called with null parameters - login: " + login + ", password: " + (password != null ? "[PROVIDED]" : "null"));
        return null;
    }
    return userRepo.saveUser(login, password, isAdmin);
}
    @Transactional
    public UserModel updateUser(UserModel user, String displayName, String password, String avatar) {
        if (user == null) {
            logger.error("updateUser called with null user");
            return null;
        }
        return userRepo.modifyUser(user, displayName, password, avatar);
    }

    @Transactional
    public boolean removeUser(UUID id) {
        if (id == null) {
            logger.error("removeUser called with null id");
            return false;
        }
        return userRepo.removeUser(id);
    }

    public long countUsers() {
        return userRepo.totalUserCount();
    }
}