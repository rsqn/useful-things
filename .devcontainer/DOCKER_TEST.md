# Docker Build and Test Instructions

This document describes how to test the Dockerfile and post-create script using standard Docker commands (without devcontainer.json).

## Building the Image

```bash
docker build -f .devcontainer/Dockerfile -t useful-things-dev:test .
```

This will:
- Build an Amazon Corretto 21-based development image
- Install all system dependencies (git, curl, wget, etc.)
- Install Maven for build management
- Set up the vscode user with proper permissions
- Configure Java environment variables

## Running the Post-Create Script

After building, test the post-create script:

```bash
docker run --rm useful-things-dev:test bash -c "bash .devcontainer/post-create.sh"
```

This will:
- Verify Java installation and version
- Verify Maven installation and version
- Check JAVA_HOME environment variable
- Verify project structure (pom.xml, src directories)
- Build Maven project (if pom.xml exists)
- Create helper scripts
- Run environment verification

## Running an Interactive Container

To get an interactive shell in the container:

```bash
docker run --rm -it useful-things-dev:test bash
```

Then you can manually run the post-create script:
```bash
bash .devcontainer/post-create.sh
```

Or verify the environment:
```bash
java -version
mvn -version
echo $JAVA_HOME
```

## Expected Output

The post-create script should:
- ✅ Verify Java installation (Amazon Corretto 21)
- ✅ Verify Maven installation
- ✅ Check JAVA_HOME is set correctly
- ✅ Create necessary directories (src/main/java, src/test/java, etc.)
- ✅ Build Maven project (if pom.xml exists)
 (dev_helpers.sh, test_environment.sh)
- ✅ Complete successfully

## Troubleshooting

If the build fails:
1. Verify Docker has enough resources (memory/disk)
2. Check that the Amazon Corretto image is available
3. Check Docker logs: `docker logs <container-id>`
4. Ensure internet connection for package downloads

If the post-create script fails:
1. The script uses `set +e` to handle errors gracefully
2. Check the output for specific error messages
3. Missing `pom.xml` is expected if you haven't created it yet
4. Maven build failures may be expected if dependencies need configuration

## Notes

- The Dockerfile installs system packages as root, then switches to vscode user
- The post-create script runs as the vscode user
- File permissions are set correctly using `--chown=vscode:vscode`
- Maven dependencies will be downloaded to `~/.m2` directory
- The `.dockerignore` file (if present) excludes unnecessary files from the build

## Java/Maven Verification

To verify Java and Maven are working correctly:

```bash
# Check Java version
java -version

# Check Maven version
mvn -version

# Check JAVA_HOME
echo $JAVA_HOME

# Should show: /usr/lib/jvm/java-21-amazon-corretto
```
