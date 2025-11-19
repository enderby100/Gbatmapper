# BatMapper - Ant Build Instructions

This project has been converted from Maven to Ant for building.

## Prerequisites

- Java Development Kit (JDK) 8 or later
- Apache Ant (installed and in your PATH)

## Build Commands

### Build the project (default)
```bash
ant jar
```
This will:
1. Download all dependencies from Maven Central (if not already present)
2. Compile the source code
3. Create a fat JAR with all dependencies included
4. Output the JAR to: `C:\Users\eprice\batclient\plugins\batUtils-1.6.3-full.jar`

### Clean build artifacts
```bash
ant clean
```
This removes the `target/` directory and the output JAR file.

### Rebuild (clean + build)
```bash
ant rebuild
```

### Compile only (without creating JAR)
```bash
ant compile
```

### Compile and run tests
```bash
ant test
```

## Dependencies

The build automatically downloads the following dependencies from Maven Central:
- JUNG (Java Universal Network/Graph Framework) 2.0.1
  - jung-graph-impl
  - jung-api
  - jung-algorithms
  - jung-visualization
- Collections-Generic 4.01
- Colt 1.2.0
- Concurrent 1.3.4
- Commons-IO 2.7
- JUnit 4.13.1 (for tests)

The `bat_interfaces-1.jar` is copied from the local `repo/` directory.

## Output

The final JAR is created at:
```
C:\Users\eprice\batclient\plugins\batUtils-1.6.3-full.jar
```

This is a "fat JAR" containing all dependencies, ready to be used as a plugin.

## Configuration

To change the output directory, edit the `output.dir` property in `build.xml`:
```xml
<property name="output.dir" location="C:/Users/eprice/batclient/plugins"/>
```

To change the version number, edit the `project.version` property in `build.xml`:
```xml
<property name="project.version" value="1.6.3"/>
```
