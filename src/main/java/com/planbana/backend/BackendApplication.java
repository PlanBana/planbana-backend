package com.planbana.backend;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@SpringBootApplication
@EnableMongoAuditing
public class BackendApplication {
  public static void main(String[] args) {
    // ✅ Load .env into system properties before Spring starts
    Dotenv dotenv = Dotenv.configure()
                          .filename(".env") // Looks for .env file in project root
                          .ignoreIfMissing() // Don't crash if file is missing
                          .ignoreIfMalformed() // Skip malformed lines
                          .load();

    dotenv.entries().forEach(entry ->
      System.setProperty(entry.getKey(), entry.getValue())
    );

    SpringApplication.run(BackendApplication.class, args);
  }
}
