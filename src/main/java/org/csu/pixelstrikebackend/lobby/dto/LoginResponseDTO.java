package org.csu.pixelstrikebackend.lobby.dto;

import lombok.Data;
import org.csu.pixelstrikebackend.lobby.entity.UserProfile;

@Data
public class LoginResponseDTO {
    private String token;
    private UserProfile userProfile;
}
