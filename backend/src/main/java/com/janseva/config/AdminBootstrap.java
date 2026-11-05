package com.janseva.config;

import com.janseva.user.Role;
import com.janseva.user.RoleName;
import com.janseva.user.RoleRepository;
import com.janseva.user.User;
import com.janseva.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    @Bean
    CommandLineRunner bootstrapAdmin(UserRepository userRepository,
                                     RoleRepository roleRepository,
                                     PasswordEncoder passwordEncoder,
                                     AppProperties properties) {
        return args -> {
            if (userRepository.existsByEmail(properties.admin().email())) {
                return;
            }
            Role adminRole = roleRepository.findByName(RoleName.ADMIN)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role is not seeded"));
            User admin = new User(
                    properties.admin().email(),
                    passwordEncoder.encode(properties.admin().password()),
                    null,
                    "Platform Administrator"
            );
            admin.addRole(adminRole);
            userRepository.save(admin);
            log.info("Default admin account created (email: {})", properties.admin().email());
        };
    }
}
