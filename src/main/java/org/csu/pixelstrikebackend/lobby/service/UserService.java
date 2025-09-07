package org.csu.pixelstrikebackend.lobby.service;

import org.csu.pixelstrikebackend.lobby.common.CommonResponse;
import org.csu.pixelstrikebackend.lobby.dto.ResetPasswordRequest;
import org.csu.pixelstrikebackend.lobby.dto.UpdateProfileRequest;
import org.csu.pixelstrikebackend.lobby.entity.UserProfile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

public interface UserService {
    CommonResponse<?> resetPassword(ResetPasswordRequest request);
    CommonResponse<?> updateNickname(Integer userId, String newNickname);
    Mono<CommonResponse<?>> updateAvatar(Integer userId, FilePart file);
    CommonResponse<?> deleteAccount(Integer userId);
}
