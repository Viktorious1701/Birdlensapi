package com.example.birdlensapi.domain.user;

import com.example.birdlensapi.common.exception.ConflictException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse registerUser(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email is already in use.");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username is already in use.");
        }

        String hashedPassword = passwordEncoder.encode(request.password());

        User user = new User(request.email(), request.username(), hashedPassword);
        User savedUser = userRepository.save(user);

        return UserResponse.fromEntity(savedUser);
    }
}