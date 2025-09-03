package org.csu.pixelstrikebackend.lobby.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("friends")
public class Friend {
    @TableId("sender_id")
    private Integer senderId;
    @TableField("addr_id")
    private Integer addrId;
    private String status;
}
