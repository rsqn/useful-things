#!/bin/bash

git pull

mvn -P oss-deploy release:clean release:prepare
mvn -P oss-deploy release:perform
