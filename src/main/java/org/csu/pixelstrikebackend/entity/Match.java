package org.csu.pixelstrikebackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("matches")
public class Match {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("game_mode")
    private String gameMode;
    @TableField("map_name")
    private String mapName;
    @TableField("start_time")
    private LocalDateTime startTime;
    @TableField("end_time")
    private LocalDateTime endTime;
}
