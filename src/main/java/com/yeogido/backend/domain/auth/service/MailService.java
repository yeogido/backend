package com.yeogido.backend.domain.auth.service;

import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MailService {

  private final JavaMailSender mailSender;

  @Value("${app.mail.from}")
  private String from;

  public void sendPasswordResetCode(String to, String authCode) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(to);
    message.setSubject("[여기도] 비밀번호 재설정 인증번호");
    message.setText("""
      안녕하세요. 여기도입니다.

      비밀번호 재설정 인증번호는 아래와 같습니다.

      인증번호: %s

      인증번호는 5분 동안 유효합니다.
      본인이 요청하지 않았다면 이 메일을 무시해주세요.
      """.formatted(authCode));

    try {
      mailSender.send(message);
    } catch (MailException e) {
      throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR);
    }
  }
}
