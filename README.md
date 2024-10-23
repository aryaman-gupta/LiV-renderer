
# LiV-renderer

## Description
LiV-renderer is a distributed rendering volume engine based on the [scenery](https://github.com/scenerygraphics/scenery) rendering framework. It offers a straightforward
interface for adding volumes to the scenegraph of `scenery`. Currently, MPI functions necessary for parallel image compositing must be defined in the external library that
uses LiV-renderer (see [LiV](https://github.com/aryaman-gupta/LiV) for example), but this is planned for integration into the library in the future.

## Installation
1. Clone the repository:
    ```sh
    git clone https://github.com/aryaman-gupta/LiV-renderer.git
    ```
2. Navigate to the project directory:
    ```sh
    cd LiV-renderer
    ```
3. Build the project using Gradle:
    ```sh
    ./gradlew build
    ```