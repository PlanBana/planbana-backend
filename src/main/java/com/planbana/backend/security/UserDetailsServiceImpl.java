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
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    User u = repo.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("User not found"));
    return new org.springframework.security.core.userdetails.User(
        u.getEmail(),
        u.getPasswordHash(),
        u.getRoles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).collect(Collectors.toList())
    );
  }
}
