#!/usr/bin/env bash
# Install only inside LAB_RUNTIME. No sudo and no changes to system Java.
set -euo pipefail
ROOT="${LAB_RUNTIME:-/tmp/codex-pagerank-runtime}"
mkdir -p "$ROOT/downloads"
ROOT="$(cd "$ROOT" && pwd)"
cd "$ROOT/downloads"
download() {
  local name="$1" url="$2" directory="$3" digest="$4"
  if [[ -d "$ROOT/$directory" ]]; then
    printf 'Already extracted: %s\n' "$directory"
    return
  fi
  curl --fail --location --retry 3 "$url" -o "$name"
  if [[ -n "$digest" ]]; then
    printf '%s  %s\n' "$digest" "$name" | sha512sum --check -
  fi
  tar -xzf "$name" -C "$ROOT"
}
download hadoop.tar.gz \
  https://mirrors.huaweicloud.com/apache/hadoop/common/hadoop-3.3.6/hadoop-3.3.6.tar.gz \
  hadoop-3.3.6 \
  de3eaca2e0517e4b569a88b63c89fae19cb8ac6c01ff990f1ff8f0cc0f3128c8e8a23db01577ca562a0e0bb1b4a3889f8c74384e609cd55e537aada3dcaa9f8a
download hbase.tar.gz \
  https://repo.huaweicloud.com/apache/hbase/2.5.11/hbase-2.5.11-bin.tar.gz \
  hbase-2.5.11 \
  bad94b693c2f3013f91fb50bf8678d48c749815a8ca335531e85c0b62bfc18d34df253a43b50aa66b7ed5c1ff379e8ea0213b4a6f21441f93dd83eb21dec473c
download spark.tar.gz \
  https://mirrors.huaweicloud.com/apache/spark/spark-3.5.7/spark-3.5.7-bin-hadoop3.tgz \
  spark-3.5.7-bin-hadoop3 \
  f3b7d5974d746b9aaecb19104473da91068b698a4d292177deb75deb83ef9dc7eb77062446940561ac9ab7ee3336fb421332b1c877292dab4ac1b6ca30f4f2e0
download java.tar.gz \
  https://cdn.azul.com/zulu/bin/zulu11.84.17-ca-jdk11.0.29-linux_x64.tar.gz \
  zulu11.84.17-ca-jdk11.0.29-linux_x64 \
  8fc08ee163c263ed7f597644d1546d0352e44a6a53aa21a9d4fd16f8159a6a4159f7cb7f3a47216bc45921064dd81e1b6f6ede69c25f91f2b4f6f317b034eece
download maven.tar.gz \
  https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.tar.gz \
  apache-maven-3.9.9 \
  a555254d6b53d267965a3404ecb14e53c3827c09c3b94b5678835887ab404556bfaf78dcfe03ba76fa2508649dca8531c74bca4d5846513522404d48e8c4ac8b
printf 'Runtime extracted at %s\n' "$ROOT"
