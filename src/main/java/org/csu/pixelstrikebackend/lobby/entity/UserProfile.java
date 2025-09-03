package org.csu.pixelstrikebackend.lobby.entity;


import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("user_profiles")
public class UserProfile {

    @TableId
    private int userId; // 关联到用户表的 ID [cite: 6]

    private String nickname; // 游戏内显示的昵称 [cite: 6]

    private String avatarUrl; // 用户头像的 URL [cite: 6]

    private Integer totalMatches; // 总对战场次 [cite: 6]

    private Integer wins; // 胜利场次 [cite: 6]
}
