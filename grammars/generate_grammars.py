# @author android_zero <github.com/msmt2018> [update] : Enhanced to support deep nested composite sub.paths (e.g., ocaml/grammars/interface) and recursive parser search.
# Used for automatically generating grammars/grammars.json, thus no manual addition or maintenance is required

import os
import re
import json

def get_gitmodules_paths():
    """Parse the .gitmodules file to extract all paths and language names registered under grammars/"""
    gitmodules_path = ".gitmodules"
    if not os.path.exists(gitmodules_path):
        print(f"Error: Not found in root directory '{gitmodules_path}' Documents！")
        return set()
    
    path_regex = re.compile(r'^\s*path\s*=\s*grammars/(.+)$', re.MULTILINE)
    
    with open(gitmodules_path, "r", encoding="utf-8") as f:
        content = f.read()
        
    return set(path_regex.findall(content))

def find_parser_subpaths(base_dir, current_rel_path=""):
    """
    递归向下递归扫描目录，寻找所有包含 src/parser.c 或 src/parser.cc 的叶子或中间子目录。
    返回形如相对于 base_dir 的子路径列表，例如 ["grammars/interface", "grammars/ocaml", "grammars/type"]
    """
    results = []
    abs_current_dir = os.path.join(base_dir, current_rel_path) if current_rel_path else base_dir
    
    if not os.path.exists(abs_current_dir) or not os.path.isdir(abs_current_dir):
        return results

    # 检查当前目录下是否有 src/parser.c 或 src/parser.cc
    src_dir = os.path.join(abs_current_dir, "src")
    has_parser = os.path.exists(os.path.join(src_dir, "parser.c")) or os.path.exists(os.path.join(src_dir, "parser.cc"))
    
    if has_parser and current_rel_path:
        # 如果当前层级本身就是一个完整的 parser 目录，则记录
        results.append(current_rel_path)

    try:
        entries = os.listdir(abs_current_dir)
    except OSError:
        return results

    for entry in sorted(entries):
        if entry.startswith('.'):
            continue
        full_entry_path = os.path.join(abs_current_dir, entry)
        if os.path.isdir(full_entry_path):
            next_rel_path = os.path.join(current_rel_path, entry) if current_rel_path else entry
            # 递归调用
            results.extend(find_parser_subpaths(base_dir, next_rel_path))

    return results

def generate_grammars_json():
    grammars_dir = "grammars"
    output_file = os.path.join(grammars_dir, "grammars.json")
    tmp_output_file = os.path.join(grammars_dir, "grammars.json.tmp")
    
    if not os.path.exists(grammars_dir):
        print(f"Error: '{grammars_dir}' folder not found!")
        return

    git_languages = get_gitmodules_paths()
    if not git_languages:
        print("Tip: No grammars/ paths are registered in .gitmodules.")
        return

    local_dirs = set([d for d in os.listdir(grammars_dir) 
                      if os.path.isdir(os.path.join(grammars_dir, d)) and not d.startswith('.')])

    valid_languages = sorted(list(git_languages.intersection(local_dirs)))

    new_grammars_list = []
    for lang_name in valid_languages:
        lang_full_path = os.path.join(grammars_dir, lang_name)
        
        # 使用递归深度扫描探测是否存在多个子解析器
        discovered_subpaths = find_parser_subpaths(lang_full_path)
        # 路径格式化为统一的斜杠
        discovered_subpaths = [p.replace("\\", "/") for p in discovered_subpaths]

        # 如果发现多于 1 个子解析器，或者子路径包含深层嵌套（例如带 grammars/ 或类似结构）
        if len(discovered_subpaths) >= 2 or any("/grammars/" in p or p.count("/") >= 1 for p in discovered_subpaths):
            # 过滤掉根目录自己（如果根目录本身也有 src/parser.c 且被误判的情况）
            sub_paths = [p for p in discovered_subpaths if p != "src"]
            
            grammar_entry = {
                "name": lang_name,
                "composite": True,
                "sub.paths": sub_paths,
                "src.extra": []
            }
            
            # 检查根目录是否有额外的 scanner
            extra_scanner_c = os.path.join(lang_full_path, "scanner.c")
            extra_scanner_cc = os.path.join(lang_full_path, "scanner.cc")
            if os.path.exists(extra_scanner_c):
                grammar_entry["src.extra"].append("scanner.c")
            elif os.path.exists(extra_scanner_cc):
                grammar_entry["src.extra"].append("scanner.cc")
                
            new_grammars_list.append(grammar_entry)
            print(f"🧩 Detected Deep Composite Grammar -> [{lang_name}] with sub.paths: {sub_paths}")

        else:
            # 传统单解析器
            src_dir = os.path.join(lang_full_path, "src")
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
                "composite": False,
                "src.extra": src_extra
            })
            print(f"📦 Detected Single Grammar           -> [{lang_name}]")

    try:
        with open(tmp_output_file, "w", encoding="utf-8") as f:
            json.dump(new_grammars_list, f, indent=2, ensure_ascii=False)
        
        if os.path.exists(output_file):
            os.remove(output_file)
        os.rename(tmp_output_file, output_file)
        
        print(f"\n✨ Success! Deep composite data generated.")
        print(f"📄 Total valid grammars: {len(new_grammars_list)} -> Synchronized to {output_file}")

    except Exception as e:
        print(f"❌ Write failure: {e}")
        if os.path.exists(tmp_output_file):
            os.remove(tmp_output_file)

if __name__ == "__main__":
    generate_grammars_json()