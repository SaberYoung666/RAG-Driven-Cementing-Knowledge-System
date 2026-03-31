package org.swpu.backend.modules.auth.service;

import org.swpu.backend.modules.auth.dto.LoginRequest;
import org.swpu.backend.modules.auth.dto.RegisterRequest;
import org.swpu.backend.modules.auth.vo.AuthTokenVo;
import org.swpu.backend.modules.auth.vo.UsernameCheckVo;
import org.swpu.backend.modules.auth.vo.UserProfileVo;

public interface AuthService {
    AuthTokenVo register(RegisterRequest request);

    AuthTokenVo login(LoginRequest request);

    UserProfileVo me(String bearerToken);

    UsernameCheckVo checkUsernameAvailability(String username);
}
