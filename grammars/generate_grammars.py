# @author android_zero <github.com/msmt2018>
#Used for automatically generating grammars/grammars.json, thus no manual addition or maintenance is required

import os
import re
import json

def get_gitmodules_paths():
    """Parse the .gitmodules file to extract all paths and language names registered under grammars/"""
    gitmodules_path = ".gitmodules"
    if not os.path.exists(gitmodules_path):
        print(f"Error: Not found in root directory '{gitmodules_path}' Documents！")
        return set()
    
    # 匹配形如 path = grammars/xxxx 的行
    path_regex = re.compile(r'^\s*path\s*=\s*grammars/(.+)$', re.MULTILINE)
    
    with open(gitmodules_path, "r", encoding="utf-8") as f:
        content = f.read()
        
    # 利用 set() 天生去重
    return set(path_regex.findall(content))

def generate_grammars_json():
    grammars_dir = "grammars"
    output_file = os.path.join(grammars_dir, "grammars.json")
    tmp_output_file = os.path.join(grammars_dir, "grammars.json.tmp") # 缓存临时文件
    
    if not os.path.exists(grammars_dir):
        print(f"Error: '{grammars_dir}' folder not found!")
        return

    # 获取并去重 .gitmodules 中的语言
    git_languages = get_gitmodules_paths()
    if not git_languages:
        print("Tip: No grammars/ paths are registered in .gitmodules.")
        return

    # 获取并去重本地实际存在的文件夹
    local_dirs = set([d for d in os.listdir(grammars_dir) 
                      if os.path.isdir(os.path.join(grammars_dir, d)) and not d.startswith('.')])

    # 双向确认（取交集）：严格去重且对齐
    valid_languages = sorted(list(git_languages.intersection(local_dirs)))

    # 打印差异警告（保持透明度）
    for orphaned in local_dirs - git_languages:
        print(f"⚠️ Warning: Local folder found '{orphaned}' Not registered in .gitmodules, removed from configuration.")
    for missing in git_languages - local_dirs:
        print(f"⚠️ Warning: .gitmodules includes '{missing}' However, this directory is not available locally and has been removed from the configuration.")

    # 在内存中构建全新的、完整的配置列表（全量内存缓存）
    new_grammars_list = []
    for lang_name in valid_languages:
        src_dir = os.path.join(grammars_dir, lang_name, "src")
        
        if not os.path.exists(src_dir):
            continue
            
        scanner_c = os.path.join(src_dir, "scanner.c")
        scanner_cc = os.path.join(src_dir, "scanner.cc")
        
        src_extra = []
        if os.path.exists(scanner_c):
            src_extra.append("src/scanner.c")
        elif os.path.exists(scanner_cc):
            src_extra.append("src/scanner.cc")

        new_grammars_list.append({
            "name": lang_name,
            "src.extra": src_extra
        })

    # 安全写入策略：先完整写入临时缓存文件，再安全覆盖目标文件
    try:
        with open(tmp_output_file, "w", encoding="utf-8") as f:
            json.dump(new_grammars_list, f, indent=2, ensure_ascii=False)
        
        # 操作系统级原子替换，如果成功，直接覆盖原文件
        if os.path.exists(output_file):
            os.remove(output_file)
        os.rename(tmp_output_file, output_file)
        
        print(f"\n✨ Success! Complete data has been generated in cache and securely overridden.")
        print(f"📄 The latest total number of valid grammar points: {len(new_grammars_list)} Piece -> Synchronized to the event {output_file}")

    except Exception as e:
        print(f"❌ Write failure, the original file has been protected from impact. Reason for error: {e}")
        if os.path.exists(tmp_output_file):
            os.remove(tmp_output_file)

if __name__ == "__main__":
    generate_grammars_json()
