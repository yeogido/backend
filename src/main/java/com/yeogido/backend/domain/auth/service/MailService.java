package com.yeogido.backend.domain.auth.service;

import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MailService {

  private static final String SENDER_NAME = "여기도";

  private final JavaMailSender mailSender;

  @Value("${app.mail.from}")
  private String from;

  public void sendEmailVerificationCode(String to, String authCode) {
    send(
      to,
      "[여기도] 회원가입 이메일 인증번호",
      """
      안녕하세요. 여기도입니다.

      회원가입 이메일 인증번호는 아래와 같습니다.

      인증번호: %s

      인증번호는 5분 동안 유효합니다.
      본인이 요청하지 않았다면 이 메일을 무시해주세요.
      """.formatted(authCode)
    );
  }

  public void sendPasswordResetCode(String to, String authCode) {
    send(
      to,
      "[여기도] 비밀번호 재설정 인증번호",
      """
      안녕하세요. 여기도입니다.

      비밀번호 재설정 인증번호는 아래와 같습니다.

      인증번호: %s

      인증번호는 5분 동안 유효합니다.
      본인이 요청하지 않았다면 이 메일을 무시해주세요.
      """.formatted(authCode)
    );
  }

  private void send(String to, String subject, String text) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

      helper.setFrom(from, SENDER_NAME);
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(text);

      mailSender.send(message);
    } catch (MessagingException | UnsupportedEncodingException | MailException e) {
      throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR);
    }
  }
}
