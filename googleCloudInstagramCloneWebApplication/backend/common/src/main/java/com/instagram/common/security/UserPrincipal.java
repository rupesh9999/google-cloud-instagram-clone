package com.instagram.common.security;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.security.Principal;

@Data
@AllArgsConstructor
public class UserPrincipal implements Principal {
    private String id;
    private String username;

    @Override
    public String getName() {
        return username;
    }
}
