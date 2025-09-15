/*
 Navicat Premium Dump SQL

 Source Server         : local-mysql
 Source Server Type    : MySQL
 Source Server Version : 80040 (8.0.40)
 Source Host           : localhost:3306
 Source Schema         : pixelstrike

 Target Server Type    : MySQL
 Target Server Version : 80040 (8.0.40)
 File Encoding         : 65001

 Date: 08/09/2025 20:46:32
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for characters
-- ----------------------------
DROP TABLE IF EXISTS `characters`;
CREATE TABLE `characters`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色名称',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '角色描述',
  `health` int NOT NULL COMMENT '基础生命值',
  `speed` float NOT NULL COMMENT '基础移动速度',
  `jump_height` float NOT NULL COMMENT '基础跳跃高度',
  `default_weapon_id` int NULL DEFAULT NULL COMMENT '默认持有武器ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `name`(`name` ASC) USING BTREE,
  INDEX `fk_character_weapon`(`default_weapon_id` ASC) USING BTREE,
  CONSTRAINT `fk_character_weapon` FOREIGN KEY (`default_weapon_id`) REFERENCES `weapons` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '游戏内可用角色列表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for friends
-- ----------------------------
DROP TABLE IF EXISTS `friends`;
CREATE TABLE `friends`  (
  `sender_id` bigint NOT NULL,
  `addr_id` bigint NOT NULL,
  `status` enum('pending','accepted') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'pending' COMMENT '关系状态（请求中、已接受）',
  PRIMARY KEY (`sender_id`, `addr_id`) USING BTREE,
  INDEX `fk_friends_addr`(`addr_id` ASC) USING BTREE,
  INDEX `idx_friends_status`(`status` ASC) USING BTREE,
  CONSTRAINT `fk_friends_addr` FOREIGN KEY (`addr_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_friends_sender` FOREIGN KEY (`sender_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for maps
-- ----------------------------
DROP TABLE IF EXISTS `maps`;
CREATE TABLE `maps`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '地图名称',
  `map_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '标准对战' COMMENT '地图类型/模式',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '地图描述',
  `thumbnail_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '地图缩略图URL',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `name`(`name` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '游戏内可用地图信息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for match_participants
-- ----------------------------
DROP TABLE IF EXISTS `match_participants`;
CREATE TABLE `match_participants`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `match_id` bigint NOT NULL,
  `user_id` bigint NULL DEFAULT NULL,
  `character_id` int NULL DEFAULT NULL COMMENT '玩家在本局使用的角色ID',
  `kills` int NOT NULL DEFAULT 0 COMMENT '击杀数',
  `deaths` int NOT NULL DEFAULT 0 COMMENT '死亡数',
  `ranking` int NOT NULL COMMENT '对战的名次',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_match_participants_match_id`(`match_id` ASC) USING BTREE,
  INDEX `idx_match_participants_user_id`(`user_id` ASC) USING BTREE,
  INDEX `fk_participants_character`(`character_id` ASC) USING BTREE,
  CONSTRAINT `fk_match_participants_matches` FOREIGN KEY (`match_id`) REFERENCES `matches` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_match_participants_users` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `fk_participants_character` FOREIGN KEY (`character_id`) REFERENCES `characters` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 39 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for matches
-- ----------------------------
DROP TABLE IF EXISTS `matches`;
CREATE TABLE `matches`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `game_mode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '游戏模式（如 \'匹配\', \'开房间\'）',
  `start_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end_time` timestamp NULL DEFAULT NULL,
  `map_id` int NULL DEFAULT NULL COMMENT '地图ID',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_matches_game_mode`(`game_mode` ASC) USING BTREE,
  INDEX `fk_match_map`(`map_id` ASC) USING BTREE,
  CONSTRAINT `fk_match_map` FOREIGN KEY (`map_id`) REFERENCES `maps` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 46 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_profiles
-- ----------------------------
DROP TABLE IF EXISTS `user_profiles`;
CREATE TABLE `user_profiles`  (
  `user_id` bigint NOT NULL,
  `nickname` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '游戏内显示的昵称，可以重复',
  `avatar_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `total_matches` int NOT NULL DEFAULT 0,
  `wins` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`) USING BTREE,
  INDEX `idx_user_profiles_nickname`(`nickname` ASC) USING BTREE,
  CONSTRAINT `fk_user_profiles_users` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `hashed_password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `username`(`username` ASC) USING BTREE,
  UNIQUE INDEX `email`(`email` ASC) USING BTREE,
  INDEX `idx_users_username`(`username` ASC) USING BTREE,
  INDEX `idx_users_email`(`email` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for weapons
-- ----------------------------
DROP TABLE IF EXISTS `weapons`;
CREATE TABLE `weapons`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '武器名称',
  `damage` int NOT NULL COMMENT '单发伤害',
  `fire_rate` float NOT NULL COMMENT '射速 (每秒发射次数)',
  `recoil` float NOT NULL COMMENT '后坐力',
  `ammo_capacity` int NOT NULL COMMENT '弹夹容量',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `name`(`name` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '游戏内武器定义表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;

-- ----------------------------
-- Records of characters
-- ----------------------------
INSERT INTO `characters` VALUES (1, 'Ash', '均衡型角色', 100, 7.0, 15.0, 1);
INSERT INTO `characters` VALUES (2, 'Shu', '敏捷型角色', 90, 8.0, 16.0, 2);
INSERT INTO `characters` VALUES (3, 'Angel Neng', '火力型角色', 110, 6.5, 14.0, 3);
INSERT INTO `characters` VALUES (4, 'Blue Archive Marthe', '战术型角色', 100, 7.0, 15.0, 1);

