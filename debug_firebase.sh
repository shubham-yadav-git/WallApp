#!/bin/bash

# WallApp Firebase Debugging Script
# This script helps diagnose Firebase data loading issues

set -e

echo "🔍 WallApp Firebase Diagnostic Tool"
echo "===================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check if adb is available
if ! command -v adb &> /dev/null; then
    echo -e "${RED}❌ Error: adb not found. Please install Android SDK Platform Tools.${NC}"
    exit 1
fi

# Check if device is connected
echo -e "${BLUE}📱 Checking for connected devices...${NC}"
DEVICE_COUNT=$(adb devices | grep -v "List" | grep "device" | wc -l | tr -d ' ')

if [ "$DEVICE_COUNT" -eq "0" ]; then
    echo -e "${RED}❌ No device connected. Please connect your Android device or start an emulator.${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Found $DEVICE_COUNT device(s)${NC}"
echo ""

# Configuration
PACKAGE_NAME="com.sky.wallapp"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

echo -e "${BLUE}🔧 Configuration:${NC}"
echo "   Package: $PACKAGE_NAME"
echo "   APK: $APK_PATH"
echo ""

# Check if APK exists
if [ ! -f "$APK_PATH" ]; then
    echo -e "${YELLOW}⚠️  APK not found. Building...${NC}"
    ./gradlew assembleDebug
    echo -e "${GREEN}✅ Build complete${NC}"
    echo ""
fi

# Function to check network connectivity
check_network() {
    echo -e "${BLUE}🌐 Checking device network connectivity...${NC}"
    if adb shell ping -c 2 8.8.8.8 &> /dev/null; then
        echo -e "${GREEN}✅ Device has internet connection${NC}"
    else
        echo -e "${YELLOW}⚠️  Device may not have internet connection${NC}"
    fi
    echo ""
}

# Function to uninstall app
uninstall_app() {
    echo -e "${BLUE}🗑️  Uninstalling old version...${NC}"
    if adb uninstall $PACKAGE_NAME &> /dev/null; then
        echo -e "${GREEN}✅ Uninstalled${NC}"
    else
        echo -e "${YELLOW}⚠️  App not installed or already removed${NC}"
    fi
    echo ""
}

# Function to install app
install_app() {
    echo -e "${BLUE}📦 Installing debug APK...${NC}"
    if adb install "$APK_PATH" &> /dev/null; then
        echo -e "${GREEN}✅ Installed successfully${NC}"
    else
        echo -e "${RED}❌ Installation failed${NC}"
        exit 1
    fi
    echo ""
}

# Function to start logcat monitoring
start_logcat() {
    echo -e "${BLUE}📊 Starting logcat monitor...${NC}"
    echo -e "${YELLOW}   Press Ctrl+C to stop monitoring${NC}"
    echo -e "${YELLOW}   Now launch the app on your device${NC}"
    echo ""
    echo "================================================"

    # Monitor for Firebase-related logs
    adb logcat -c  # Clear old logs
    adb logcat | grep --color=always -E "(WallAppApplication|MainActivity|Firebase|PERMISSION_DENIED|onDataChange|onCancelled)"
}

# Main execution
echo -e "${BLUE}🚀 Starting diagnostic process...${NC}"
echo ""

check_network
uninstall_app
install_app

echo -e "${GREEN}✅ Ready to test!${NC}"
echo ""
echo -e "${BLUE}📝 What to look for in logs:${NC}"
echo "   ✅ '🔥 Firebase Database initialized'"
echo "   ✅ '🔗 Firebase Database URL: https://wallapp-611ae.firebaseio.com/'"
echo "   ✅ '📊 Total categories loaded: X' (X should be > 0)"
echo "   ❌ 'PERMISSION_DENIED' - Check Firebase rules"
echo "   ❌ 'Children count: 0' - Database is empty or inaccessible"
echo ""

start_logcat

