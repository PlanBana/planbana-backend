package com.planbana.backend.events.dto;

public class ParticipantDto {

    private final String id;
    private final String name;
    private final String avatar;

    public ParticipantDto(String id, String name, String avatar) {
        this.id = id;
        this.name = name;
        this.avatar = avatar;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAvatar() {
        return avatar;
    }
}
