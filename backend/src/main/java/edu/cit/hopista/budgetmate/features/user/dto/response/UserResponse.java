package edu.cit.hopista.budgetmate.features.user.dto.response;

import java.time.LocalDateTime;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    private Long id;
    private String name;
    private String email;
    private String imageUrl;
    private Boolean emailVerified;
    private Boolean enabled;
    private String status;
    private Set<String> roles;
    private String provider;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
}
