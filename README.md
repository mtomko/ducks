# Ducks
FASTQ demultiplexer

## Building
You have two options for building `ducks`: an assembly jar, or a native image. You probably want to
check out the latest version before building anything.

```shell
$ git checkout v0.1.0
```

### Assembly Jar
To make an assembly jar, you need [sbt](http://www.scala-sbt.org). Run:

```shell 
$ sbt assembly 
```

This will produce a jar in `target/scala-2.13` called something like `ducks-0.1.0-assembly.jar`. You
can run the program then using:

```shell 
$ java -jar target/scala-2.13/ducks-0.0.1-assembly.jar
```

### Native Image
Building a binary requires a [GraalVM](https://www.graalvm.org/) installed as well as the
`native-image` command. If you have GraalVM already set as your default JVM, you can install
`native-image` with:

```shell
$ ${JAVA_HOME}/bin/gu install native-image
```

Then you can use targets in the make file to build a native image:

```shell
$ make
```

Building the native image takes some time and prints some rather ugly warnings, but it seems to make
a working program. Currently the system tests use the native image.

## Usage
    Usage: ducks --conditions <path> --dmux-fastq <path> --data-fastq <path> [--output-dir <path>]
    
    Demultiplexes FASTQ files based on conditions
    
    Options and flags:
        --help
            Display this help text.
        --conditions <path>, -c <path>
            The conditions file
        --dmux-fastq <path>, -1 <path>
            The FASTQ file containing demultiplexing reads
        --data-fastq <path>, -2 <path>
            The FASTQ file containing data reads
        --output-dir <path>, -o <path>
            The output directory
