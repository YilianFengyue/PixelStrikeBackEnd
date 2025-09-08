package org.csu.pixelstrikebackend.lobby.controller;

import org.csu.pixelstrikebackend.lobby.common.CommonResponse;
import org.csu.pixelstrikebackend.lobby.entity.GameCharacter;
import org.csu.pixelstrikebackend.lobby.entity.GameMap;
import org.csu.pixelstrikebackend.lobby.entity.Weapon;
import org.csu.pixelstrikebackend.lobby.mapper.CharacterMapper;
import org.csu.pixelstrikebackend.lobby.mapper.MapMapper;
import org.csu.pixelstrikebackend.lobby.mapper.WeaponMapper;
import org.csu.pixelstrikebackend.lobby.service.GameDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/game-data")
public class GameDataController {

    @Autowired
    private GameDataService gameDataService;
    @Autowired
    private CharacterMapper characterMapper;
    @Autowired
    private WeaponMapper weaponMapper;
    @Autowired
    private MapMapper mapMapper;

    @GetMapping("/maps")
    public CommonResponse<List<GameMap>> getMaps() {
        return CommonResponse.createForSuccess("获取成功", mapMapper.selectList(null));
    }

    @GetMapping("/maps/types")
    public CommonResponse<List<String>> getMapTypes() {
        return CommonResponse.createForSuccess("获取成功", mapMapper.selectAllMapTypes());
    }

    @GetMapping("/characters")
    public CommonResponse<List<GameCharacter>> getCharacters() {
        return CommonResponse.createForSuccess("获取成功", characterMapper.selectList(null));
    }

    @GetMapping("/characters/{id}")
    public CommonResponse<GameCharacter> getCharacterDetails(@PathVariable Integer id) {
        GameCharacter character = characterMapper.selectById(id);
        return character != null ? CommonResponse.createForSuccess("获取成功", character)
                : CommonResponse.createForError("角色不存在");
    }

    @GetMapping("/weapons")
    public CommonResponse<List<Weapon>> getWeapons() {
        return CommonResponse.createForSuccess("获取成功",weaponMapper.selectList(null));
    }
}
