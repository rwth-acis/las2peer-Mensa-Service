DROP TABLE IF EXISTS `reviews`;
CREATE TABLE `reviews`
(
    `authorId` varchar
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
        COLLATE utf8_general_ci ,
  
    PRIMARY KEY (`authorId`,`mensaId`,`dishId`) USING BTREE,
    FOREIGN KEY (`mensaId`) REFERENCES mensas(`id`),
    FOREIGN KEY (`dishId`) REFERENCES dishes(`id`)
) ENGINE = InnoDB AUTO_INCREMENT = 414 CHARACTER
SET = utf8
COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;