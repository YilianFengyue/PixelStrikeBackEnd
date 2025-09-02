package org.csu.pixelstrikebackend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

@Data
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private int id;
    private String username;
    private String email;
    @TableField("hashed_password")
    private String password;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime  createdAt;
    //private String status;

}
