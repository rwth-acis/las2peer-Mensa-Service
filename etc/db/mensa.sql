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
) ENGINE = InnoDB AUTO_INCREMENT = 414 CHARACTER
SET = utf8
COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;
