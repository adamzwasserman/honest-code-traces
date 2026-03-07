FROM eclipse-temurin:21-jdk

ARG TARGETARCH

# Install Go
RUN apt-get update && apt-get install -y wget && \
    if [ "$TARGETARCH" = "arm64" ]; then GOARCH=arm64; else GOARCH=amd64; fi && \
    wget -q "https://go.dev/dl/go1.23.5.linux-${GOARCH}.tar.gz" -O /tmp/go.tar.gz && \
    tar -C /usr/local -xzf /tmp/go.tar.gz && \
    rm /tmp/go.tar.gz
ENV PATH="/usr/local/go/bin:${PATH}"

# Install Node.js + tsx
RUN apt-get install -y curl && \
    curl -fsSL https://deb.nodesource.com/setup_22.x | bash - && \
    apt-get install -y nodejs && \
    npm install -g tsx

# Install Python
RUN apt-get install -y python3 && \
    ln -sf /usr/bin/python3 /usr/bin/python

RUN apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /traces
COPY . .

RUN cd harness/java && javac Harness.java

RUN mkdir -p results
VOLUME ["/traces/results"]

CMD ["bash", "run-all.sh"]
