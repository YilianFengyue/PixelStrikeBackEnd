package org.csu.pixelstrikebackend.lobby.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("maps")
public class GameMap {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String name;
    private String mapType;
    private String description;
    private String thumbnailUrl;
}
