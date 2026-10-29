#!/bin/bash

# ================= 配置区域 =================
# 只需填入最基础的点分包名
OLD_BASE="com.zerostudio.idea"
NEW_BASE="com.zerostudio.idea"
# ============================================

# 自动推导各种变体 (点、路径、下划线、横杠)
VARIANTS=("." "/" "_" "-")

echo "🎯 开始全量重构..."

# 1. 第一阶段：全量替换文件内部的文本内容
echo "📝 正在替换文件内容 (代码、XML、Gradle)..."
# 排除 .git, .gradle, .idea, build 等干扰目录
find . -type f -not -path '*/.*' -not -path '*/build/*' | while read -r file; do
    for sep in "${VARIANTS[@]}"; do
        OLD_STR=$(echo "$OLD_BASE" | tr '.' "$sep")
        NEW_STR=$(echo "$NEW_BASE" | tr '.' "$sep")
        # 使用 | 作为 sed 分隔符，防止路径斜杠干扰
        sed -i "s|$OLD_STR|$NEW_STR|g" "$file"
    done
done

# 2. 第二阶段：物理目录重构 (核心修正)
echo "📁 正在重构物理目录结构..."
for sep in "/" "_" "-"; do
    OLD_STR=$(echo "$OLD_BASE" | tr '.' "$sep")
    NEW_STR=$(echo "$NEW_BASE" | tr '.' "$sep")

    # 使用 -path 代替 -name，解决警告问题
    # -depth 确保从最深层开始处理
    find . -depth -type d -path "*$OLD_STR*" | while read -r src; do
        # 仅替换路径中最后匹配的部分，防止路径前缀被误杀
        dst="${src//$OLD_STR/$NEW_STR}"
        
        if [ "$src" != "$dst" ]; then
            echo "迁移: $src -> $dst"
            mkdir -p "$(dirname "$dst")"
            # 移动内容：如果目标已存在则合并，否则移动整个目录
            if [ -d "$dst" ]; then
                mv "$src"/* "$dst/" 2>/dev/null && rm -rf "$src"
            else
                mv "$src" "$dst"
            fi
        fi
    done
done

# 3. 第三阶段：重命名单个文件名 (例如含有包名的 .java 或 .so 文件)
echo "📄 正在重命名含关键词的文件名..."
find . -depth -type f -not -path '*/.*' | while read -r src; do
    filename=$(basename "$src")
    new_filename="$filename"
    
    for sep in "." "_" "-"; do
        OLD_STR=$(echo "$OLD_BASE" | tr '.' "$sep")
        NEW_STR=$(echo "$NEW_BASE" | tr '.' "$sep")
        new_filename="${new_filename//$OLD_STR/$NEW_STR}"
    done

    if [ "$filename" != "$new_filename" ]; then
        dst="$(dirname "$src")/$new_filename"
        echo "更名文件: $filename -> $new_filename"
        mv "$src" "$dst"
    fi
done

# 4. 最后清理空目录
find . -type d -empty -delete 2>/dev/null

echo -e "\n✅ 重构任务全部完成！"
