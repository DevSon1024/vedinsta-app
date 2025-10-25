#!/bin/bash
echo "Building VedInsta Release APKs..."

# Clean previous builds
./gradlew clean

# Build all ABI variants
./gradlew assembleRelease

echo "Release APKs built successfully!"
echo "Location: app/build/outputs/apk/release/"
echo ""
echo "Generated files:"
ls -la app/build/outputs/apk/release/*.apk

echo ""
echo "Recommended for distribution:"
echo "- vedinsta-1.0-arm64-v8a-release.apk (for most users)"
echo "- vedinsta-1.0-armeabi-v7a-release.apk (for older devices)"
echo "- vedinsta-1.0-universal-release.apk (for testing)"
