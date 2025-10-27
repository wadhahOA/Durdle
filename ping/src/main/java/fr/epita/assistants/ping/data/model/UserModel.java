package fr.epita.assistants.ping.data.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
public class UserModel {

    //@Getter
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public UUID id;

    //@Getter
    @Column(nullable = false, unique = true)
    public String login;

    @Column(nullable = false)
    public String password;

    public String displayName;

    public String avatar;

    //@Getter
    @Column(nullable = false)
    public Boolean isAdmin;

}