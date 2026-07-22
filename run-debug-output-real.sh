#!/bin/bash

# Create a temporary directory for the test
mkdir -p test-project-real

# Create build.gradle.kts
cat > test-project-real/build.gradle.kts << 'EOF'
plugins {
    id("education.cccp.plantuml")
}
EOF

# Create settings.gradle.kts
cat > test-project-real/settings.gradle.kts << 'EOF'
rootProject.name = "plantuml-rag-test"
EOF

# Copy the plantuml plugin to the local repository
mkdir -p test-project-real/plugins
cp -r plantuml-plugin test-project-real/plugins/plantuml-plugin

# Create an empty RAG directory
mkdir -p test-project-real/generated/rag

# Run the task and capture output
echo "=== Output for empty directory ==="
./gradlew -p test-project-real collectPlantumlIndex --stacktrace
echo "=================================="

# Create a RAG directory with one file
echo "@startuml
class Test
@enduml" > test-project-real/generated/rag/test.puml

# Run the task and capture output
echo "=== Output for directory with one file ==="
./gradlew -p test-project-real collectPlantumlIndex --stacktrace
echo "=========================================="

# Clean up
rm -rf test-project-real