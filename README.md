<p align="center">
  <img src="https://raw.githubusercontent.com/rwth-acis/las2peer/master/img/logo/bitmap/las2peer-logo-128x128.png" />
</p>
<h1 align="center">las2peer-Mensa-Service</h1>
<p align="center">
  <a href="https://travis-ci.org/rwth-acis/las2peer-Mensa-Service" alt="Travis Build Status">
        <img src="https://travis-ci.org/rwth-acis/las2peer-Mensa-Service.svg?branch=master" /></a>
  <a href="https://codecov.io/gh/rwth-acis/las2peer-Mensa-Service" alt="Code Coverage">
        <img src="https://codecov.io/gh/rwth-acis/las2peer-Mensa-Service/branch/master/graph/badge.svg" /></a>
  <a href="https://libraries.io/github/rwth-acis/las2peer-Mensa-Service" alt="Dependencies">
        <img src="https://img.shields.io/librariesio/github/rwth-acis/las2peer-Mensa-Service" /></a>
</p>

A simple RESTful service for retrieving the current menu of a canteen of the RWTH Aachen University. The service is based on [las2peer](https://github.com/rwth-acis/LAS2peer).

Run the following command to start the service:

```sh
 docker compose -f "docker-compose.yml" up -d --build
```

Now visit `localhost:8080`. You should see the las2peer node frontend. Navigate to `localhost:8080/mensa/187` to see the menu for the mensa Academica in Aachen for the current day (might be closed).

## Configuration
For development purposes, you might consider running the service without using Docker. You can also build and run the service using Gradle and Java.

### MySQL

The mensa service requires a MySQL database to store dishes and ratings. Make sure that you have a running MySQL server with the required database tables. The `mysql-init-files\initDB.sql` script can be used to initialize the database.

Now configure the `\etc\i5.las2peer.services.mensaService.MensaService.properties` file with your database setup.

| variable         | default     |
| ---------------- | ----------- |
| databaseUser     | root        |
| databasePassword | password    |
| databaseName     | LAS2PEERMON |
| databaseHost     | 127.0.0.1   |
| databasePort     | 3306        |

with the credentials to your database

### Gradle

Make sure you have Gradle 7.3 and Java 17 installed.

Execute the following command on your shell:

```sh
gradle build
```

## Start
The build step generated shell scripts in `bin`. To start the Mensa Service, use one of the available start scripts:

Windows:

```shell
bin/start_network.bat
```

Unix/Mac:

```shell
bin/start_network.sh
```

After successful start, Mensa Service is available under

[http://localhost:8080/mensa/](http://localhost:8080/mensa/)

A list of available REST calls can be found in the _swagger.json_ which is available at:

[http://localhost:8080/mensa/swagger.json](http://localhost:8080/mensa/swagger.json)

## MobSOS Monitoring Messages

This service logs some custom messages to indicate certain events.
In order to build a success model with MobSOS it is helpful to know the meaning of these messages. Id's for mensas and dishes correspond to the IDs provided by the [Open Mensa API](https://doc.openmensa.org/api/v2/)

| ID  | Description                                                | Remarks                                                                                               |
| --- | ---------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| 1   | Menu queried for mensa.                                    | Id of mensa.                                                                                          |
| 2   | Menu queried for language.                                 | Language in lang-country format. **Deprecated**                                                       |
| 3   | Ratings queried for dish.                                  | id of dish.                                                                                           |
| 4   | Rating added for dish.                                     | id of dish.                                                                                           |
| 5   | Rating deleted for dish.                                   | id of dish.                                                                                           |
| 6   | Pictures queried for dish.                                 | Name of dish.                                                                                         |
| 7   | Picture added for dish.                                    | Name of dish.                                                                                         |
| 8   | Picture deleted for dish.                                  | Name of dish.                                                                                         |
| 10  | Menu successfully retrieved.                               | Id of mensa, for which menu was fetched.                                                              |
| 20  | Menu queried for unsupported mensa.                        | Id of unsupported mensa.                                                                              |
| 40  | Time in ms to process request.                             | Format: json: `duration`: Time is ms, `path`: relative path as string                                 |
| 41  | Time spent in chat performing a task, like adding a review | Format: json: `time`: Time is ms,`task`: kind of task as string, `email`: email of the user as string |
| 42  | Time in ms to get return the pictures for a dish.          | Time is ms. **Deprecated**                                                                            |
| 43  | Time in ms to update Dish index.                           | Format: Timestamp in ms. **Deprecated**                                                               |

_ID means the number after the *SERVICE_CUSTOM_MESSAGE* prefix._

## MobSOS ERROR Messages

This service logs some error messages to indicate certain errors. In order to build a success model with MobSOS it is helpful to know the meaning of these messages.

| ID  | Description               | Remarks           |
| --- | ------------------------- | ----------------- |
| 1   | Exception occured         | exception message |
| 2   | Menu could not be fetched | Id of the mensa   |

_ID means the number after the *SERVICE_CUSTOM_ERROR*_ prefix.\_
