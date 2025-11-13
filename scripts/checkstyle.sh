#!/bin/bash
# ==========================================
# Checkstyle Script for Java Code Quality
# 改进版本 - 不会因为检查失败而停止构建
# ==========================================

set -e

echo "=========================================="
echo "运行 Checkstyle 代码质量检查..."
echo "=========================================="
echo ""

# 颜色输出
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 设置配置文件路径
CHECKSTYLE_CONFIG="checkstyle-simple.xml"
CHECKSTYLE_SUPPRESSIONS="checkstyle-suppressions.xml"

# 检查配置文件是否存在
if [ ! -f "$CHECKSTYLE_CONFIG" ]; then
    echo -e "${YELLOW}⚠ 警告: 找不到 $CHECKSTYLE_CONFIG，使用默认配置${NC}"
    CHECKSTYLE_CONFIG="google_checks.xml"
fi

echo "使用配置文件: $CHECKSTYLE_CONFIG"
echo ""

# 运行 Checkstyle，即使失败也继续执行
echo "正在分析代码..."
mvn checkstyle:checkstyle \
    -Dcheckstyle.config.location="$CHECKSTYLE_CONFIG" \
    -Dcheckstyle.suppressions.location="$CHECKSTYLE_SUPPRESSIONS" \
    -Dcheckstyle.consoleOutput=true \
    -Dcheckstyle.failsOnError=false \
    -Dcheckstyle.failOnViolation=false \
    -Dcheckstyle.violationSeverity=warning \
    || true

echo ""
echo "=========================================="
echo "分析结果"
echo "=========================================="

# 检查报告是否生成
REPORT_FOUND=false

# 查找所有模块的 checkstyle 报告
for report in $(find . -name "checkstyle-result.xml" 2>/dev/null); do
    REPORT_FOUND=true
    MODULE_NAME=$(dirname $(dirname $report) | xargs basename)

    echo ""
    echo "📦 模块: $MODULE_NAME"
    echo "   报告: $report"

    if [ -f "$report" ]; then
        # 统计不同级别的违规
        ERRORS=$(grep -c 'severity="error"' "$report" 2>/dev/null || echo "0")
        WARNINGS=$(grep -c 'severity="warning"' "$report" 2>/dev/null || echo "0")
        INFOS=$(grep -c 'severity="info"' "$report" 2>/dev/null || echo "0")
        TOTAL=$((ERRORS + WARNINGS + INFOS))

        echo "   违规统计:"

        if [ "$ERRORS" -gt 0 ]; then
            echo -e "   ${RED}  ❌ 错误: $ERRORS${NC}"
        else
            echo -e "   ${GREEN}  ✓ 错误: 0${NC}"
        fi

        if [ "$WARNINGS" -gt 0 ]; then
            echo -e "   ${YELLOW}  ⚠ 警告: $WARNINGS${NC}"
        else
            echo -e "   ${GREEN}  ✓ 警告: 0${NC}"
        fi

        if [ "$INFOS" -gt 0 ]; then
            echo "     ℹ 提示: $INFOS"
        fi

        echo "   总计: $TOTAL 个问题"

        # 显示前 5 个最常见的违规
        if [ "$TOTAL" -gt 0 ]; then
            echo ""
            echo "   前 5 个常见问题:"
            grep '<error' "$report" 2>/dev/null | \
                sed 's/.*source="\([^"]*\)".*/\1/' | \
                sort | uniq -c | sort -rn | head -5 | \
                while read count rule; do
                    echo "     - $rule: $count 次"
                done
        fi
    fi
done

echo ""
echo "=========================================="

if [ "$REPORT_FOUND" = false ]; then
    echo -e "${YELLOW}⚠ 未找到 Checkstyle 报告${NC}"
    echo "请确保 Maven 配置正确"
else
    echo -e "${GREEN}✓ Checkstyle 分析完成${NC}"
    echo ""
    echo "查看详细报告:"
    find . -name "checkstyle.html" 2>/dev/null | while read html; do
        echo "  file://$PWD/$html"
    done
fi

echo "=========================================="
echo ""

# 总是成功退出，不影响后续流程
exit 0