package com.example.digital_subsidy.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String role = authority.getAuthority();
            if (role.equals("ROLE_ADMIN")) {
                response.sendRedirect("/admin/dashboard");
                return;
            } else if (role.equals("ROLE_FIELD_OFFICER")) {
                response.sendRedirect("/field-officer/dashboard");
                return;
            } else if (role.equals("ROLE_DISTRICT_OFFICER")) {
                response.sendRedirect("/district-officer/dashboard");
                return;
            } else if (role.equals("ROLE_FINANCE_APPROVER")) {
                response.sendRedirect("/finance/dashboard");
                return;
            } else if (role.equals("ROLE_BENEFICIARY")) {
                response.sendRedirect("/beneficiary/dashboard");
                return;
            }
        }
        response.sendRedirect("/");
    }
}
