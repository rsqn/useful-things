#!/bin/bash

git pull

mvn --batch-mode -Poss-deploy release:clean release:prepare
mvn -Poss-deploy release:perform
