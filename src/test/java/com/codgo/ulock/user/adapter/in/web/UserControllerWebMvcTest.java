package com.codgo.ulock.user.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codgo.ulock.common.security.ProblemDetailSecurityHandlers;
import com.codgo.ulock.common.security.SecurityConfig;
import com.codgo.ulock.common.security.SecurityProperties;
import com.codgo.ulock.common.security.TenantAccess;
import com.codgo.ulock.common.security.TenantIsolationInterceptor;
import com.codgo.ulock.sharedkernel.paging.PageQuery;
import com.codgo.ulock.sharedkernel.paging.PageResult;
import com.codgo.ulock.sharedkernel.valueobject.Email;
import com.codgo.ulock.sharedkernel.valueobject.TenantId;
import com.codgo.ulock.sharedkernel.valueobject.UserId;
import com.codgo.ulock.user.application.port.in.command.CreateUserCommand;
import com.codgo.ulock.user.application.service.UserService;
import com.codgo.ulock.user.application.service.command.UpdateUserCommand;
import com.codgo.ulock.user.domain.exception.EmailAlreadyInUseException;
import com.codgo.ulock.user.domain.exception.UserNotFoundException;
import com.codgo.ulock.user.domain.model.User;
import com.codgo.ulock.user.domain.model.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web layer only: the real security chain, tenant-isolation interceptor and ProblemDetail handler,
 * with the application service mocked.
 */
@WebMvcTest(controllers = UserController.class,
        // The auth slice's JWT converter is a Converter bean, which @WebMvcTest would otherwise pick up.
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.codgo\\.ulock\\.auth\\..*"))
@ActiveProfiles("test")
@Import({SecurityConfig.class, TenantIsolationInterceptor.class, TenantAccess.class,
        ProblemDetailSecurityHandlers.class, UserControllerWebMvcTest.TestBeans.class})
class UserControllerWebMvcTest {

    private static final Instant NOW = Instant.parse("2026-09-23T10:00:00Z");
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();
    private static final String USERS = "/api/v1/tenants/" + TENANT + "/users";

    @TestConfiguration
    @EnableConfigurationProperties(SecurityProperties.class)
    static class TestBeans {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        /** Unused by the jwt() post-processor, but required by the security filter chain. */
        @Bean
        JwtAuthenticationConverter jwtAuthenticationConverter() {
            return new JwtAuthenticationConverter();
        }
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UserService userService;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void listsUsersAsDtosWithTheDefaultSort() throws Exception {
        User user = user("jane@example.test");
        when(userService.list(eq(TenantId.of(TENANT)), eq(null), any()))
                .thenReturn(new PageResult<>(List.of(user), 0, 20, 1));

        mvc.perform(get(USERS).with(admin("user:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(user.id().toString()))
                .andExpect(jsonPath("$.content[0].email").value("jane@example.test"))
                .andExpect(jsonPath("$.content[0].locked").value(false))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(userService).list(TenantId.of(TENANT), null, new PageQuery(0, 20, List.of(
                new PageQuery.Sort("email", PageQuery.Direction.ASC), new PageQuery.Sort("id", PageQuery.Direction.ASC))));
    }

    @Test
    void createsAUserAndReturnsItsLocation() throws Exception {
        User user = user("new@example.test");
        when(userService.create(new CreateUserCommand(TenantId.of(TENANT), "new@example.test", "New", "long-password")))
                .thenReturn(user);

        mvc.perform(post(USERS).with(admin("user:write")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "new@example.test", "fullName": "New", "password": "long-password"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", USERS + "/" + user.id()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void rejectsInvalidPayloadsBeforeReachingTheService() throws Exception {
        mvc.perform(post(USERS).with(admin("user:write")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"not-an-email\", \"fullName\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));
        verify(userService, never()).create(any());
    }

    @Test
    void passesTheCallerAsActorWhenUpdating() throws Exception {
        UserId target = UserId.newId();
        when(userService.update(any())).thenReturn(user("t@example.test"));

        mvc.perform(patch(USERS + "/" + target).with(admin("user:write")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"INACTIVE\"}"))
                .andExpect(status().isOk());

        verify(userService).update(new UpdateUserCommand(TenantId.of(TENANT), target, null, UserStatus.INACTIVE,
                UserId.of(ADMIN)));
    }

    @Test
    void domainExceptionsBecomeProblemDetails() throws Exception {
        UserId missing = UserId.newId();
        when(userService.get(TenantId.of(TENANT), missing)).thenThrow(new UserNotFoundException(missing));
        when(userService.create(any())).thenThrow(new EmailAlreadyInUseException());

        mvc.perform(get(USERS + "/" + missing).with(admin("user:read")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("User " + missing + " was not found"));
        mvc.perform(post(USERS).with(admin("user:write")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"dup@example.test\", \"fullName\": \"D\", \"password\": \"long-password\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void requiresThePermissionForTheOperation() throws Exception {
        mvc.perform(get(USERS).with(admin("role:read"))).andExpect(status().isForbidden());
        mvc.perform(get(USERS)).andExpect(status().isUnauthorized());
        verify(userService, never()).list(any(), any(), any());
    }

    @Test
    void aTokenFromAnotherTenantCannotReadThisTenantsUsers() throws Exception {
        JwtRequestPostProcessor otherTenant = jwt()
                .jwt(token -> token.subject(ADMIN.toString()).claim("tid", UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("user:read"));

        mvc.perform(get(USERS + "/" + UUID.randomUUID()).with(otherTenant))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Access to this tenant is not allowed"));
        verify(userService, never()).get(any(), any());
    }

    private static JwtRequestPostProcessor admin(String... authorities) {
        return jwt()
                .jwt(token -> token.subject(ADMIN.toString()).claim("tid", TENANT.toString()))
                .authorities(Arrays.stream(authorities).<GrantedAuthority>map(SimpleGrantedAuthority::new).toList());
    }

    private static User user(String email) {
        User user = User.register(TenantId.of(TENANT), Email.of(email), "hash", "User", NOW);
        user.pullDomainEvents();
        return user;
    }
}
