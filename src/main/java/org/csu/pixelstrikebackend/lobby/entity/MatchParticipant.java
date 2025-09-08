package org.csu.pixelstrikebackend.lobby.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("match_participants")
public class MatchParticipant {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("match_id")
    private Long matchId;
    @TableField("character_id") // 明确数据库字段名
    private Integer characterId;
    @TableField("user_id")
    private Integer userId;
    private Integer ranking;
    private Integer kills;
    private Integer deaths;
}
