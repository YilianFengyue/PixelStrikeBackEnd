package org.csu.pixelstrikebackend.lobby.service;

import org.csu.pixelstrikebackend.lobby.common.CommonResponse;
import org.csu.pixelstrikebackend.lobby.dto.ResetPasswordRequest;
import org.csu.pixelstrikebackend.lobby.dto.UpdateProfileRequest;
import org.csu.pixelstrikebackend.lobby.entity.UserProfile;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {
    CommonResponse<?> resetPassword(ResetPasswordRequest request);
    CommonResponse<?> updateNickname(Integer userId, String newNickname);
    CommonResponse<?> updateAvatar(Integer userId, MultipartFile file);
    CommonResponse<?> deleteAccount(Integer userId);
}
