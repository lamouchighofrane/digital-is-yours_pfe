package com.digitalisyours.infrastructure.web.dto.request;

import com.digitalisyours.domain.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "Le prénom est requis")
    @Size(min = 2, message = "Le prénom doit avoir au moins 2 caractères")
    private String prenom;

    @NotBlank(message = "Le nom est requis")
    @Size(min = 2, message = "Le nom doit avoir au moins 2 caractères")
    private String nom;

    @NotBlank(message = "L'email est requis")
    @Email(message = "Email invalide")
    private String email;

    private String telephone;

    @NotBlank(message = "Le mot de passe est requis")
    @Size(min = 8, message = "Le mot de passe doit avoir au moins 8 caractères")
    private String password;

    @NotNull(message = "Le rôle est requis")
    private Role role;
}
