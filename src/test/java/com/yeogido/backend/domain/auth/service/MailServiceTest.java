package com.yeogido.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

  private static final String FROM = "yeogido25@gmail.com";
  private static final String TO = "recipient@example.com";

  @Mock
  private JavaMailSender mailSender;

  private MailService mailService;

  @BeforeEach
  void setUp() {
    mailService = new MailService(mailSender);
    ReflectionTestUtils.setField(mailService, "from", FROM);
  }

  @Test
  void sendEmailVerificationCodeSetsSenderName() throws Exception {
    MimeMessage message = createMimeMessage();
    when(mailSender.createMimeMessage()).thenReturn(message);

    mailService.sendEmailVerificationCode(TO, "123456");

    assertSender(message);
    assertThat(message.getAllRecipients()[0].toString()).isEqualTo(TO);
    assertThat(message.getSubject()).isEqualTo("[여기도] 회원가입 이메일 인증번호");
    verify(mailSender).send(message);
  }

  @Test
  void sendPasswordResetCodeSetsSenderName() throws Exception {
    MimeMessage message = createMimeMessage();
    when(mailSender.createMimeMessage()).thenReturn(message);

    mailService.sendPasswordResetCode(TO, "123456");

    assertSender(message);
    assertThat(message.getAllRecipients()[0].toString()).isEqualTo(TO);
    assertThat(message.getSubject()).isEqualTo("[여기도] 비밀번호 재설정 인증번호");
    verify(mailSender).send(message);
  }

  private MimeMessage createMimeMessage() {
    return new MimeMessage(Session.getInstance(new Properties()));
  }

  private void assertSender(MimeMessage message) throws Exception {
    InternetAddress sender = (InternetAddress) message.getFrom()[0];

    assertThat(sender.getAddress()).isEqualTo(FROM);
    assertThat(sender.getPersonal()).isEqualTo("여기도");
  }
}
