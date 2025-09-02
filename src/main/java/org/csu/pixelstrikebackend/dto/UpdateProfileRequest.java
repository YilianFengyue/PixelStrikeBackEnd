package org.csu.pixelstrikebackend.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(min = 2, max = 20, message = "昵称长度必须在2到20位之间")
    private String nickname;

    private String avatarUrl;
}
