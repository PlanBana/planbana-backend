package com.planbana.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.core.io.ClassPathResource;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FirebaseConfig {

  @PostConstruct
  public void initFirebase() {
    if (!FirebaseApp.getApps().isEmpty()) return;
    try {
      var serviceAccount = new ClassPathResource("firebase-service-account.json");
      FirebaseOptions options = FirebaseOptions.builder()
          .setCredentials(GoogleCredentials.fromStream(serviceAccount.getInputStream()))
          .build();
      FirebaseApp.initializeApp(options);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to initialize Firebase Admin SDK", e);
    }
  }
}
