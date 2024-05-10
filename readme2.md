# How to build and run

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
