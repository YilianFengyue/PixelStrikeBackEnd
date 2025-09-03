package org.csu.pixelstrikebackend.service;

import org.csu.pixelstrikebackend.lobby.common.CommonResponse;
import org.csu.pixelstrikebackend.dto.ResetPasswordRequest;
import org.csu.pixelstrikebackend.dto.UpdateProfileRequest;
import org.csu.pixelstrikebackend.entity.UserProfile;

public interface UserService {
    CommonResponse<?> resetPassword(ResetPasswordRequest request);
    CommonResponse<UserProfile> updateUserProfile(Integer userId, UpdateProfileRequest request);
    CommonResponse<?> deleteAccount(Integer userId);
}
