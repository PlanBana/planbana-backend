package com.planbana.backend.auth;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {
  private final JavaMailSender mailSender;

  public MailService(JavaMailSender mailSender) {
    this.mailSender = mailSender;
  }

  public void sendSimple(String to, String subject, String text) {
    try {
      SimpleMailMessage msg = new SimpleMailMessage();
      msg.setTo(to);
      msg.setSubject(subject);
      msg.setText(text);
      mailSender.send(msg);
    } catch (Exception e) {
      // In dev, mail may not be configured. Log and continue.
      System.out.println("[MailService] Could not send: " + e.getMessage());
    }
  }
}
