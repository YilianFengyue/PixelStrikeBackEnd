package org.csu.pixelstrikebackend.lobby.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("characters")
public class GameCharacter {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String name;
    private String description;
    private Integer health;
    private Float speed;
    private Float jumpHeight;
    @TableField("default_weapon_id")
    private Integer defaultWeaponId;
}