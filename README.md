# TeaStore #  

This is a forked version of the TeaStore benchmark. You can follow the main [TeaStore](https://github.com/DescartesResearch/TeaStore) repository for their detailed documentation on how to run, deploy, instrument, and test the code. This forked version enables call-graph tracking. Kieker tracing is not supported in this version, and instead, a call graph tracking backend (CGT) runs as a separate service that will keep track of the call graph while requests flow through the application. To build, deploy, and run this version, please follow these steps:

1. First, run the [CGT](./CGT/) backend following the instructions [here](./CGT/README.md).
2. Build the modified code by running `mvn clean install -DskipTests` from the root directory. If Maven is not already installed, first make sure that you have it installed.
3. Then, run [the CGT build script](./tools/cgt_build.sh). This script will build all the Docker images with call graph tracking enabled. (Again make sure you have docker installed on your machine)
4. Finally, run the images! A sample script for running the Docker images is provided [here](./examples/CGT/).


## Configuring deployment parameters

