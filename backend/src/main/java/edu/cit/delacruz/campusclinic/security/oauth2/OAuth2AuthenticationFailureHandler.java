package edu.cit.delacruz.campusclinic.security.oauth2;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Value("${app.oauth2.authorized-redirect-uri}")
    private String redirectUri;

    @Value("${app.oauth2.mobile-authorized-redirect-uri:}")
    private String mobileRedirectUri;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String targetUrl = UriComponentsBuilder.fromUriString(resolveRedirectUri(request))
                .queryParam("error", URLEncoder.encode(exception.getLocalizedMessage(), StandardCharsets.UTF_8))
                .build().toUriString();

        log.error("OAuth2 authentication failed: {}", exception.getMessage());
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
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
