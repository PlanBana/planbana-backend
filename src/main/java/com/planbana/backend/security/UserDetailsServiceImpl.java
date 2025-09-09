package com.planbana.backend.security;

import com.planbana.backend.user.User;
import com.planbana.backend.user.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

  private final UserRepository repo;

  public UserDetailsServiceImpl(UserRepository repo) {
    this.repo = repo;
  }

  @Override
  public UserDetails loadUserByUsername(String phone) throws UsernameNotFoundException {
    User u = repo.findByPhone(phone)
        .orElseThrow(() -> new UsernameNotFoundException("User not found with phone: " + phone));

    return new org.springframework.security.core.userdetails.User(
        u.getPhone(),
        u.getPasswordHash(),
        u.getRoles().stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .collect(Collectors.toSet())
    );
  }
}
