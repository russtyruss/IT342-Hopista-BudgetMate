package edu.cit.hopista.campusclinic.features.user.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import edu.cit.hopista.campusclinic.features.user.dto.request.ChangePasswordRequest;
import edu.cit.hopista.campusclinic.features.user.dto.request.UpdateProfileNameRequest;
import edu.cit.hopista.campusclinic.features.user.dto.response.UserResponse;
import edu.cit.hopista.campusclinic.features.user.entity.User;
import edu.cit.hopista.campusclinic.shared.exception.BadRequestException;
import edu.cit.hopista.campusclinic.shared.exception.ResourceNotFoundException;
import edu.cit.hopista.campusclinic.features.budget.repository.BudgetRepository;
import edu.cit.hopista.campusclinic.features.expense.repository.ExpenseRepository;
import edu.cit.hopista.campusclinic.features.user.repository.UserRepository;
import edu.cit.hopista.campusclinic.shared.websocket.AdminUsersNotificationService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;
    private final AdminUsersNotificationService adminUsersNotificationService;
        private final PasswordEncoder passwordEncoder;

        @Value("${app.storage.profile-images-dir:uploads/profile-images}")
        private String profileImagesDir;

        @Value("${app.storage.profile-images-max-size-bytes:5242880}")
        private long profileImagesMaxSizeBytes;

        @Value("${app.supabase.storage.enabled:false}")
        private boolean supabaseStorageEnabled;

        @Value("${app.supabase.url:}")
        private String supabaseUrl;

        @Value("${app.supabase.service-role-key:}")
        private String supabaseServiceRoleKey;

        @Value("${app.supabase.storage.profile-images-bucket:profile-images}")
        private String supabaseProfileImagesBucket;

        private final HttpClient httpClient = HttpClient.newHttpClient();

        private static final Set<String> ALLOWED_PROFILE_CONTENT_TYPES = Set.of(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            "image/jpg",
            "image/webp",
            "image/gif"
        );

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return mapToResponse(user);
    }

    @Transactional
    public UserResponse updateCurrentUserName(Long userId, UpdateProfileNameRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        user.setName(request.getName().trim());
        return mapToResponse(userRepository.save(user));
    }

    @Transactional
    public void changeCurrentUserPassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (!StringUtils.hasText(user.getPassword())) {
            throw new BadRequestException("Password cannot be changed for this account.");
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect.");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BadRequestException("New password must be different from current password.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional
    public UserResponse uploadCurrentUserProfileImage(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please select a profile image file.");
        }

        String contentType = file.getContentType();
        String normalizedContentType = contentType == null ? null : contentType.toLowerCase();
        if (!StringUtils.hasText(normalizedContentType)
            || !ALLOWED_PROFILE_CONTENT_TYPES.contains(normalizedContentType)) {
            throw new BadRequestException("Only JPG, PNG, WEBP, and GIF files are allowed.");
        }

        if (file.getSize() > profileImagesMaxSizeBytes) {
            throw new BadRequestException("File is too large. Maximum allowed size is 5 MB.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (supabaseStorageEnabled) {
            try {
                String extension = resolveExtension(file.getOriginalFilename(), normalizedContentType);
                String storedFileName = "user-" + userId + "-" + System.currentTimeMillis() + extension;
                String objectKey = "users/" + userId + "/" + storedFileName;

                uploadProfileImageToSupabase(objectKey, file, normalizedContentType);
                deleteOldProfileImageFromSupabase(user.getImageUrl());

                user.setImageUrl(objectKey);
                user.setImageContentType(normalizedContentType);
                user.setImageSize(file.getSize());
                user.setImageUploadedAt(LocalDateTime.now());

                return mapToResponse(userRepository.save(user));
            } catch (IOException e) {
                throw new BadRequestException("Failed to upload profile image.");
            }
        }

        Path uploadDir = Paths.get(profileImagesDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadDir);

            String extension = resolveExtension(file.getOriginalFilename(), normalizedContentType);
            String storedFileName = "user-" + userId + "-" + System.currentTimeMillis() + extension;
            Path targetFile = uploadDir.resolve(storedFileName).normalize();

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }

            deleteOldProfileImage(uploadDir, user.getImageUrl());

            user.setImageUrl(storedFileName);
            user.setImageContentType(normalizedContentType);
            user.setImageSize(file.getSize());
            user.setImageUploadedAt(LocalDateTime.now());

            return mapToResponse(userRepository.save(user));
        } catch (IOException e) {
            throw new BadRequestException("Failed to upload profile image.");
        }
    }

    @Transactional(readOnly = true)
    public UserProfileImageData getCurrentUserProfileImage(Long userId) {
        return getUserProfileImageById(userId);
    }

    @Transactional(readOnly = true)
    public UserProfileImageData getUserProfileImageById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (!StringUtils.hasText(user.getImageUrl()) || user.getImageUrl().startsWith("http")) {
            throw new ResourceNotFoundException("Profile image", "userId", userId);
        }

        if (supabaseStorageEnabled) {
            UserProfileImageData supabaseImage = fetchProfileImageFromSupabase(user.getImageUrl(), user.getImageContentType());
            if (supabaseImage == null && !user.getImageUrl().contains("/")) {
                String prefixedKey = "users/" + userId + "/" + user.getImageUrl();
                supabaseImage = fetchProfileImageFromSupabase(prefixedKey, user.getImageContentType());
            }
            if (supabaseImage != null) {
                return supabaseImage;
            }
        }

        Path uploadDir = Paths.get(profileImagesDir).toAbsolutePath().normalize();
        Path filePath = uploadDir.resolve(user.getImageUrl()).normalize();
        if (!filePath.startsWith(uploadDir) || !Files.exists(filePath)) {
            throw new ResourceNotFoundException("Profile image", "userId", userId);
        }

        try {
            String contentType = StringUtils.hasText(user.getImageContentType())
                    ? user.getImageContentType()
                    : Files.probeContentType(filePath);
            if (!StringUtils.hasText(contentType)) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }

            return new UserProfileImageData(Files.readAllBytes(filePath), contentType, filePath.getFileName().toString());
        } catch (IOException e) {
            throw new BadRequestException("Failed to read profile image.");
        }
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional
    public void deleteUser(Long id, Long requestedByUserId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        if (user.getId().equals(requestedByUserId)) {
            throw new BadRequestException("Admin cannot delete their own account.");
        }

        if (user.getRoles().stream().anyMatch(role -> role.name().equals("ADMIN"))) {
            throw new BadRequestException("Admin accounts cannot be deleted.");
        }

        expenseRepository.deleteAllByUserId(id);
        budgetRepository.deleteAllByUserId(id);
        userRepository.delete(user);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    adminUsersNotificationService.notifyUsersUpdate(id);
                }
            });
        } else {
            adminUsersNotificationService.notifyUsersUpdate(id);
        }
    }

    public UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .imageUrl(resolveImageUrl(user))
                .emailVerified(user.getEmailVerified())
                .enabled(user.getEnabled())
                .status(resolveStatus(user))
                .provider(user.getProvider().name())
                .roles(user.getRoles().stream().map(Enum::name).collect(Collectors.toSet()))
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }

    private String resolveStatus(User user) {
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            return "INACTIVE";
        }

        LocalDateTime lastLogin = user.getLastLoginAt();
        if (lastLogin == null) {
            return "INACTIVE";
        }

        return lastLogin.isAfter(LocalDateTime.now().minusDays(30)) ? "ACTIVE" : "INACTIVE";
    }

    private String resolveImageUrl(User user) {
        if (!StringUtils.hasText(user.getImageUrl())) {
            return null;
        }

        if (user.getImageUrl().startsWith("http://") || user.getImageUrl().startsWith("https://")) {
            return user.getImageUrl();
        }

        return "/api/v1/users/" + user.getId() + "/profile-image";
    }

    private void deleteOldProfileImage(Path uploadDir, String existingImageRef) throws IOException {
        if (!StringUtils.hasText(existingImageRef) || existingImageRef.startsWith("http")) {
            return;
        }

        Path oldFile = uploadDir.resolve(existingImageRef).normalize();
        if (oldFile.startsWith(uploadDir)) {
            Files.deleteIfExists(oldFile);
        }
    }

    private void uploadProfileImageToSupabase(String objectKey, MultipartFile file, String contentType) throws IOException {
        validateSupabaseConfig();

        String endpoint = buildSupabaseObjectEndpoint(objectKey);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + supabaseServiceRoleKey)
                .header("apikey", supabaseServiceRoleKey)
                .header("Content-Type", contentType)
                .header("x-upsert", "true")
                .POST(HttpRequest.BodyPublishers.ofByteArray(file.getBytes()))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new BadRequestException("Failed to upload profile image.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("Failed to upload profile image.");
        }
    }

    private UserProfileImageData fetchProfileImageFromSupabase(String objectKey, String fallbackContentType) {
        validateSupabaseConfig();

        String endpoint = buildSupabaseObjectEndpoint(objectKey);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + supabaseServiceRoleKey)
                .header("apikey", supabaseServiceRoleKey)
                .GET()
                .build();

        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 404) {
                return null;
            }

            if (response.statusCode() / 100 != 2) {
                throw new BadRequestException("Failed to read profile image.");
            }

            String contentType = response.headers()
                    .firstValue("Content-Type")
                    .filter(StringUtils::hasText)
                    .orElse(fallbackContentType);

            if (!StringUtils.hasText(contentType)) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }

            String filename = objectKey.contains("/")
                    ? objectKey.substring(objectKey.lastIndexOf('/') + 1)
                    : objectKey;

            return new UserProfileImageData(response.body(), contentType, filename);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("Failed to read profile image.");
        } catch (IOException e) {
            throw new BadRequestException("Failed to read profile image.");
        }
    }

    private void deleteOldProfileImageFromSupabase(String existingImageRef) {
        if (!StringUtils.hasText(existingImageRef) || existingImageRef.startsWith("http")) {
            return;
        }

        validateSupabaseConfig();

        String endpoint = buildSupabaseObjectEndpoint(existingImageRef);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + supabaseServiceRoleKey)
                .header("apikey", supabaseServiceRoleKey)
                .DELETE()
                .build();

        try {
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            // Ignore cleanup failures so upload flow can still succeed.
        }
    }

    private String buildSupabaseObjectEndpoint(String objectKey) {
        String normalizedBaseUrl = supabaseUrl.endsWith("/")
                ? supabaseUrl.substring(0, supabaseUrl.length() - 1)
                : supabaseUrl;
        return normalizedBaseUrl + "/storage/v1/object/" + encodePathSegment(supabaseProfileImagesBucket)
                + "/" + encodeObjectKey(objectKey);
    }

    private String encodeObjectKey(String objectKey) {
        return java.util.Arrays.stream(objectKey.split("/"))
                .map(this::encodePathSegment)
                .collect(Collectors.joining("/"));
    }

    private String encodePathSegment(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void validateSupabaseConfig() {
        if (!StringUtils.hasText(supabaseUrl) || !StringUtils.hasText(supabaseServiceRoleKey)) {
            throw new BadRequestException("Supabase storage is not configured.");
        }
    }

    private String resolveExtension(String originalFilename, String contentType) {
        String cleanName = StringUtils.hasText(originalFilename)
                ? StringUtils.cleanPath(originalFilename)
                : "";

        int lastDot = cleanName.lastIndexOf('.');
        if (lastDot > -1 && lastDot < cleanName.length() - 1) {
            return cleanName.substring(lastDot);
        }

        return switch (contentType.toLowerCase()) {
            case MediaType.IMAGE_JPEG_VALUE, "image/jpg" -> ".jpg";
            case MediaType.IMAGE_PNG_VALUE -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> "";
        };
    }

    public record UserProfileImageData(byte[] bytes, String contentType, String filename) {}
}
