package edu.cit.hopista.budgetmate.shared.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.hopista.budgetmate.features.user.entity.User;
import edu.cit.hopista.budgetmate.features.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Load by email (used for form login).
     */
    @Override
    @Transactional
    public UserDetails loadUserByUsername(String emailOrId) throws UsernameNotFoundException {
        // JWT filter passes userId as string; login form passes email
        try {
            Long id = Long.parseLong(emailOrId);
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + id));
            return UserPrincipal.create(user);
        } catch (NumberFormatException e) {
            User user = userRepository.findByEmail(emailOrId)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + emailOrId));
            return UserPrincipal.create(user);
        }
    }

    @Transactional
    public UserDetails loadUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + id));
        return UserPrincipal.create(user);
    }
}
