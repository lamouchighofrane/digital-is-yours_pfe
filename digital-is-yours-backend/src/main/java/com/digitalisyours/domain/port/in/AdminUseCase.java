package com.digitalisyours.domain.port.in;

import com.digitalisyours.domain.model.User;

import java.util.List;
import java.util.Map;

public interface AdminUseCase {
    Map<String, Long> getStats();
    List<User> getAllUsers();
    User getUserById(Long id);
    User createUser(User user, String rawPassword);
    User updateUser(Long id, User user, String rawPassword);
    void toggleActive(Long id);
    void deleteUser(Long id);
    void approveFormateur(Long id);
    void rejectFormateur(Long id);
}
