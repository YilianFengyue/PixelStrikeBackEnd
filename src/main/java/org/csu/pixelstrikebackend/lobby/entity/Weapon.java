package org.csu.pixelstrikebackend.lobby.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("weapons")
public class Weapon {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String name;
    private Integer damage;
    private Float fireRate;
    private Float recoil;
    private Integer ammoCapacity;
}