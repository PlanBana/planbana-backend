package com.planbana.backend.config;

import org.springframework.context.annotation.Configuration;

/**
 * Static upload serving has moved to {@code storage.UploadsController}
 * (GET /uploads/**), which resolves absolute paths directly and is robust to
 * Windows paths that contain spaces.
 */
@Configuration
public class WebConfig {
}
