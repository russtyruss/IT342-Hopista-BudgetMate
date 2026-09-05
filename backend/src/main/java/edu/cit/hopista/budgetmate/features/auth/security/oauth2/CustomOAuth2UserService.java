package edu.cit.hopista.budgetmate.features.auth.security.oauth2;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import edu.cit.hopista.budgetmate.features.user.entity.Role;
import edu.cit.hopista.budgetmate.features.user.entity.User;
import edu.cit.hopista.budgetmate.features.user.repository.UserRepository;
import edu.cit.hopista.budgetmate.shared.security.UserPrincipal;
import edu.cit.hopista.budgetmate.features.auth.service.EmailService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final EmailService emailService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        return processOAuth2User(userRequest, oAuth2User);
    }

    private OAuth2User processOAuth2User(OAuth2UserRequest userRequest, OAuth2User oAuth2User) {
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String email = (String) attributes.get("email");
        String name  = (String) attributes.get("name");
        String picture = (String) attributes.get("picture");
        String providerId = (String) attributes.get("sub");

        Optional<User> userOptional = userRepository.findByEmail(email);
        User user;

        if (userOptional.isPresent()) {
            user = userOptional.get();
            // Update profile info
            user.setName(name);
            user.setImageUrl(picture);
            user.setEmailVerified(true);
        } else {
            Set<Role> roles = new HashSet<>();
            roles.add(Role.USER);
            user = User.builder()
                    .name(name)
                    .email(email)
                    .imageUrl(picture)
                    .emailVerified(true)
                    .provider(User.AuthProvider.GOOGLE)
                    .providerId(providerId)
                    .roles(roles)
                    .build();

            // First-time OAuth registration should also receive a welcome email.
            emailService.sendWelcomeEmail(email, name);
        }

        user = userRepository.save(user);
        return UserPrincipal.create(user, attributes);
    }
}
