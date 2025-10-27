package fr.epita.assistants.ping.data.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor 
public class ProjectModel {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "path")
    public String path;

    // 1 project has 1 owner (in our case)
    @ManyToOne(fetch = FetchType.LAZY) // Lazy just means that you don't load the owner until we use it (opposite of eager)
    @JoinColumn(name = "owner_id", nullable = false)
    public UserModel owner;

    // one project has many members one user can be member of many projects
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "project_members",
        joinColumns = @JoinColumn(name = "project_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    public List<UserModel> members = new ArrayList<>();

    // Keep ONLY this constructor for business logic
    public ProjectModel(String name, UserModel owner) {
        // let Hibernate generate it
        this.name = name;
        this.owner = owner;
        this.members = new ArrayList<>();
        this.members.add(owner); // the owner is automatically a member
    }
}