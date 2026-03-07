FROM eclipse-temurin:21-jdk

ARG TARGETARCH

# Go
RUN apt-get update && apt-get install -y wget && \
    if [ "$TARGETARCH" = "arm64" ]; then GOARCH=arm64; else GOARCH=amd64; fi && \
    wget -q "https://go.dev/dl/go1.23.5.linux-${GOARCH}.tar.gz" -O /tmp/go.tar.gz && \
    tar -C /usr/local -xzf /tmp/go.tar.gz && rm /tmp/go.tar.gz
ENV PATH="/usr/local/go/bin:${PATH}"

# Node.js + tsx
RUN apt-get install -y curl && \
    curl -fsSL https://deb.nodesource.com/setup_22.x | bash - && \
    apt-get install -y nodejs && npm install -g tsx

# Python
RUN apt-get install -y python3 && ln -sf /usr/bin/python3 /usr/bin/python

# Kotlin (compiler)
RUN apt-get install -y unzip && \
    wget -q "https://github.com/JetBrains/kotlin/releases/download/v2.1.0/kotlin-compiler-2.1.0.zip" -O /tmp/kotlin.zip && \
    unzip -q /tmp/kotlin.zip -d /opt && rm /tmp/kotlin.zip
ENV PATH="/opt/kotlinc/bin:${PATH}"

# .NET SDK
RUN wget -q https://dot.net/v1/dotnet-install.sh -O /tmp/dotnet-install.sh && \
    chmod +x /tmp/dotnet-install.sh && \
    /tmp/dotnet-install.sh --channel 8.0 --install-dir /usr/share/dotnet && \
    rm /tmp/dotnet-install.sh
ENV PATH="/usr/share/dotnet:${PATH}"
ENV DOTNET_CLI_TELEMETRY_OPTOUT=1

# Swift (needs libncurses, etc.)
RUN apt-get install -y libncurses6 libcurl4-openssl-dev libxml2-dev && \
    if [ "$TARGETARCH" = "arm64" ]; then SWARCH=aarch64; else SWARCH=x86_64; fi && \
    wget -q "https://download.swift.org/swift-6.0.3-release/ubuntu2404-${SWARCH}/swift-6.0.3-RELEASE/swift-6.0.3-RELEASE-ubuntu24.04-${SWARCH}.tar.gz" -O /tmp/swift.tar.gz && \
    tar -xzf /tmp/swift.tar.gz -C /opt && rm /tmp/swift.tar.gz && \
    ln -s /opt/swift-6.0.3-RELEASE-ubuntu24.04-${SWARCH}/usr/bin/swift /usr/local/bin/swift && \
    ln -s /opt/swift-6.0.3-RELEASE-ubuntu24.04-${SWARCH}/usr/bin/swiftc /usr/local/bin/swiftc \
    || echo "Swift install failed, skipping"

# PHP
RUN apt-get install -y php-cli

# Ruby
RUN apt-get install -y ruby

# Dart
RUN if [ "$TARGETARCH" = "arm64" ]; then DARTARCH=arm64; else DARTARCH=x64; fi && \
    wget -q "https://storage.googleapis.com/dart-archive/channels/stable/release/3.5.4/sdk/dartsdk-linux-${DARTARCH}-release.zip" -O /tmp/dart.zip && \
    unzip -q /tmp/dart.zip -d /opt && rm /tmp/dart.zip
ENV PATH="/opt/dart-sdk/bin:${PATH}"

# C++ (g++ already in base image)
RUN apt-get install -y g++

RUN apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /traces
COPY . .

# Pre-compile Java and Kotlin
RUN cd harness/java && javac Harness.java
RUN cd harness/kotlin && kotlinc harness.kt -include-runtime -d harness.jar 2>/dev/null

# Pre-compile C++
RUN cd harness/cpp && g++ -O2 -std=c++17 harness.cpp -o harness

# Pre-compile C#
RUN cd harness/csharp && dotnet build -c Release --nologo -v q

# Pre-compile Swift (may not be available)
RUN cd harness/swift && (swiftc -O harness.swift -o harness 2>/dev/null || echo "Swift compile skipped")

RUN mkdir -p results
VOLUME ["/traces/results"]

CMD ["bash", "run-all.sh"]
