#!/bin/bash

# Startup script for FHIR Toolkit
# There are two CATALINA_BASE directories: Toolkits/XdsToolkit and Toolkits/FhirToolkit.
# Toolkits/XdsToolkit may contain the HAPI FHIR server and/or XDS Toolkit. If either are present
# (if Toolkits/XdsToolkit/webapps/ is not empty) then this CATALINA_BASE is started.
# Next Toolkits/FhirToolkit is started.  This contains the FHIR Toolkit components.

# NOTE: HAPI FHIR and XDS Toolkit must be started before FhirToolkit.

unameOut="$(uname -s)"
case "${unameOut}" in
    Linux*)     machine=Linux;;
    Darwin*)    machine=Mac;;
    CYGWIN*)    machine=Cygwin;;
    MINGW*)     machine=MinGw;;
    *)          machine="UNKNOWN:${unameOut}"
esac

if [[ ${machine} = 'Mac' ]]
then
	echo "Running on a MAC"
fi

BASEDIR=$(dirname "$0")
if [[ ${BASEDIR} = '.' ]]
then
	BASEDIR=`pwd`
fi
echo "BASEDIR is $BASEDIR"


TOOLKITS=${BASEDIR}/../Toolkits
FHIRTOOLKIT=${TOOLKITS}/FhirToolkit
XDSTOOLKIT=${TOOLKITS}/XdsToolkit
XDSWEBAPPS=${XDSTOOLKIT}/webapps

export CATALINA_HOME=${BASEDIR}/..
echo "CATALINA_HOME is $CATALINA_HOME"

echo "starting HAPI FHIR base"
HAPIFHIRBASE=${BASEDIR}/../HapiFhir/base
mkdir ${HAPIFHIRBASE}/temp
mkdir ${HAPIFHIRBASE}/logs
export CATALINA_BASE=${HAPIFHIRBASE}
echo "CATALINA_BASE=$CATALINA_BASE"
./startup.sh
sleep 5


echo "Looking at $XDSWEBAPPS"
# find works different on a MAC vs LINUX
if [[ ${machine} == 'Mac' ]]
then
	WEBAPPSCOUNT=`find ${XDSWEBAPPS} -maxdepth 1 \( -type d -or -name *.war \) -print | wc -c`
else
	WEBAPPSCOUNT=`find ${XDSWEBAPPS} -maxdepth 1 \( -type d -o -name *.war \) -printf x | wc -c`
fi

# start XdsToolkit base if its webapps dir is not empty
echo "count is $WEBAPPSCOUNT"
if [[ ${machine} == 'Mac' ]]
then
	MINCOUNT=2
else
	MINCOUNT=1
fi

# account for find counting base directory
if [[ ${WEBAPPSCOUNT} -gt ${MINCOUNT} ]]
then
	echo "Starting XdsToolkit"
	mkdir ${XDSTOOLKIT}/logs
	export CATALINA_BASE=${XDSTOOLKIT}
	echo "CATALINA_BASE=$CATALINA_BASE"
	./startup.sh
	sleep 30
else
	echo "XdsToolkit should not be started"
fi

# start FhirToolkit

echo "Starting FhirToolkit"
if [[ $1 != 'jre1.8' ]]
then
  export CATALINA_OPTS="${CATALINA_OPTS} --add-opens java.base/java.util=ALL-UNNAMED"
fi

mkdir ${FHIRTOOLKIT}/logs
export CATALINA_BASE=${FHIRTOOLKIT}
echo "CATALINA_BASE=$CATALINA_BASE"
./startup.sh
