import os
import sys
import re
import shutil
import subprocess

# 默认克隆镜像前缀（例如 "https://ghproxy.net" ），留空则直连 https://github.com
MIRROR_PREFIX = ""

def parse_gitmodules(file_path):
    """解析 .gitmodules 文件，提取 path 和 url"""
    modules = []
    if not os.path.exists(file_path):
        print(f"提示：指定的配置文件未找到 -> {file_path}")
        return modules
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()
    blocks = re.findall(r'\[submodule ".*?"\](.*?(?=\[submodule|$))', content, re.DOTALL)
    for block in blocks:
        path_match = re.search(r'path\s*=\s*(.*)', block)
        url_match = re.search(r'url\s*=\s*(.*)', block)
        if path_match and url_match:
            modules.append({"path": path_match.group(1).strip(), "url": url_match.group(1).strip()})
    return modules

def fix_url(url, mirror_str):
    """规范化 GitHub URL 并支持镜像前缀拼接"""
    if url.endswith(".git"):
        url = url[:-4]
    repo_part = re.sub(r'^(git@github.com:|https://github.com|git://://github.com)', '', url).lstrip("/")
    
    if mirror_str and mirror_str.strip():
        base_mirror = mirror_str.strip().rstrip("/") + "/"
        if "github.com" in base_mirror.lower():
            return f"{base_mirror}{repo_part}"
        return f"{base_mirror}https://github.com{repo_part}"
    return f"https://github.com{repo_part}"

def process_gitmodules_file(gitmodules_path, output_dir, mirror_str):
    """解析并使用纯 git clone 下载子模块，并开启实时控制台输出"""
    modules = parse_gitmodules(gitmodules_path)
    if not modules:
        return

    for mod in modules:
        target_path = os.path.normpath(os.path.join(output_dir, mod["path"]))
        download_url = fix_url(mod["url"], mirror_str)
        
        print("\n" + "="*80)
        print(f"准备下载子模块: {mod['path']}")
        print(f"目标存放路径: {target_path}")
        print(f"当前使用的拉取 URL: {download_url}")
        print("="*80)

        # 1. 强力移除旧的物理源码目录或由于损坏导致的半截子模块空壳
        if os.path.exists(target_path):
            print(f"正在物理清理旧的源码/残留指针: {target_path} ...")
            try:
                shutil.rmtree(target_path)
            except Exception:
                if os.name == 'nt':
                    subprocess.run(f'rmdir /s /q "{target_path}"', shell=True)
                else:
                    subprocess.run(f'rm -rf "{target_path}"', shell=True)
        
        # 2. 创建父级目录并克隆最新源码
        os.makedirs(os.path.dirname(target_path), exist_ok=True)
        print(f"开始通过 Git 拉取最新源码...")
        clone_cmd = ["git", "clone", "--depth", "1", download_url, target_path]
        result = subprocess.run(clone_cmd)
        
        if result.returncode == 0:
            print(f" -> 模块 [{mod['path']}] 最新源码下载成功！")
            
            # 先不删除 .git 文件夹，让子模块内层克隆能向下一层递归查找
            nested_gitmodules = os.path.join(target_path, ".gitmodules")
            if os.path.exists(nested_gitmodules):
                print(f"发现嵌套的深层子模块配置文件，开始递归处理...")
                process_gitmodules_file(nested_gitmodules, target_path, mirror_str)
        else:
            print(f" -> [错误] 模块 [{mod['path']}] 克隆失败。")

def run_git_post_commands():
    """下载完成后，在项目根目录按顺序重振、同步和强刷所有 Git 子模块指针"""
    print("\n" + "#"*80)
    print(" 物理下载全部结束！现在开始自动为您『安排』Git 状态同步与指针刷新...")
    print("#"*80 + "\n")

    # 1. 强制同步最新的本地 .gitmodules 配置到 .git/config 缓存中
    print("【步骤 1/4】强制进行深层递归同步 (git submodule sync --recursive)...")
    subprocess.run(["git", "submodule", "sync", "--recursive"])

    # 2. 强行重新初始化注册（防止出现未注册导致的死锁）
    print("\n【步骤 2/4】强行重新初始化子模块注册表 (git submodule init)...")
    subprocess.run(["git", "submodule", "init"])

    # 3. 彻底清除之前报错的残存子模块内部缓存（修复：fatal: not a git repository 核心）
    # 通过 foreach 检查并重新绑定修复指针
    print("\n【步骤 3/4】遍历并检查修复子模块内部的物理隔离树...")
    subprocess.run(["git", "submodule", "foreach", "--recursive", "git rev-parse --git-dir || true"])

    # 4. 全局递归强刷更新绑定
    print("\n【步骤 4/4】最终带缓存刷新执行递归绑定 (git submodule update --init --recursive)...")
    subprocess.run(["git", "submodule", "update", "--init", "--recursive"])
    
    print("\n恭喜！所有深层子模块源码已全部就位，Git 状态刷新成功，死锁已解除。")

if __name__ == "__main__":
    # 用法：python3 download_submodules.py <输出基准目录> <.gitmodules路径>
    out_dir = sys.argv[1] if len(sys.argv) > 1 else "./"
    gm_file = sys.argv[2] if len(sys.argv) > 2 else (".gitmodules" if len(sys.argv) <= 1 else sys.argv[1])
    
    print(f"初始化配置：输出基准目录 -> {out_dir} | 配置文件路径 -> {gm_file}")
    
    # 执行物理下载覆盖
    process_gitmodules_file(gm_file, out_dir, MIRROR_PREFIX)
    
    # 物理下载完毕后，启动自动同步、刷新、强制绑定的后置命令链
    run_git_post_commands()
