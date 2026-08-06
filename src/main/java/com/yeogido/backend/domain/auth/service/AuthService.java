package com.yeogido.backend.domain.auth.service;

import com.yeogido.backend.domain.auth.dto.AuthReqDTO;
import com.yeogido.backend.domain.auth.dto.AuthResDTO;
import com.yeogido.backend.domain.auth.security.AuthUser;

public interface AuthService {

  AuthResDTO.SignUp signUp(AuthReqDTO.SignUp request);

  AuthResDTO.Token login(AuthReqDTO.Login request);

  AuthResDTO.SocialLogin socialLogin(AuthReqDTO.SocialLogin request);

  AuthResDTO.Token completeSocialSignup(AuthReqDTO.SocialSignupComplete request);

  AuthResDTO.EmailCheck checkEmail(String email);

  void logout(AuthUser authUser);

  AuthResDTO.Token reissue(AuthReqDTO.Reissue request);

  void sendEmailVerificationCode(AuthReqDTO.EmailSendCode request);

  AuthResDTO.EmailVerifyCode verifyEmailCode(AuthReqDTO.EmailVerifyCode request);

  void sendResetCode(AuthReqDTO.PasswordSendCode request);

  AuthResDTO.PasswordVerifyCode verifyResetCode(AuthReqDTO.PasswordVerifyCode request);

  void resetPassword(AuthReqDTO.PasswordReset request);
}
