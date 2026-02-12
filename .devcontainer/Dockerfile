# Use Ubuntu LTS as base
FROM ubuntu:22.04

# Set environment variables
ENV DEBIAN_FRONTEND=noninteractive \
    JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto

# Install prerequisites and system dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    build-essential \
    curl \
    wget \
    git \
    vim \
    jq \
    procps \
    zip \
    unzip \
    gnupg \
    gnupg2 \
    software-properties-common \
    ca-certificates \
    openssh-client \
    && rm -rf /var/lib/apt/lists/*

# Install Amazon Corretto 21 JDK
RUN wget -qO- https://apt.corretto.aws/corretto.key | gpg --dearmor --output /usr/share/keyrings/corretto-archive-keyring.gpg && \
    echo "deb [signed-by=/usr/share/keyrings/corretto-archive-keyring.gpg] https://apt.corretto.aws stable main" \
        | tee /etc/apt/sources.list.d/corretto.list && \
    apt-get update && \
    apt-get install -y java-21-amazon-corretto-jdk && \
    rm -rf /var/lib/apt/lists/*

# Install Maven
RUN apt-get update && \
    apt-get install -y maven && \
    rm -rf /var/lib/apt/lists/*

# Set user to root
USER root

# Set up workspace directory
WORKDIR /workspace

# Create .m2 directory for Maven
RUN mkdir -p /root/.m2

# Ensure gpg2 is available (Maven GPG plugin expects it)
RUN ln -sf $(which gpg) /usr/bin/gpg2 2>/dev/null || true

# Set PATH to include Java
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Default command
CMD ["bash"]