#!/bin/bash

# useful-things Development Environment - Post-Create Setup Script
# This script runs after the devcontainer is created to set up the development environment

# Don't exit on error - we'll handle errors gracefully
set +e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}✅${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠️${NC} $1"
}

print_error() {
    echo -e "${RED}❌${NC} $1"
}

print_info() {
    echo -e "${BLUE}ℹ️${NC} $1"
}

echo "🚀 Setting up useful-things development environment..."

# Verify Java installation
print_info "Verifying Java installation..."
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1)
    print_status "Java found: $(echo "$JAVA_VERSION" | head -n 1)"
    if echo "$JAVA_VERSION" | grep -qi "corretto\|amazon"; then
        print_status "Amazon Corretto detected"
    fi
else
    print_error "Java not found!"
fi

# Verify Maven installation
print_info "Verifying Maven installation..."
if command -v mvn &> /dev/null; then
    MAVEN_VERSION=$(mvn -version 2>&1 | head -n 1)
    print_status "Maven found: $MAVEN_VERSION"
else
    print_error "Maven not found!"
fi

print_status "Development environment setup complete!"
print_status "🎉 useful-things Development Environment is ready!"