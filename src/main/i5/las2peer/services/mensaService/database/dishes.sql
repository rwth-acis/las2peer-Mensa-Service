/*Initializes the table for mensa dishes*/
DROP TABLE IF EXISTS `dishes`;
CREATE TABLE `dishes`
(
    `id` int (12) NOT NULL,
    `mensaId` int (12) NOT NULL,
      `name` varchar
        (255) CHARACTER
        SET utf8
        COLLATE utf8_general_ci NOT NULL,
    `category` varchar
        (255) CHARACTER
        SET utf8
        COLLATE utf8_general_ci,
    PRIMARY KEY (`id`,`mensaId`) USING BTREE,
    FOREIGN KEY (`mensaId`) REFERENCES mensas(`id`)
) ENGINE = InnoDB AUTO_INCREMENT = 414 CHARACTER
SET = utf8
COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;