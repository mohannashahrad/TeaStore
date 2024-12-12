# TeaStore

This is a forked version of the TeaStore benchmark. You can follow the main [TeaStore](https://github.com/DescartesResearch/TeaStore) repository for their detailed documentation on how to run, deploy, instrument, and test the code. 

This forked version enables call-graph tracking. Kieker tracing is not supported in this version. Instead, a call graph tracking backend (CGT) runs as a separate service that tracks the call graph while requests flow through the application.

To build, deploy, and run this version, please follow these steps:

1. **Run the [CGT](./CGT/) backend** following the instructions [here](./CGT/README.md).
2. **Build the modified code** by running `mvn clean install -DskipTests` from the root directory. If Maven is not already installed, first make sure that you have it installed.
3. **Run the [CGT build script](./tools/cgt_build.sh)**. This script will build all the Docker images with call graph tracking enabled. (Make sure you have Docker installed on your machine.)
4. **Finally, run the images!** A sample script for running the Docker images is provided [here](./examples/CGT/).

## Configuring Deployment Parameters

### Parameters to Set in Each Service Image
- **BATCH_SIZE**: This refers to the size of the batch (number of Trace Data) that is sent to the backend CGT servers at once. The default value is 1000. If you want to change this value, you should pass it as an environment variable to the service images. An example of this is given in the [config file](./examples/CGT/cgt_config.cfg) and the sample [docker-compose](./examples/CGT/docker-compose.yml) file provided.
- **FREQUENCY**: Refers to the frequency (in seconds) at which each batch is sent to the CGT server. By default, this is set to 2 seconds. If you wish to change the value, pass it as an environment variable to each image.
- **CONNECTION_TIMEOUT**: The maximum timeout (in milliseconds) for when the CallGraphTracking module from each service tries to connect to the CGT backend. By default, the value is 5000 milliseconds. You can configure this by changing the [config file](./examples/CGT/cgt_config.cfg), the docker-compose YAML file, or by passing it as an environment variable to the images.

### Parameters to Set on the CGT Server Side
You can find an example of how to set these parameters in the [sample config file](./CGT/cgt_config.json) provided.

- **pattern_matching_enabled**: By default, its value is set to `false`, meaning that endpoints are stored as they are in the call graph. However, if you want to map any pattern of endpoints to a target endpoint name stored in the call graph, you can set this to `true`. This will enable the CGT backend to normalize the endpoints during graph construction.

- **patterns**: This parameter is only used if you set `pattern_matching_enabled` to `true`. The default value for this is an empty list. You should provide a list of patterns, each in the following format:

  ```json
  {
    "pattern": "<the pattern to look for>",
    "normalized_path": "<the target to match>"
  }
  ```
  
  For examples on how to set this parameter, please refer to the [sample](./CGT/cgt_config.json) provided.

- **visualization_storage**: This parameter sets the path on the machine where the visualizations of the call graph will be saved. By default, this is stored in the `callgraphs` subdirectory in the directory where the server is running. To set a custom path, pass the target directory in the [server configuration](./CGT/cgt_config.json) file.