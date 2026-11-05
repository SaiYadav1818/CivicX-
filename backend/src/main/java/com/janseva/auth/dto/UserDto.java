package com.janseva.auth.dto;

public record UserDto(
        Long id,
        String fullName,
        String email,
        String phone
) {
}
