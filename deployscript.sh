#!/bin/bash

git pull

mvn -B -Poss-deploy release:clean release:prepare
mvn -B -Poss-deploy release:perform
