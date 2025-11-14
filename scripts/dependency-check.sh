#!/bin/bash
# ==========================================
# OWASP Dependency Check Script
# Based on Workshop 8 - SAST Scan
# ==========================================

set -e

echo "=========================================="
echo "Running OWASP Dependency Check..."
echo "=========================================="

# Download Dependency Check if not exists
if [ ! -d "dependency-check" ]; then
    echo "Downloading OWASP Dependency Check..."
    wget https://github.com/jeremylong/DependencyCheck/releases/download/v9.0.9/dependency-check-9.0.9-release.zip
    unzip -q dependency-check-9.0.9-release.zip
    rm dependency-check-9.0.9-release.zip
fi

# Run dependency check
echo "Scanning dependencies for vulnerabilities..."
./dependency-check/bin/dependency-check.sh \
    --project "GameVault Backend" \
    --scan . \
    --format HTML \
    --format JSON \
    --out ./dependency-check-report \
    --suppression ./dependency-check-suppression.xml 2>/dev/null || true

# Check results
if [ -f "./dependency-check-report/dependency-check-report.html" ]; then
    echo "✓ Dependency Check report generated"

    # Count vulnerabilities if JSON exists
    if [ -f "./dependency-check-report/dependency-check-report.json" ]; then
        CRITICAL=$(grep -o '"severity":"CRITICAL"' ./dependency-check-report/dependency-check-report.json | wc -l || echo "0")
        HIGH=$(grep -o '"severity":"HIGH"' ./dependency-check-report/dependency-check-report.json | wc -l || echo "0")

        echo "Found vulnerabilities:"
        echo "  - Critical: $CRITICAL"
        echo "  - High: $HIGH"

        if [ "$CRITICAL" -gt 0 ]; then
            echo "❌ Critical vulnerabilities detected!"
        fi
    fi
else
    echo "⚠ Dependency Check report not generated"
fi

echo "=========================================="
echo "Dependency Check completed"
echo "=========================================="

exit 0
