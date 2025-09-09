package com.planbana.backend;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@SpringBootApplication
@EnableMongoAuditing
public class BackendApplication {
  public static void main(String[] args) {
    try {
      Dotenv dotenv = Dotenv.configure()
          .filename(".env") // project root
          .ignoreIfMissing()
          .ignoreIfMalformed()
          .load();

      dotenv.entries().forEach(e -> System.setProperty(e.getKey(), e.getValue()));
    } catch (Exception e) {
      System.err.println("[dotenv] Failed to load .env: " + e.getMessage());
      // Optionally log e.printStackTrace();
    }

    SpringApplication.run(BackendApplication.class, args);
  }

}
