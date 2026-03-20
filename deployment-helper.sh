#!/bin/bash
# Quick Start Script for WallApp Automated Deployment
# This script helps you set up and test your deployment automation

set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  WallApp Deployment Setup Assistant   ║${NC}"
echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
echo ""

# Check if we're in a git repo
if ! git rev-parse --git-dir > /dev/null 2>&1; then
    echo -e "${RED}❌ Not a git repository!${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Git repository detected${NC}"

# Check remote
REMOTE=$(git remote get-url origin 2>/dev/null || echo "")
if [[ -n "$REMOTE" ]]; then
    echo -e "${GREEN}✓ Remote: $REMOTE${NC}"
else
    echo -e "${YELLOW}⚠ No git remote found${NC}"
fi

echo ""
echo -e "${BLUE}════════════════════════════════════════${NC}"
echo -e "${BLUE}Current Workflows Status:${NC}"
echo -e "${BLUE}════════════════════════════════════════${NC}"
echo ""

# Check if workflows exist
if [ -f ".github/workflows/build-check.yml" ]; then
    echo -e "${GREEN}✓ Build Check workflow${NC} - Runs on every push"
else
    echo -e "${RED}✗ Build Check workflow missing${NC}"
fi

if [ -f ".github/workflows/release-build.yml" ]; then
    echo -e "${GREEN}✓ Release Build workflow${NC} - Creates releases from tags"
else
    echo -e "${RED}✗ Release Build workflow missing${NC}"
fi

if [ -f ".github/workflows/deploy-playstore.yml" ]; then
    echo -e "${GREEN}✓ Play Store Deploy workflow${NC} - Auto-uploads to Play Store"
else
    echo -e "${RED}✗ Play Store Deploy workflow missing${NC}"
fi

echo ""
echo -e "${BLUE}════════════════════════════════════════${NC}"
echo -e "${BLUE}What would you like to do?${NC}"
echo -e "${BLUE}════════════════════════════════════════${NC}"
echo ""
echo "1) Test build locally (no commit)"
echo "2) Commit and push workflows (enable automation)"
echo "3) Create a release tag (triggers release build)"
echo "4) View deployment guide"
echo "5) Check GitHub secrets status"
echo "6) Exit"
echo ""
read -p "Enter your choice (1-6): " choice

case $choice in
    1)
        echo -e "\n${BLUE}Building debug APK locally...${NC}"
        ./gradlew assembleDebug --stacktrace
        echo -e "${GREEN}✓ Build complete!${NC}"
        echo -e "APK location: ${YELLOW}app/build/outputs/apk/debug/${NC}"
        ;;
    2)
        echo -e "\n${BLUE}Committing workflow files...${NC}"
        git add .github/workflows/
        git add app/build.gradle
        git add DEPLOYMENT_GUIDE.md
        git status
        echo ""
        read -p "Commit message (or press Enter for default): " commit_msg
        commit_msg=${commit_msg:-"Add GitHub Actions workflows for automated deployment"}
        git commit -m "$commit_msg"
        echo -e "${GREEN}✓ Committed${NC}"
        echo ""
        read -p "Push to remote? (y/n): " push_confirm
        if [[ $push_confirm == "y" || $push_confirm == "Y" ]]; then
            git push
            echo -e "${GREEN}✓ Pushed to remote${NC}"
            echo -e "\n${YELLOW}🎉 Automation is now active!${NC}"
            echo -e "Check: ${BLUE}https://github.com/$(git remote get-url origin | sed 's/.*github.com[:/]\(.*\)\.git/\1/')/actions${NC}"
        fi
        ;;
    3)
        echo -e "\n${BLUE}Creating a release tag...${NC}"
        echo ""
        CURRENT_VERSION=$(grep "versionName" app/build.gradle | sed 's/.*"\(.*\)"/\1/')
        echo -e "Current version in build.gradle: ${YELLOW}$CURRENT_VERSION${NC}"
        echo ""
        read -p "Enter new version tag (e.g., v1.2.1): " new_tag

        if [[ -z "$new_tag" ]]; then
            echo -e "${RED}❌ No tag provided${NC}"
            exit 1
        fi

        read -p "Tag message (optional): " tag_msg
        tag_msg=${tag_msg:-"Release $new_tag"}

        git tag -a "$new_tag" -m "$tag_msg"
        echo -e "${GREEN}✓ Tag created: $new_tag${NC}"
        echo ""
        read -p "Push tag to remote? (y/n): " push_tag
        if [[ $push_tag == "y" || $push_tag == "Y" ]]; then
            git push origin "$new_tag"
            echo -e "${GREEN}✓ Tag pushed${NC}"
            echo -e "\n${YELLOW}🚀 Release build started!${NC}"
            echo -e "Check: ${BLUE}https://github.com/$(git remote get-url origin | sed 's/.*github.com[:/]\(.*\)\.git/\1/')/actions${NC}"
        fi
        ;;
    4)
        echo -e "\n${BLUE}Opening deployment guide...${NC}"
        if [ -f "DEPLOYMENT_GUIDE.md" ]; then
            cat DEPLOYMENT_GUIDE.md | less
        else
            echo -e "${RED}❌ DEPLOYMENT_GUIDE.md not found${NC}"
        fi
        ;;
    5)
        echo -e "\n${BLUE}════════════════════════════════════════${NC}"
        echo -e "${BLUE}GitHub Secrets Checklist:${NC}"
        echo -e "${BLUE}════════════════════════════════════════${NC}"
        echo ""
        echo "Go to: GitHub repo → Settings → Secrets and variables → Actions"
        echo ""
        echo -e "${YELLOW}For Signed Releases:${NC}"
        echo "  □ KEYSTORE_BASE64"
        echo "  □ KEYSTORE_PASSWORD"
        echo "  □ KEY_ALIAS"
        echo "  □ KEY_PASSWORD"
        echo ""
        echo -e "${YELLOW}For Play Store Deployment:${NC}"
        echo "  □ PLAY_STORE_SERVICE_ACCOUNT_JSON"
        echo ""
        echo -e "See ${BLUE}DEPLOYMENT_GUIDE.md${NC} for detailed setup instructions"
        ;;
    6)
        echo -e "${GREEN}Goodbye!${NC}"
        exit 0
        ;;
    *)
        echo -e "${RED}Invalid choice${NC}"
        exit 1
        ;;
esac

echo ""
echo -e "${GREEN}Done!${NC}"

