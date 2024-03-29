# Sudachi Tools

This is a project which contains tools for developing [Sudachi](https://github.com/WorksApplications/Sudachi) and its dictionaries.

## Features

### Analysis

This project can perform analysis or a large text corpus with Sudachi java version.
Sudachi jar needs to be provided externally.

Data will be cut into 5mb chunks and analyzed with Sudachi in parallel.
The resulting files will be compressed with [ZStandard](https://facebook.github.io/zstd/) compression algorithm.
Different setups can be specified with different Sudachi configuration files.

### Diffing

Compute diffs between two folders of analyzed data. In addition to plain diffs, this tool also computes overview of diffed elements.
It is easy to see whether changed dictionary were actually working in the real text.

# Installation

You need JDK 17 installed to work with it.
It is possible that the project will run on earlier JDK releases, but this is neither tested nor supported.

Download the latest fat jar from releases page.

## From sources

After cloning the project, run `./gradlew shadowJar`.
The jar with dependencies will be in `build/libs` directory.

# Usage

## Analysis

You need to download Sudachi and its dictionary.
The example invocation for analyzing is the following.
The analysis results will be placed in `/path/to/output` directory.
```bash
java -jar sudachi-tools.jar analyze \
    --jar /path/to/sudachi.jar \
    --config /path/to/sudachi.json \
    --mode C \
    --output /path/to/output \
    --filter '*.txt' \
    /path/to/input/data
```

It is also possible specify version of Sudachi instead of file: `--jar v0.6.2`.
In this case, Sudachi will be automatically downloaded from Maven repository.
Snapshots builds are also supported: `--jar v0.7.1-SNAPSHOT`, in this case 
snapshot version will be checked each time, ensuring the execution with the latest snapshot.


## Computing Diffs

The invocation is the following. Directory `/path/to/diffs` will contain html report with diffs.
Inputs must be the analysis results from the analysis step.

```bash
java -jar sudachi-tools.jar diff \
    /path/to/analysis1 \
    /path/to/analysis2 \
    /path/to/diffs
```

## Debug

Debug mode which analyzes stdin with the given Sudachi version and config.
In this mode, lattice dump is outputted as well.

```bash
java -jar sudachi-tools.jar debug \
    --jar /path/to/sudachi.jar \
    --config /path/to/sudachi.json \
    --mode C
```