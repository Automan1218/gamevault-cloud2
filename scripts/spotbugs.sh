#!/bin/bash
# ==========================================
# SpotBugs Script for Bug Detection
# Based on Workshop 8 - Linting
# ==========================================

set -e

echo "=========================================="
echo "Running SpotBugs Analysis..."
echo "=========================================="

# Run Maven SpotBugs plugin
mvn compile spotbugs:spotbugs || true

# Check if report exists
if [ -f "target/spotbugsXml.xml" ]; then
    echo "✓ SpotBugs report generated: target/spotbugsXml.xml"

    # Count bugs
    BUGS=$(grep -c "<BugInstance" target/spotbugsXml.xml || echo "0")
    echo "Found $BUGS potential bugs"

    if [ "$BUGS" -gt 0 ]; then
        echo "Warning: Potential bugs detected. Please review the report."
    else
        echo "No bugs found!"
    fi
else
    echo "SpotBugs report not generated"
fi

echo "=========================================="
echo "SpotBugs analysis completed"
echo "=========================================="

exit 0
