FROM openjdk:8-jdk-alpine

ENV LAS2PEER_PORT=9014
ENV DATABASE_NAME=LAS2PEERMON
ENV DATABASE_HOST=mobsos-mysql.mobsos
ENV DATABASE_PORT=3306
ENV DATABASE_USER=root
ENV DATABASE_PASSWORD=root

RUN apk add --update bash mysql-client apache-ant curl && rm -f /var/cache/apk/*
RUN addgroup -g 1000 -S las2peer && \
    adduser -u 1000 -S las2peer -G las2peer

COPY --chown=las2peer:las2peer . /src
WORKDIR /src

RUN chmod -R a+rwx /src
RUN chmod +x /src/docker-entrypoint.sh
RUN dos2unix docker-entrypoint.sh
# run the rest as unprivileged user
USER las2peer
RUN ant jar startscripts

EXPOSE $LAS2PEER_PORT
ENTRYPOINT ["/src/docker-entrypoint.sh"]
