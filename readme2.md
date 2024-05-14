# How to build and run

You'll need to install Java Development Kit (JDK) 8, also
known as 1.8.

## Build the JAR file

````bash
$ ./gradlew jar
````

## Run NetHub

Here we run it in the background.

````bash
$ java -jar build/libs/dodo-1.0.jar &
````

## Run DodoServer

````bash
$ java -cp build/libs/dodo-1.0.jar dev.hawala.xns.DodoServer
````

## Alternatively, you can run the `dodoserver-and-nethub.sh` script

This produces the file `dodo.properties` from `dodo.properties.template`, redirects output
to a set of log files, and records the process IDs for ease of termination later.

````bash
$ src/main/scripts/dodoserver-and-nethub.sh
````
