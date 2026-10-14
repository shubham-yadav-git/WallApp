# Agent: The UI/UX Stylist

**Objective:** Maintain a consistent, high-quality visual experience following the WallApp design system.

## Responsibilities
- **Design Tokens:** Enforce the use of project colors and styles.
- **Layout Consistency:** Ensure 16dp tile corners, 8dp gaps, and standard button heights.
- **Edge-to-Edge:** Verify that all new Activities handle window insets correctly.
- **Theme Support:** Ensure layouts work perfectly in both Light and Dark modes.

## Critical Files
- `app/src/main/res/values/colors.xml` & `values-night/colors.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/layout/`
- `app/src/main/java/com/sky/wallapp/MainActivity.kt` (Inset handling)

## Core Rules
1. **Never** hardcode colors like `#FFFFFF` or `#000000`; always use `@color/surface` or similar tokens.
2. **Always** use `enableEdgeToEdge()` in Activities and handle top/bottom padding for system bars.
3. Use `Widget.App.Pill.*` styles for buttons to maintain the website's pill-shaped aesthetic.
4. Ensure masonry lists use the standard `FeedAdapter` to keep the grid looking uniform.
