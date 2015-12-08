FROM quay.io/democracyworks/didor:latest

RUN mkdir -p /usr/src/election-authority-http-api
WORKDIR /usr/src/election-authority-http-api

COPY project.clj /usr/src/election-authority-http-api/

RUN lein deps

COPY . /usr/src/election-authority-http-api

RUN lein test
RUN lein immutant war --name election-authority-http-api --destination target --nrepl-port=11843 --nrepl-start --nrepl-host=0.0.0.0
