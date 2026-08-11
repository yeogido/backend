package com.yeogido.backend.domain.user.service;

import com.yeogido.backend.domain.user.entity.User;

public interface AdminAuthorizationService {

    User validateAdmin(Long userId);
}
