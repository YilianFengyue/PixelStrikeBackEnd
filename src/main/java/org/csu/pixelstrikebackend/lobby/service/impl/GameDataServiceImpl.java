package org.csu.pixelstrikebackend.lobby.service.impl;

import org.csu.pixelstrikebackend.lobby.common.CommonResponse;
import org.csu.pixelstrikebackend.lobby.entity.GameCharacter;
import org.csu.pixelstrikebackend.lobby.entity.Weapon;
import org.csu.pixelstrikebackend.lobby.mapper.CharacterMapper;
import org.csu.pixelstrikebackend.lobby.mapper.WeaponMapper;
import org.csu.pixelstrikebackend.lobby.service.GameDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GameDataServiceImpl implements GameDataService {

    @Autowired
    private CharacterMapper characterMapper;
    @Autowired
    private WeaponMapper weaponMapper;

    @Override
    public CommonResponse<?> getAllCharacters() {
        List<GameCharacter> characters = characterMapper.selectList(null);
        return CommonResponse.createForSuccess("获取成功", characters);
    }

    @Override
    public CommonResponse<?> getAllWeapons() {
        List<Weapon> weapons = weaponMapper.selectList(null);
        return CommonResponse.createForSuccess("获取成功", weapons);
    }
}
