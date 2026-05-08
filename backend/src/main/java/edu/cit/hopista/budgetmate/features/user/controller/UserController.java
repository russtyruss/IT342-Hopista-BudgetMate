package edu.cit.hopista.budgetmate.features.user.controller;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import edu.cit.hopista.budgetmate.features.user.dto.request.ChangePasswordRequest;
import edu.cit.hopista.budgetmate.features.user.dto.request.UpdateProfileNameRequest;
import edu.cit.hopista.budgetmate.features.user.dto.response.UserResponse;
import edu.cit.hopista.budgetmate.shared.exception.BadRequestException;
import edu.cit.hopista.budgetmate.shared.security.UserPrincipal;
import edu.cit.hopista.budgetmate.features.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** Get the authenticated user's own profile. */
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<UserResponse> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userService.getUserById(principal.getId()));
    }

    @PutMapping("/me/name")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<UserResponse> updateCurrentUserName(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateProfileNameRequest request) {
        return ResponseEntity.ok(userService.updateCurrentUserName(principal.getId(), request));
    }

    @PutMapping("/me/password")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<Void> changeCurrentUserPassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changeCurrentUserPassword(principal.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<UserResponse> uploadCurrentUserProfileImage(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(userService.uploadCurrentUserProfileImage(principal.getId(), file));
    }

    @GetMapping("/me/profile-image")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<Resource> getCurrentUserProfileImage(
            @AuthenticationPrincipal UserPrincipal principal) {
        UserService.UserProfileImageData imageData = userService.getCurrentUserProfileImage(principal.getId());
        Resource resource = new ByteArrayResource(imageData.bytes()) {
            @Override
            public String getFilename() {
                return imageData.filename();
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + imageData.filename() + "\"")
                .contentType(MediaType.parseMediaType(imageData.contentType()))
                .contentLength(imageData.bytes().length)
                .body(resource);
    }

    @GetMapping("/{id}/profile-image")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<Resource> getUserProfileImageById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        boolean isAdmin = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);

        if (!isAdmin && !principal.getId().equals(id)) {
            throw new BadRequestException("You can only access your own profile image.");
        }

        UserService.UserProfileImageData imageData = userService.getUserProfileImageById(id);
        Resource resource = new ByteArrayResource(imageData.bytes()) {
            @Override
            public String getFilename() {
                return imageData.filename();
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + imageData.filename() + "\"")
                .contentType(MediaType.parseMediaType(imageData.contentType()))
                .contentLength(imageData.bytes().length)
                .body(resource);
    }

    /** Admin: get any user by ID. */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    /** Admin: list all users with pagination. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserResponse>> getAllUsers(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(userService.getAllUsers(pageable));
    }

    /** Admin: delete a user. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        userService.deleteUser(id, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
