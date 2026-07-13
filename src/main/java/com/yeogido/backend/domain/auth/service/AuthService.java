package com.yeogido.backend.domain.auth.service;

import com.yeogido.backend.domain.auth.dto.AuthReqDTO;
import com.yeogido.backend.domain.auth.dto.AuthResDTO;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  public AuthResDTO.SignUp signUp(AuthReqDTO.SignUp request) {
    return new AuthResDTO.SignUp(1L);
  }

  public AuthResDTO.Token login(AuthReqDTO.Login request) {
    return new AuthResDTO.Token(
      1L,
      "mock_access_token_for_" + request.email(),
      "mock_refresh_token_for_" + request.email()
    );
  }

  public AuthResDTO.Token socialLogin(AuthReqDTO.SocialLogin request) {
    return new AuthResDTO.Token(
      2L,
      "mock_social_access_token",
      "mock_social_refresh_token"
    );
  }

  public AuthResDTO.EmailCheck checkEmail(String email) {
    return new AuthResDTO.EmailCheck(true);
  }

  public void logout(String authorization) {
  }

  public void sendResetCode(AuthReqDTO.PasswordSendCode request) {
  }

  public AuthResDTO.PasswordVerifyCode verifyResetCode(AuthReqDTO.PasswordVerifyCode request) {
    return new AuthResDTO.PasswordVerifyCode("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
  }
}
