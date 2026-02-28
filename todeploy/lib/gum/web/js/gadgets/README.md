# Gum Gadgets Directory

This directory contains gadget implementations for the Gum dashboard system.

## Architecture

Gadgets are loaded dynamically at runtime using a manifest-based system. This eliminates the need to modify the HTML template when adding new gadgets.

### Components

1. **gadgets.json** - Manifest file listing all available gadgets
2. **gum.js** - Loads gadgets dynamically during initialization (via `_loadAllScripts_()`)
3. **Base classes**:
   - `help2edit.js` - Editor helper utilities
   - `gadget.js` - Base gadget class (all gadgets extend this)
4. **Gadget implementations** - Specific gadget types (button, chart, gauge, etc.)

## Adding a New Gadget

To add a new gadget to the system:

### 1. Create the Gadget File

Create your gadget JavaScript file in this directory:

```javascript
// my-gadget.js
class MyGadget extends Gadget {
    constructor() {
        super();
        // Your initialization
    }

    // Override base methods
    render() {
        // Your rendering logic
    }

    // Add your methods
}
```

### 2. Update the Manifest

Add your gadget filename to `gadgets.json`:

```json
{
    "description": "Manifest of available Gum gadgets...",
    "base": [
        "help2edit.js",
        "gadget.js"
    ],
    "gadgets": [
        "button.js",
        "chart.js",
        "my-gadget.js"    ← Add your gadget here
    ]
}
```

### 3. That's It!

No changes needed to:
- ❌ HTML template files
- ❌ Main application code
- ❌ Configuration files

The gadget will be automatically loaded and available in the dashboard.

## Loading Order

1. **Base scripts** load first (dependencies)
2. **Gadget scripts** load second (implementations)
3. Scripts within each group load **sequentially** to maintain proper order

## Troubleshooting

### Gadget Not Loading

Check the browser console for errors:
- `[GadgetLoader] Loaded: /gum/js/gadgets/my-gadget.js` ✅ Success
- `[GadgetLoader] Failed to load: ...` ❌ File not found or syntax error

### Gadget Loads But Doesn't Work

Ensure:
1. Your gadget class extends `Gadget` base class
2. Required methods are implemented
3. No JavaScript syntax errors (check console)
4. The gadget is registered properly in `gadgets.json`

## Implementation Details

The dynamic loading system (in `gum.js:_loadAllScripts_()`):
- Fetches **gadgets.json** manifest using jQuery's `$.getJSON()`
- Uses **Promise chains** for sequential async loading
- Loads **base scripts first** (dependencies like `gadget.js`)
- Then loads **gadget implementations** (button, chart, scheduler, etc.)
- Finally loads **third-party libraries** (Chart.js, gauge, etc.)
- **Tracks loaded scripts** in `_loadedScripts_` Set to prevent duplicates
- Uses `_loadScriptOnce_()` helper that skips already-loaded scripts
- Provides **error handling** with console logging for failures
- Integrates seamlessly with Gum's initialization sequence

## Migration Notes

Previously, gadgets were hardcoded in `_template_.html`:

```html
<!-- OLD WAY (deprecated) -->
<script src="/gum/js/gadgets/button.js"></script>
<script src="/gum/js/gadgets/chart.js"></script>
```

Now they're defined in `gadgets.json` and loaded automatically:

```json
{
    "gadgets": ["button.js", "chart.js"]
}
```

This makes the system more maintainable and extensible.

### Important: Existing Dashboards

**Dashboard HTML files** generated from the old template still have static `<script>` tags for gadgets. These must be removed to prevent duplicate loading errors.

**Fix existing dashboards:**
```bash
# Remove static gadget script tags from generated dashboard files
sed -i '/gadgets\/.*\.js/d' path/to/dashboard.html
```

**Or:** Regenerate dashboards from the updated `_template_.html`.

The system now tracks loaded scripts to prevent duplicates, but it's cleaner to remove the old static tags entirely.
