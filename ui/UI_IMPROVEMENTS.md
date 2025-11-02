# UI Design Improvements

## Issues Identified

### 1. Theme Inconsistency
- Body background: Dark (#020617)
- Shell background: Light (#f8fafc) - CONFLICT
- Auth pages: White cards (#ffffff)
- Todo pages: Dark transparent backgrounds
- Result: Chaotic mixing of light and dark themes

### 2. Contrast Problems
- Todo pages use rgba with 0.12-0.25 opacity
- Light text on barely-visible backgrounds
- Disabled button text: rgba(148, 163, 184, 0.6) - nearly invisible
- Error/success messages have poor contrast

### 3. Specific Visibility Issues
- White background + light gray text = hard to read
- Transparent backgrounds don't provide enough separation
- Filter buttons and action buttons blend into background
- Meta information (status, dates) too dim

## Improved Design System

### Color Palette (Dark Theme - Consistent)
```
Background Hierarchy:
- Body: #0f172a (slate-900) - dark blue-gray
- Shell: #0f172a (same as body)
- Cards/Containers: #1e293b (slate-800) - lighter blue-gray
- Elevated elements: #334155 (slate-700)
- Inputs: #1e293b with #334155 borders

Text Colors:
- Primary: #f1f5f9 (slate-100) - high contrast
- Secondary: #cbd5e1 (slate-300) - medium contrast
- Tertiary: #94a3b8 (slate-400) - low contrast
- Disabled: #64748b (slate-500)

Accent Colors (High Contrast):
- Primary Blue: #3b82f6 (blue-500)
- Primary Hover: #2563eb (blue-600)
- Success: #10b981 (emerald-500)
- Success Background: #064e3b (emerald-900)
- Error: #ef4444 (red-500)
- Error Background: #7f1d1d (red-900)
- Warning: #f59e0b (amber-500)

Interactive States:
- Hover: Lighten by 10% or add glow
- Active: #60a5fa (blue-400)
- Focus: 3px ring #3b82f6 with 40% opacity
- Disabled: 40% opacity
```

### Typography
- Font: Inter (already set)
- Headings: font-weight: 700
- Body: font-weight: 400
- Buttons: font-weight: 600

### Spacing & Borders
- Border radius: 0.75rem (12px) for cards, 0.5rem (8px) for inputs
- Consistent padding: 1.5rem for cards, 1rem for inputs
- Gap: 1rem standard, 1.5rem for sections

## Implementation Plan

1. Global Styles: Update to consistent dark theme
2. Shell: Match body background, improve header contrast
3. Todo List: Solid backgrounds, better contrast for all elements
4. Todo Editor: Solid backgrounds, visible form elements
5. Auth Pages: Adapt to dark theme with proper contrast
