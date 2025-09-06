#!/bin/bash

java_lines=0
html_lines=0
css_lines=0
js_lines=0
une_lines=0
total_lines=0

# Search java files in current folder

for file in $(find . -type f); do

    lines_in_file=0

    case "$file" in
        *.java)
            lines_in_file=$(wc -l < "$file")
            java_lines=$((java_lines + lines_in_file))
            ;;
    esac

    total_lines=$((total_lines + lines_in_file))
done

# Search Une, Java, JS, HTML and CSS files in 'todeploy' folder

cd todeploy

# for file in $(find . -path "*/lib" -prune -o -type f -print); do    # Exclude folders named 'lib'
for file in $(find . -print); do

    lines_in_file=0

    case "$file" in
        *.java)
            lines_in_file=$(wc -l < "$file")
            java_lines=$((java_lines + lines_in_file))
            ;;

        *.js)
            lines_in_file=$(wc -l < "$file")
            js_lines=$((js_lines + lines_in_file))
            ;;

        *.html)
            lines_in_file=$(wc -l < "$file")
            html_lines=$((html_lines + lines_in_file))
            ;;

        *.css)
            lines_in_file=$(wc -l < "$file")
            css_lines=$((css_lines + lines_in_file))
            ;;

        *.une)
            # Handle other file types here (or ignore them)
            lines_in_file=$(wc -l < "$file")
            une_lines=$((une_lines + lines_in_file))
            ;;
    esac

    total_lines=$((total_lines + lines_in_file))
done

total_lines=$((total_lines + lines_in_file))


echo "Total lines in .java files: $(printf "%'7d" $java_lines)"
echo "Total lines in .js   files: $(printf "%'7d" $js_lines)"
echo "Total lines in .html files: $(printf "%'7d" $html_lines)"
echo "Total lines in .css  files: $(printf "%'7d" $css_lines)"
echo "Total lines in .une  files: $(printf "%'7d" $une_lines)"
echo "                            -------"
echo "                 Big total: $(printf "%'7d" $total_lines)"
