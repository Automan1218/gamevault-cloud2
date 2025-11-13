#!/bin/bash
# ==================================================
# OWASP ZAP DAST Scan Script - Minimal (2 URLs)
# 专门用于扫描2个指定的前端页面
# ==================================================

set -e

echo "=========================================="
echo "Running OWASP ZAP DAST Scan"
echo "=========================================="

# ============================================
# 配置参数
# ============================================
TARGET_URL="${ZAP_TARGET_URL:-http://3.0.15.128:3000}"
MAX_DURATION="${ZAP_MAX_DURATION:-3}"  # 3分钟足够了

# 要扫描的 URL 列表
SCAN_URLS=(
    "http://3.0.15.128:3000/dashboard/store"
    "http://3.0.15.128:3000/dashboard/forum"
)

echo ""
echo "Configuration:"
echo "  Base URL:       $TARGET_URL"
echo "  Max Duration:   $MAX_DURATION minutes"
echo "  Total URLs:     ${#SCAN_URLS[@]}"
echo ""

# ============================================
# 显示要扫描的 URL
# ============================================
echo "📋 URLs to scan:"
for i in "${!SCAN_URLS[@]}"; do
    echo "  $((i+1)). ${SCAN_URLS[$i]}"
done
echo ""

# ============================================
# 检查远程应用可访问性
# ============================================
echo "Checking remote application..."
max_attempts=3
attempt=0

until curl -sf "$TARGET_URL/" > /dev/null 2>&1 || \
      curl -sf "${SCAN_URLS[0]}" > /dev/null 2>&1 || \
      [ $attempt -eq $max_attempts ]; do
    attempt=$((attempt + 1))
    echo "  Attempt $attempt/$max_attempts..."
    sleep 3
done

if [ $attempt -eq $max_attempts ]; then
    echo "⚠️  Warning: Application not responding at $TARGET_URL"
    echo "Proceeding with scan anyway..."
else
    echo "✅ Application is accessible"
fi
echo ""

# ============================================
# 拉取ZAP镜像
# ============================================
echo "Pulling OWASP ZAP Docker image..."
docker pull ghcr.io/zaproxy/zaproxy:stable
echo ""

# ============================================
# 运行ZAP扫描
# ============================================
echo "=========================================="
echo "Starting ZAP scan (${#SCAN_URLS[@]} URLs)..."
echo "This should complete in 1-2 minutes"
echo "=========================================="

START_TIME=$(date +%s)

# 创建报告目录
mkdir -p zap-reports

# 扫描每个 URL
REPORT_FILES=()
for i in "${!SCAN_URLS[@]}"; do
    URL="${SCAN_URLS[$i]}"
    REPORT_NAME="zap_report_$((i+1)).html"
    CONTAINER_NAME="zap-scan-$((i+1))"

    echo ""
    echo "Scanning URL $((i+1))/${#SCAN_URLS[@]}: $URL"
    echo "----------------------------------------"

    # 运行 ZAP 扫描
    docker run --name "$CONTAINER_NAME" \
        -v $(pwd):/zap/wrk:rw \
        ghcr.io/zaproxy/zaproxy:stable \
        zap-baseline.py \
        -t "$URL" \
        -m 0 \
        -r "/zap/wrk/zap-reports/$REPORT_NAME" \
        -l PASS || true

    # 清理容器
    docker rm "$CONTAINER_NAME" 2>/dev/null || true

    # 记录报告文件
    if [ -f "zap-reports/$REPORT_NAME" ]; then
        REPORT_FILES+=("zap-reports/$REPORT_NAME")
        echo "✅ Report generated: $REPORT_NAME"
    else
        echo "⚠️  Report not generated for $URL"
    fi
done

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo "=========================================="
echo "Scan completed in $((DURATION / 60))m $((DURATION % 60))s"
echo "=========================================="

# ============================================
# 合并报告
# ============================================
echo ""
echo "Merging reports..."

if [ ${#REPORT_FILES[@]} -gt 0 ]; then
    # 合并所有报告
    cat "${REPORT_FILES[@]}" > zap_baseline_report.html
    echo "✅ Merged report created: zap_baseline_report.html"
else
    echo "❌ No reports to merge"
    exit 1
fi

# ============================================
# 分析结果
# ============================================
echo ""
echo "Analyzing results..."

if [ -f "zap_baseline_report.html" ]; then
    # 统计漏洞数量
    HIGH_COUNT=$(grep -o "FAIL-High" zap_baseline_report.html | wc -l || echo 0)
    MEDIUM_COUNT=$(grep -o "FAIL-Medium" zap_baseline_report.html | wc -l || echo 0)
    LOW_COUNT=$(grep -o "FAIL-Low" zap_baseline_report.html | wc -l || echo 0)
    PASS_COUNT=$(grep -o "PASS" zap_baseline_report.html | wc -l || echo 0)

    # 显示结果
    echo ""
    echo "╔════════════════════════════════════════╗"
    echo "║      Vulnerability Summary             ║"
    echo "╠════════════════════════════════════════╣"
    printf "║  🔴 High:   %-4s                      ║\n" "$HIGH_COUNT"
    printf "║  🟡 Medium: %-4s                      ║\n" "$MEDIUM_COUNT"
    printf "║  🔵 Low:    %-4s                      ║\n" "$LOW_COUNT"
    printf "║  ✅ Passed: %-4s                      ║\n" "$PASS_COUNT"
    echo "╚════════════════════════════════════════╝"
    echo ""

    # 总结
    if [ "$HIGH_COUNT" -gt 0 ]; then
        echo "🚨 CRITICAL: $HIGH_COUNT high severity vulnerabilities found!"
        echo "   Immediate action required."
    elif [ "$MEDIUM_COUNT" -gt 0 ]; then
        echo "⚠️  WARNING: $MEDIUM_COUNT medium severity vulnerabilities found."
        echo "   Please review and address these issues."
    else
        echo "✅ SUCCESS: No high or medium severity vulnerabilities detected."
    fi

    echo ""
    echo "Reports available:"
    echo "   - Merged HTML: zap_baseline_report.html"
    echo "   - Individual reports in: zap-reports/"

    # 显示扫描的URL
    echo ""
    echo "Scanned URLs:"
    for i in "${!SCAN_URLS[@]}"; do
        echo "   $((i+1)). ${SCAN_URLS[$i]}"
    done

else
    echo "ERROR: Report not generated"
    echo "   Please check the logs above for details."
    exit 1
fi

echo ""
echo "=========================================="
echo "OWASP ZAP scan completed successfully"
echo "=========================================="

exit 0