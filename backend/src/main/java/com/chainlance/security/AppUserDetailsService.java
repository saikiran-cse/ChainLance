package com.chainlance.security;

import com.chainlance.repository.UserRepository;
import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) {
        this.users = users;
    }

    /** Used by the login authentication; the "username" is the email address. */
    @Override
    public AppUserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return users.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .map(AppUserDetails::from)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    /** Used by the JWT filter on every request, so disabled or deleted users stop working immediately. */
    public AppUserDetails loadById(Long id) throws UsernameNotFoundException {
        return users.findById(id)
                .map(AppUserDetails::from)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
