# opencensus-jvm-metrics
This repository provides a way to export JVM metrics to [OpenCensus](https://github.com/census-instrumentation/opencensus-java).

## Current status
This repository is in a very early status, it currently only supports metrics regarding memory usage.

## Usage
```
JvmMetrics.start();
JvmMetrics.registerAllViews();
```
