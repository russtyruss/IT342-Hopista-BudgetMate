package edu.cit.hopista.campusclinic.security.oauth2;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import edu.cit.hopista.campusclinic.security.JwtTokenProvider;
import edu.cit.hopista.campusclinic.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider tokenProvider;

    @Value("${app.oauth2.authorized-redirect-uri}")
    private String redirectUri;

    @Value("${app.oauth2.mobile-authorized-redirect-uri:}")
    private String mobileRedirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String targetUrl = determineTargetUrl(request, response, authentication);

        if (response.isCommitted()) {
            log.debug("Response has already been committed. Unable to redirect to {}", targetUrl);
            return;
        }

        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    @Override
    protected String determineTargetUrl(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        String token = tokenProvider.generateTokenFromUserId(userPrincipal.getId(), userPrincipal.getEmail());

        String finalRedirectUri = resolveRedirectUri(request);

        return UriComponentsBuilder.fromUriString(finalRedirectUri)
                .queryParam("token", token)
                .build().toUriString();
    }

    private String resolveRedirectUri(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        boolean isMobileBrowser = userAgent != null && userAgent.matches(".*(Android|iPhone|iPad|Mobile).*");

        if (isMobileBrowser && mobileRedirectUri != null && !mobileRedirectUri.isBlank()) {
            return mobileRedirectUri;
        }

        return redirectUri;
    }
}
