package org.csu.pixelstrikebackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度必须在3到20位之间")
    private String username;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "密码不能为空")
    // 在这里为 @Size 注解添加 message 属性
    @Size(min = 4, max = 30, message = "密码长度必须在6到30位之间")
    private String password;

    @NotBlank(message = "昵称不能为空")
    @Size(min = 2, max = 20, message = "昵称长度必须在2到20位之间")
    private String nickname;
}
