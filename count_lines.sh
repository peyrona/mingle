#!/bin/bash
set -euo pipefail

# 1. Configuration
DIRS_TO_SEARCH=( "todeploy" "todeploy/examples" "todeploy/include" "todeploy/lib/gum/web" \
                 "candi" "cil" "controllers" "glue" "gum" "lang" "menu" "network" "stick" "tape")
EXTENSIONS=("java" "js" "html" "css" "une" "sh")

# Initialize counters
declare -A counts
declare -A file_counts
for ext in "${EXTENSIONS[@]}"; do
    counts[$ext]=0
    file_counts[$ext]=0
done

total_lines=0
total_files=0

echo "Scanning directories..."

# 2. Filter valid directories
valid_paths=()
for d in "${DIRS_TO_SEARCH[@]}"; do
    if [[ -d "$d" ]]; then
        valid_paths+=("$d")
    else
        echo "Warning: Directory '$d' not found, skipping." >&2
    fi
done

if [[ ${#valid_paths[@]} -eq 0 ]]; then
    echo "Error: No valid directories found to scan." >&2
    exit 1
fi

# 3. Build the find command arguments safely
#find_args=("${valid_paths[@]}" "-type" "d" "-name" "lib" "-prune" "-o" "-type" "f" "(")
find_args=("${valid_paths[@]}" "-type" "d" "-name" "lib" "-prune" "-o" "-type" "d" "-name" ".*" "-prune" "-o" "-type" "f" "(")
first=true
for ext in "${EXTENSIONS[@]}"; do
    if [ "$first" = true ]; then
        find_args+=("-name" "*.$ext")
        first=false
    else
        find_args+=("-o" "-name" "*.$ext")
    fi
done
find_args+=(")" "-exec" "wc" "-l" "{}" "+")

# 4. Execute and Process
while read -r lines filepath; do
    # Skip the "total" line from wc and empty paths
    [[ "$filepath" == "total" || -z "$filepath" ]] && continue

    # Extract extension
    ext="${filepath##*.}"

    # Update stats
    if [[ -v "counts[$ext]" ]]; then
        ((counts[$ext] += lines))
        ((file_counts[$ext] += 1))
        ((total_lines += lines))
        ((total_files += 1))
    fi
done < <(find "${find_args[@]}" 2>/dev/null | grep -v " total$" || true)

# 5. Output Results
echo -e "\nResults:"
echo "-------------------------------------"
printf "%-10s | %-10s | %-10s\n" "Extension" "Files" "Lines"
echo "-------------------------------------"
for ext in "${EXTENSIONS[@]}"; do
    printf ".%-9s | %'10d | %'10d\n" "$ext" "${file_counts[$ext]}" "${counts[$ext]}"
done
echo "-------------------------------------"
printf "%-10s | %'10d | %'10d\n" "TOTAL" "$total_files" "$total_lines"
