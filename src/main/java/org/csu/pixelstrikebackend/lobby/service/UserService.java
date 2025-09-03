package org.csu.pixelstrikebackend.lobby.service;

import org.csu.pixelstrikebackend.lobby.common.CommonResponse;
import org.csu.pixelstrikebackend.lobby.dto.ResetPasswordRequest;
import org.csu.pixelstrikebackend.lobby.dto.UpdateProfileRequest;
import org.csu.pixelstrikebackend.lobby.entity.UserProfile;

public interface UserService {
    CommonResponse<?> resetPassword(ResetPasswordRequest request);
    CommonResponse<UserProfile> updateUserProfile(Integer userId, UpdateProfileRequest request);
    CommonResponse<?> deleteAccount(Integer userId);
}
