package fr.epita.assistants.ping.dto;

import java.util.List;
import java.util.UUID;

import fr.epita.assistants.ping.utils.Logger;
import jakarta.inject.Inject;

// all project DTOs in one file this is much simpler for import i,stead of having a file per DTO
//FIXME: apprentrly not good prtice, so cqn be chqnged later
public class ProjectDTOs {

    @Inject
    static Logger logger;
    
    public static class CreateProject {
        public String name;
    }
    

    public static class UpdateProject {
        public String name;
        public UUID newOwnerId;
    }
    

    public static class ProjectInfo {
        public UUID id;
        public String name;
        public List<UserSummary> members;
        public UserSummary owner;
    }
    
    public static class UserSummary {
        public UUID id;
        public String displayName;
        public String avatar;
    }
    
    public static class AddUser {
        public UUID userId;
    }
    
    public static class RunCommand {
        public String feature;
        public String command;
        public List<String> params;
    }

    public static class RemoveUser {
        public UUID userId;
    }   


    public static UserSummary makeUserSummary(fr.epita.assistants.ping.data.model.UserModel user) {
        if(user == null)
        {
            logger.error("makeUserSummary called with null user");
            return null;
        }
        UserSummary summary = new UserSummary();
        summary.id = user.getId();
        summary.displayName = user.getDisplayName();
        summary.avatar = user.getAvatar();
        return summary;
    }

    public static ProjectInfo makeProjectInfo(fr.epita.assistants.ping.data.model.ProjectModel project) {
        if (project == null) {
            logger.error("makeProjectInfo called with null project");
            return null;
        }
        ProjectInfo info = new ProjectInfo();

        info.id = project.id;
        info.name = project.name;
        info.owner = makeUserSummary(project.owner);
        info.members = project.members.stream()
            .map(ProjectDTOs::makeUserSummary)
            .toList();
        return info;
    }


}