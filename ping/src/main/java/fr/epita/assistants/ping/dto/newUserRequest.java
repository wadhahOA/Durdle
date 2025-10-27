package fr.epita.assistants.ping.dto;

public class newUserRequest {
    //DTO stand for Data Transfer Object, it is a simple object that is used to transfer data between layers of an application.
    // En francais on parle d'objet de transfert de données (OTD).
    //https://fr.wikipedia.org/wiki/Objet_de_transfert_de_donn%C3%A9es

    public String login;
    public String password;
    public Boolean isAdmin;
    
}