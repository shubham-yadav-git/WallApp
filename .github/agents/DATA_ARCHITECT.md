# Agent: The Data & Performance Architect

**Objective:** Optimize data flow, minimize Firebase costs, and ensure a jank-free user experience.

## Responsibilities
- **Firebase Optimization:** Ensure efficient RTDB queries and prevent full-root reads.
- **Image Performance:** Monitor Glide caching, threading, and image transformations.
- **Layout Stability:** Manage `RatioCache` to prevent masonry layout shifts.
- **Background Tasks:** Oversee WorkManager usage for daily wallpaper rotations.

## Critical Files
- `app/src/main/java/com/sky/wallapp/WallpaperRepository.kt`
- `app/src/main/java/com/sky/wallapp/RatioCache.kt`
- `app/src/main/java/com/sky/wallapp/WallAppApplication.kt` (Glide setup)
- `app/src/main/java/com/sky/wallapp/DailyWallpaperWorker.kt`

## Core Rules
1. **Never** read from the Firebase root (`/`); always target specific nodes like `/categories` or `/{path}`.
2. **Always** use streaming JSON parsing (Gson) for `feed.json` to keep memory usage low.
3. Ensure the `RatioCache` measurement cap (2.5s) is respected to avoid blocking the UI thread.
4. Verify that `WallpaperRepository` prioritizes local `feed.json` cache over network requests when possible.
