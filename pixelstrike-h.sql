/*
 Navicat Premium Data Transfer

 Source Server         : localhost_3306
 Source Server Type    : MySQL
 Source Server Version : 80042 (8.0.42)
 Source Host           : localhost:3306
 Source Schema         : pixelstrike

 Target Server Type    : MySQL
 Target Server Version : 80042 (8.0.42)
 File Encoding         : 65001

 Date: 06/09/2025 09:59:51
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

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
-- Records of friends
-- ----------------------------
INSERT INTO `friends` VALUES (1, 4, 'pending');
INSERT INTO `friends` VALUES (5, 2, 'pending');
INSERT INTO `friends` VALUES (1, 2, 'accepted');
INSERT INTO `friends` VALUES (3, 1, 'accepted');

-- ----------------------------
-- Table structure for match_participants
-- ----------------------------
DROP TABLE IF EXISTS `match_participants`;
CREATE TABLE `match_participants`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `match_id` bigint NOT NULL,
  `user_id` bigint NULL DEFAULT NULL,
  `kills` int NOT NULL DEFAULT 0 COMMENT '击杀数',
  `deaths` int NOT NULL DEFAULT 0 COMMENT '死亡数',
  `ranking` int NULL DEFAULT NULL COMMENT '本局排名',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_match_participants_match_id`(`match_id` ASC) USING BTREE,
  INDEX `idx_match_participants_user_id`(`user_id` ASC) USING BTREE,
  CONSTRAINT `fk_match_participants_matches` FOREIGN KEY (`match_id`) REFERENCES `matches` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_match_participants_users` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of match_participants
-- ----------------------------
INSERT INTO `match_participants` VALUES (1, 17, 8, 0, 1, NULL);
INSERT INTO `match_participants` VALUES (2, 17, 9, 0, 1, NULL);
INSERT INTO `match_participants` VALUES (3, 17, 6, 2, 0, NULL);
INSERT INTO `match_participants` VALUES (4, 18, 8, 2, 0, 1);
INSERT INTO `match_participants` VALUES (5, 18, 9, 0, 1, 2);
INSERT INTO `match_participants` VALUES (6, 18, 6, 0, 1, 3);

-- ----------------------------
-- Table structure for matches
-- ----------------------------
DROP TABLE IF EXISTS `matches`;
CREATE TABLE `matches`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `game_mode` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '游戏模式（如 \'匹配\', \'开房间\'）',
  `map_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '	地图名称',
  `start_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end_time` timestamp NULL DEFAULT NULL,
  `ranking` int NULL DEFAULT NULL COMMENT '对战的名次',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_matches_game_mode`(`game_mode` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 19 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of matches
-- ----------------------------
INSERT INTO `matches` VALUES (1, '匹配', '城堡', '2025-09-03 08:00:00', '2025-09-03 08:15:00', NULL);
INSERT INTO `matches` VALUES (2, '开房间', '雪地', '2025-09-03 08:20:00', '2025-09-03 08:30:00', NULL);
INSERT INTO `matches` VALUES (3, '匹配', '默认地图', '2025-09-05 09:06:17', NULL, NULL);
INSERT INTO `matches` VALUES (4, '匹配', '默认地图', '2025-09-05 09:21:03', NULL, NULL);
INSERT INTO `matches` VALUES (5, '匹配', '默认地图', '2025-09-05 09:39:00', NULL, NULL);
INSERT INTO `matches` VALUES (6, '匹配', '默认地图', '2025-09-05 09:49:09', NULL, NULL);
INSERT INTO `matches` VALUES (7, '匹配', '默认地图', '2025-09-05 09:52:36', NULL, NULL);
INSERT INTO `matches` VALUES (8, '匹配', '默认地图', '2025-09-05 09:59:37', NULL, NULL);
INSERT INTO `matches` VALUES (9, '匹配', '默认地图', '2025-09-05 10:58:20', NULL, NULL);
INSERT INTO `matches` VALUES (10, '匹配', '默认地图', '2025-09-05 11:00:45', '2025-09-05 11:05:45', NULL);
INSERT INTO `matches` VALUES (11, '匹配', '默认地图', '2025-09-05 11:02:07', NULL, NULL);
INSERT INTO `matches` VALUES (12, '匹配', '默认地图', '2025-09-05 11:08:06', '2025-09-05 11:13:06', NULL);
INSERT INTO `matches` VALUES (13, '匹配', '默认地图', '2025-09-05 11:15:02', '2025-09-05 11:16:16', NULL);
INSERT INTO `matches` VALUES (14, '匹配', '默认地图', '2025-09-06 09:06:56', '2025-09-06 09:07:39', NULL);
INSERT INTO `matches` VALUES (15, '匹配', '默认地图', '2025-09-06 09:11:14', '2025-09-06 09:16:14', NULL);
INSERT INTO `matches` VALUES (16, '匹配', '默认地图', '2025-09-06 09:12:17', '2025-09-06 09:12:49', NULL);
INSERT INTO `matches` VALUES (17, '匹配', '默认地图', '2025-09-06 09:17:36', '2025-09-06 09:17:46', NULL);
INSERT INTO `matches` VALUES (18, '匹配', '默认地图', '2025-09-06 09:53:29', '2025-09-06 09:53:52', NULL);

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
-- Records of user_profiles
-- ----------------------------
INSERT INTO `user_profiles` VALUES (1, '闪电侠', 'http://example.com/avatar1.png', 2, 1);
INSERT INTO `user_profiles` VALUES (2, '马里奥', 'http://example.com/avatar2.png', 1, 0);
INSERT INTO `user_profiles` VALUES (3, '路易吉', 'http://example.com/avatar3.png', 1, 1);
INSERT INTO `user_profiles` VALUES (4, '桃花公主', NULL, 0, 0);
INSERT INTO `user_profiles` VALUES (5, '酷霸王', 'http://example.com/avatar5.png', 0, 0);
INSERT INTO `user_profiles` VALUES (6, '我是新玩家', NULL, 1, 0);
INSERT INTO `user_profiles` VALUES (7, '我是旧玩家', NULL, 0, 0);
INSERT INTO `user_profiles` VALUES (8, '测试玩家一号', NULL, 1, 1);
INSERT INTO `user_profiles` VALUES (9, '测试玩家2号', NULL, 1, 0);

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
) ENGINE = InnoDB AUTO_INCREMENT = 10 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of users
-- ----------------------------
INSERT INTO `users` VALUES (1, 'player_one', 'player1@example.com', '$2a$10$EblZ.2j0v9z5b2fXjnv05uG22yB.f2F.X.X/2v.2.3.4.5', '2025-09-01 10:00:00');
INSERT INTO `users` VALUES (2, 'player_two', 'player2@example.com', '$2a$10$EblZ.2j0v9z5b2fXjnv05uG22yB.f2F.X.X/2v.2.3.4.5', '2025-09-01 11:00:00');
INSERT INTO `users` VALUES (3, 'player_three', 'player3@example.com', '$2a$10$EblZ.2j0v9z5b2fXjnv05uG22yB.f2F.X.X/2v.2.3.4.5', '2025-09-02 12:00:00');
INSERT INTO `users` VALUES (4, 'player_four', 'player4@example.com', '$2a$10$EblZ.2j0v9z5b2fXjnv05uG22yB.f2F.X.X/2v.2.3.4.5', '2025-09-02 13:00:00');
INSERT INTO `users` VALUES (5, 'player_five', 'player5@example.com', '$2a$10$EblZ.2j0v9z5b2fXjnv05uG22yB.f2F.X.X/2v.2.3.4.5', '2025-09-03 14:00:00');
INSERT INTO `users` VALUES (6, '123', 'tester@example.com', '$2a$10$qY3uDcKyREz7SgY8.Nk/ouggtKG8vxp1vgI1aOx.G5sLcsYUMMzVi', '2025-09-03 09:56:00');
INSERT INTO `users` VALUES (7, '456', 'tester2@example.com', '$2a$10$XrId2t6drDhEj3103w8DXeE1.Jgm/78XwKgk1s..iKJiYHtbcmIW2', '2025-09-03 09:56:13');
INSERT INTO `users` VALUES (8, 'qwe', 'test11@example.com', '$2a$10$yd4K4ClaRkArPUVkdOobJuHTJbrTrfNRFyu/0g.FZiVZJe5..h2rG', '2025-09-03 10:10:09');
INSERT INTO `users` VALUES (9, 'asd', 'test22@example.com', '$2a$10$NmozJM/G1fLxMO.wYyJ9gOHM3pQ72myElw5cakbn50ToAzmT19In2', '2025-09-03 10:10:35');

SET FOREIGN_KEY_CHECKS = 1;
