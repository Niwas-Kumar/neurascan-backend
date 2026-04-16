package com.ai.learningdetection.security;

import com.ai.learningdetection.entity.Admin;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class AdminUserDetails implements UserDetails, IdentifiablePrincipal {

    @Getter
    private final String id;
    private final String email;
    private final String password;
    @Getter
    private final String name;
    private final Collection<? extends GrantedAuthority> authorities;

    public AdminUserDetails(Admin admin) {
        this.id          = admin.getId();
        this.email       = admin.getEmail();
        this.password    = admin.getPassword();
        this.name        = admin.getName();
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword()   { return password; }
    @Override public String getUsername()   { return email; }
    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return true; }
}
