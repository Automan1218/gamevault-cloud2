#!/bin/bash
# ==========================================
# Checkstyle Script for Java Code Quality
# Based on Workshop 8 - Linting
# ==========================================

set -e

echo "=========================================="
echo "Running Checkstyle Analysis..."
echo "=========================================="

# Run Maven Checkstyle plugin
mvn checkstyle:checkstyle -Dcheckstyle.config.location=google_checks.xml || true

# Check if report exists
if [ -f "target/checkstyle-result.xml" ]; then
    echo "✓ Checkstyle report generated: target/checkstyle-result.xml"

    # Count violations
    VIOLATIONS=$(grep -c "<error" target/checkstyle-result.xml || echo "0")
    echo "Found $VIOLATIONS style violations"

    if [ "$VIOLATIONS" -gt 0 ]; then
        echo "⚠ Warning: Code style issues detected. Please review the report."
    else
        echo "✓ No style violations found!"
    fi
else
    echo "⚠ Checkstyle report not generated"
fi

echo "=========================================="
echo "Checkstyle analysis completed"
echo "=========================================="

exit 0
