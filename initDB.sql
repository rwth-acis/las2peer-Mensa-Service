/*Initializes the table for mensas*/
CREATE TABLE IF NOT EXISTS `mensas`
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

/*Initializes the table for mensa dishes*/
CREATE TABLE IF NOT EXISTS `dishes` 
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



/*Initializes the table for dish reviews*/
IF NOT EXISTS  CREATE TABLE `reviews` 
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
        COLLATE utf8_general_ci ,
    FOREIGN KEY (`mensaId`) REFERENCES mensas(`id`),
    FOREIGN KEY (`dishId`) REFERENCES dishes(`id`)
) ENGINE = InnoDB AUTO_INCREMENT = 414 CHARACTER
SET = utf8
COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;
