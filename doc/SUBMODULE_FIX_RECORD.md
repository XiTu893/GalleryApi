# Git Submodule 问题修复记录

## 问题描述

GitHub Actions CI 构建失败，错误信息：
```
Error: fatal: remote error: upload-pack: not our ref e65869a3612cf710dadfda8d89972ea237d27454
Error: fatal: Fetched in submodule path 'gallery', but it did not contain e65869a3612cf710dadfda8d89972ea237d27454. Direct fetching of that commit failed.
```

## 原因分析

Submodule `gallery` 指向的 commit `e65869a3612cf710dadfda8d89972ea237d27454` 在远程仓库中不存在。

可能的原因：
1. 上游仓库进行了 force push，重写了历史
2. 该 commit 已被删除或清理
3. Submodule 引用了一个不存在的 commit hash

## 解决方案

### 步骤 1: 更新 submodule 到最新可用 commit

```bash
cd gallery
git fetch origin
git checkout main
git pull origin main
cd ..
```

### 步骤 2: 获取新的 commit hash

```bash
cd gallery
git rev-parse HEAD
# 输出: 688b4a2b305f604caaf2e6073e24f0b9d20c6bc2
cd ..
```

### 步骤 3: 更新父仓库的 submodule 引用

```bash
git add gallery
git commit -m "Update gallery submodule to latest commit (688b4a2)"
```

### 步骤 4: 推送到远程仓库

```bash
git push origin master
```

### 步骤 5: 验证

```bash
git submodule status
# 应该显示新的 commit hash，没有前缀符号
# 688b4a2b305f604caaf2e6073e24f0b9d20c6bc2 gallery (1.0.13-58-g688b4a2)
```

## 结果

✅ Submodule 已成功更新到 commit `688b4a2b305f604caaf2e6073e24f0b9d20c6bc2`  
✅ 父仓库已提交并推送更新  
✅ GitHub Actions CI 应该能够正常获取 submodule  

## 预防措施

### 1. 定期更新 submodule

建议定期运行以下命令保持 submodule 同步：

```bash
git submodule update --remote --merge
git add gallery
git commit -m "Update gallery submodule"
git push
```

### 2. 使用分支而非固定 commit

如果希望始终使用最新的代码，可以在 `.gitmodules` 中指定分支：

```ini
[submodule "gallery"]
    path = gallery
    url = https://github.com/google-ai-edge/gallery.git
    branch = main
```

然后使用以下命令更新：

```bash
git submodule update --remote --merge
```

**注意**：使用分支会导致每次构建时获取不同的代码，可能影响构建的可重现性。

### 3. 在 CI 中添加 submodule 验证

在 GitHub Actions workflow 中添加验证步骤：

```yaml
- name: Verify submodule
  run: |
    git submodule status
    cd gallery && git log -1 --oneline && cd ..
```

## 常见问题

### Q1: 如何查看 submodule 的当前状态？

```bash
git submodule status
```

输出说明：
- 无前缀：正常，commit 存在
- `-` 前缀：submodule 未初始化
- `+` 前缀：submodule 已修改但未提交
- `U` 前缀：合并冲突

### Q2: 如何重置 submodule 到记录的 commit？

```bash
git submodule update --init --force
```

### Q3: 如何完全重新初始化 submodule？

```bash
git submodule deinit -f gallery
git submodule update --init gallery
```

### Q4: 如何查看所有 submodule 的 URL？

```bash
git config --file .gitmodules --get-regexp path
git config --file .gitmodules --get-regexp url
```

## 相关文档

- [Git Submodules 官方文档](https://git-scm.com/book/en/v2/Git-Tools-Submodules)
- [GitHub Actions - Checkout Action](https://github.com/actions/checkout)
- [Android CI Workflow](../.github/workflows/android-ci.yml)

---

**修复日期**: 2026-05-15  
**修复者**: AI Assistant  
**Submodule**: gallery  
**旧 Commit**: e65869a3612cf710dadfda8d89972ea237d27454 (不存在)  
**新 Commit**: 688b4a2b305f604caaf2e6073e24f0b9d20c6bc2 (最新)
