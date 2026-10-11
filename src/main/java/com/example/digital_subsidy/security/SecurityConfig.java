package com.example.digital_subsidy.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomAuthenticationSuccessHandler successHandler;

    public SecurityConfig(CustomAuthenticationSuccessHandler successHandler) {
        this.successHandler = successHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.ignoringRequestMatchers(
                "/field-officer-login/**",
                "/district-officer-login/**",
                "/finance-login/**",
                "/admin-login/**"
            ))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/",
                    "/register",
                    "/login",
                    "/field-officer-login",
                    "/field-officer-login/**",
                    "/district-officer-login",
                    "/district-officer-login/**",
                    "/finance-login",
                    "/finance-login/**",
                    "/admin-login",
                    "/admin-login/**",
                    "/schemes",
                    "/schemes/**",
                    "/how-it-works",
                    "/eligibility",
                    "/help",
                    "/api/districts/**",
                    "/api/schemes/**",
                    "/css/**",
                    "/js/**",
                    "/images/**",
                    "/webjars/**"
                ).permitAll()
                .requestMatchers("/disbursements/schedule/**").hasAnyRole("FINANCE_APPROVER", "ADMIN", "BENEFICIARY", "FIELD_OFFICER", "DISTRICT_OFFICER")
                .requestMatchers("/compliance/submit").hasRole("BENEFICIARY")
                .requestMatchers("/compliance/verify").hasAnyRole("FIELD_OFFICER", "DISTRICT_OFFICER", "FINANCE_APPROVER", "ADMIN")
                .requestMatchers("/api/analytics/fund-utilization").hasAnyRole("FINANCE_APPROVER", "DISTRICT_OFFICER", "ADMIN")
                .requestMatchers("/reports", "/reports/**").hasAnyRole("ADMIN", "FINANCE_APPROVER", "DISTRICT_OFFICER")
                .requestMatchers("/finance/utilization/**").hasAnyRole("FINANCE_APPROVER", "ADMIN")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/field-officer/**").hasRole("FIELD_OFFICER")
                .requestMatchers("/district-officer/**", "/district_officer/**").hasRole("DISTRICT_OFFICER")
                .requestMatchers("/finance/**").hasRole("FINANCE_APPROVER")
                .requestMatchers("/beneficiary/**").hasRole("BENEFICIARY")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .successHandler(successHandler)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .headers(headers -> headers
                .cacheControl(cache -> {})
            );

        return http.build();
    }
}
