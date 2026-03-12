package com.digitalisyours.application.service;

import com.digitalisyours.domain.model.User;
import com.digitalisyours.domain.port.in.AdminUseCase;
import com.digitalisyours.domain.port.out.AdminRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminService implements AdminUseCase {
    private final AdminRepositoryPort adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Map<String, Long> getStats() {
        long apprenants = adminRepository.countByRole("APPRENANT");
        long formateurs = adminRepository.countByRole("FORMATEUR");
        return Map.of(
                "totalUsers",  apprenants + formateurs,
                "apprenants",  apprenants,
                "formateurs",  formateurs,
                "nonVerifies", adminRepository.countEmailNonVerifie(),
                "desactives",  adminRepository.countDesactives()
        );
    }

    @Override
    public List<User> getAllUsers() {
        return adminRepository.findAllNonAdmin();
    }

    @Override
    public User getUserById(Long id) {
        return adminRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }

    @Override
    public User createUser(User user, String rawPassword) {
        if (adminRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Cet email est déjà utilisé");
        }
        user.setMotDePasse(passwordEncoder.encode(rawPassword));
        user.setEmailVerifie(true);
        user.setActive(true);
        return adminRepository.save(user);
    }

    @Override
    public User updateUser(Long id, User user, String rawPassword) {
        User existing = adminRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        if (!existing.getEmail().equals(user.getEmail())
                && adminRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Cet email est déjà utilisé");
        }

        existing.setPrenom(user.getPrenom());
        existing.setNom(user.getNom());
        existing.setEmail(user.getEmail());
        existing.setTelephone(user.getTelephone());
        existing.setRole(user.getRole());

        if (rawPassword != null && !rawPassword.isBlank()) {
            existing.setMotDePasse(passwordEncoder.encode(rawPassword));
        }

        return adminRepository.save(existing);
    }

    @Override
    public void toggleActive(Long id) {
        User user = adminRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        user.setActive(!user.isActive());
        adminRepository.save(user);
    }

    @Override
    public void deleteUser(Long id) {
        adminRepository.deleteById(id);
    }

    @Override
    public void approveFormateur(Long id) {
        User user = adminRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        user.setEmailVerifie(true);
        user.setActive(true);
        adminRepository.save(user);
    }

    @Override
    public void rejectFormateur(Long id) {
        adminRepository.deleteById(id);
    }
}
