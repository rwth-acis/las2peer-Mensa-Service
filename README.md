![las2peer](https://rwth-acis.github.io/las2peer/logo/vector/las2peer-logo.svg)

# las2peer-Mensa-Service

[![Build Status](https://travis-ci.org/rwth-acis/las2peer-Mensa-Service.svg?branch=master)](https://travis-ci.org/rwth-acis/las2peer-Mensa-Service) [![codecov](https://codecov.io/gh/rwth-acis/las2peer-Mensa-Service/branch/master/graph/badge.svg)](https://codecov.io/gh/rwth-acis/las2peer-Mensa-Service)

A simple RESTful service for retrieving the current menu of a canteen of the RWTH Aachen University. The service is based on [las2peer](https://github.com/rwth-acis/LAS2peer). 

Build
--------
Execute the following command on your shell:

```shell
ant all 
```

Start
--------

To start the Mensa Service, use one of the available start scripts:

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

A list of available REST calls can be found in the *swagger.json* which is available at:

[http://localhost:8080/mensa/swagger.json](http://localhost:8080/mensa/swagger.json)

MobSOS Monitoring Messages
-------------------

This service logs some custom messages to indicate certain events.
In order to build a success model with MobSOS it is helpful to know the meaning of these messages.

| ID | Description | Remarks |
|----|-------------|---------|
|  1 | Menu queried for mensa. | Name of mensa. |
|  2 | Menu queried for language. | Language in lang-country format. |
|  3 | Ratings queried for dish. | Name of dish. |
|  4 | Rating added for dish. | Name of dish. |
|  5 | Rating deleted for dish. | Name of dish. |
|  6 | Pictures queried for dish. | Name of dish. |
|  7 | Picture added for dish. | Name of dish. |
|  8 | Picture deleted for dish. | Name of dish. |
| 10 | Menu successfully retrieved. | Menu as JSON. |
| 20 | Menu queried for unsupported mensa. | Name of unsupported mensa. |
| 40 | Time in ms to get return the menu. | Time is ms. |
| 41 | Time in ms to get return the rating for a dish. | Time is ms. |
| 42 | Time in ms to get return the pictures for a dish. | Time is ms. |

*ID means the number after the SERVICE_CUSTOM_MESSAGE_ prefix.*