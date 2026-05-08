package edu.cit.hopista.budgetmate.shared.config;

import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import edu.cit.hopista.budgetmate.features.user.entity.Role;
import edu.cit.hopista.budgetmate.features.user.entity.User;
import edu.cit.hopista.budgetmate.features.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdminAccountSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail("admin")) {
            return;
        }

        User admin = User.builder()
                .name("admin")
                .email("admin")
                .password(passwordEncoder.encode("admin@123"))
                .provider(User.AuthProvider.LOCAL)
                .emailVerified(true)
                .enabled(true)
                .roles(Set.of(Role.ADMIN))
                .build();

        userRepository.save(admin);
    }
}
