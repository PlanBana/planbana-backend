package com.planbana.backend.admin.settings;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SystemSettingsRepository extends MongoRepository<SystemSettings, String> {
}
