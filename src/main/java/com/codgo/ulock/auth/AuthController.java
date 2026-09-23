package com.codgo.ulock.auth;

import com.codgo.ulock.auth.AuthDtos.ClientTokenResponse;
import com.codgo.ulock.auth.AuthDtos.LoginRequest;
import com.codgo.ulock.auth.AuthDtos.RefreshRequest;
import com.codgo.ulock.auth.AuthDtos.TokenResponse;
import com.codgo.ulock.common.error.InvalidRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private static final String CLIENT_CREDENTIALS = "client_credentials";

    private final AuthService authService;
    private final MachineClientService machineClients;

    AuthController(AuthService authService, MachineClientService machineClients) {
        this.authService = authService;
        this.machineClients = machineClients;
    }

    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return noStore(authService.login(request));
    }

    @PostMapping("/refresh")
    ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return noStore(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /** Client-credentials grant for machine clients, in the standard OAuth 2.0 form encoding. */
    @PostMapping(path = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<ClientTokenResponse> token(@RequestParam("grant_type") String grantType,
                                              @RequestParam("client_id") String clientId,
                                              @RequestParam("client_secret") String clientSecret,
                                              HttpServletRequest request) {
        // @RequestParam also binds the query string, which ends up in proxy and access logs.
        if (queryParameterNames(request).contains("client_secret")) {
            throw new InvalidRequestException("client_secret must be sent in the request body");
        }
        if (!CLIENT_CREDENTIALS.equals(grantType)) {
            throw new InvalidRequestException("Unsupported grant_type; only client_credentials is supported");
        }
        return noStore(machineClients.issueToken(clientId, clientSecret));
    }

    private static Set<String> queryParameterNames(HttpServletRequest request) {
        if (request.getQueryString() == null) {
            return Set.of();
        }
        return UriComponentsBuilder.newInstance().query(request.getQueryString()).build().getQueryParams().keySet()
                .stream()
                .map(name -> URLDecoder.decode(name, StandardCharsets.UTF_8))
                .collect(Collectors.toSet());
    }

    /** RFC 6749 section 5.1: responses carrying tokens must not be cached. */
    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
