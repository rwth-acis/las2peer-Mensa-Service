-- ----------------------------
-- Table structure for mensas
-- ----------------------------
SET NAMES utf8mb4;

DROP TABLE IF EXISTS `mensas`;
CREATE TABLE `mensas`
(
  `id` int
(12) NOT NULL,
  `name` varchar
(255) CHARACTER
SET utf8
COLLATE utf8_general_ci NOT NULL,
  `city` varchar
(255) CHARACTER
SET utf8
COLLATE utf8_general_ci ,
  `address` varchar
(255) CHARACTER
SET utf8
COLLATE utf8_general_ci,
  PRIMARY KEY
(`id`) USING BTREE
)ENGINE = InnoDB AUTO_INCREMENT = 414 CHARACTER
SET = utf8
COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;
-- ----------------------------
-- Table structure for dishes
-- ----------------------------
DROP TABLE IF EXISTS `dishes`;
CREATE TABLE `dishes`
(
    `id` int (12) NOT NULL,
    `mensaId` int (12) NOT NULL REFERENCES mensas(`id`),
      `name` varchar
        (255) CHARACTER
        SET utf8
        COLLATE utf8_general_ci NOT NULL,
    `category` varchar
        (255) CHARACTER
        SET utf8
        COLLATE utf8_general_ci,
    PRIMARY KEY (`id`,`mensaId`) USING BTREE
 
)ENGINE = InnoDB AUTO_INCREMENT = 414 CHARACTER
SET = utf8
COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;
-- ----------------------------
-- Table structure for reviews
-- ----------------------------
DROP TABLE IF EXISTS `reviews`;
CREATE TABLE `reviews`
(   `id` int(12) NOT NULL PRIMARY KEY AUTO_INCREMENT,
    `author` varchar
        (255) CHARACTER
        SET utf8
        COLLATE utf8_general_ci NOT NULL,
    `mensaId` int (12) NOT NULL REFERENCES mensas(`id`),
    `dishId` int (12) NOT NULL REFERENCES dishes(`id`),

    `timestamp` timestamp,
    `stars` int (12) NOT NULL,
    `comment` varchar
        (255) CHARACTER
        SET utf8
        COLLATE utf8_general_ci 
  
) ENGINE = InnoDB AUTO_INCREMENT = 414 CHARACTER
SET = utf8
COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;