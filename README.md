SDN Platform
============

This repository contains an advanced SDN controller and platform for
network virtualization.

# Building #
Install prerequisites.  You'll just need a basic functioning build
environment plus a few build-time dependencies.  On Ubuntu, you can
run:

    sudo apt-get install unzip python-dev python-virtualenv \
    	 git openjdk-7-jdk ant build-essential

Note that on Ubuntu 12.04, you may want to remove java6:

    sudo apt-get remove openjdk-6-jre-lib openjdk-6-jre-headless

To build the controller:

1. Clone from repository
3. `./setup.sh`
4. `make`

# Running #

## Sdncon ##
To run the controller and the cli, you need to be running a working
instance of cassandra and sdncon.  The setup script will have created
a python virtualenv for you to make it easy to run the python
components.  You must first activate the virtualenv in your current
shell by running

    source ./workspace/ve/bin/activate

Now you can easily run any of the python commands.

The make targets `start-cassandra` and `start-sdncon` will
automatically start a local copy of cassandra and sdncon.  There are
corresponding stop commands as well.  These commands require an
activated virtualenv.  If you run

    make stop-sdncon reset-cassandra start-sdncon

This will stop any existing sdncon and cassandra, reset their
databases to zero, and start a new sdncon with a fresh database.  The
output from these commands will go to a log file in your
`workspace/ve/logs` directory.

## Controller ##

Now you're ready to run a copy of sdnplatform.  The easiest way is to
run

    make start-sdnplatform

or you can do this manually with output to standard out by running
from the `sdnplatform` directory

    java -jar target/sdnplatform.jar

You can specify your own configuration file with `-cf [path]` or use
the default.

## CLI ##

The CLI depends on a running instance of sdncon.  To run the CLI, just
run it from the command line from the CLI directory:

    ./cli.py

The CLI has online help and tab completion on its commands.

# Eclipse environment #

To set up an eclipse environment:

1. `make eclipse` to generate eclipse project files
2. Import "sdnplatform" project into any eclipse workspace
