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

