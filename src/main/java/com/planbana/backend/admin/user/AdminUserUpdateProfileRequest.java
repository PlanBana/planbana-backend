package com.planbana.backend.admin.user;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public class AdminUserUpdateProfileRequest {
    public String name;
    public String displayName;
    public String avatarUrl;
    public String bio;
    public List<String> languages;
    public Set<String> hobbies;
    public String gender;
    public LocalDate birthDate;
}
