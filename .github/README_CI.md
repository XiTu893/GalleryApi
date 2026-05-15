# GitHub Actions CI/CD 配置

## 概述

本项目已配置 GitHub Actions 自动编译流程，每次推送到 `master` 或 `main` 分支时会自动触发 Android APK 编译。

## 工作流程

### 触发条件
- ✅ Push 到 `master` 或 `main` 分支
- ✅ Pull Request 到 `master` 或 `main` 分支

### 编译步骤

1. **Checkout 代码**
   - 克隆仓库
   - 递归更新子模块（gallery）

2. **设置 JDK 17**
   - 使用 Temurin JDK 17
   - 启用 Gradle 缓存加速

3. **编译项目**
   - 授予 gradlew 执行权限
   - 执行 `./gradlew build`

4. **上传产物**
   - 成功：上传 debug APK（保留 30 天）
   - 失败：上传构建报告（保留 7 天）

## 查看编译状态

### 方法 1: GitHub 网页
1. 访问 https://github.com/XiTu893/GalleryApi/actions
2. 查看最近的 workflow 运行状态
3. 点击具体的 run 查看详细日志

### 方法 2: 下载 APK
1. 进入成功的 workflow run
2. 在 "Artifacts" 部分找到 `app-debug-apk`
3. 点击下载获取 APK 文件

## 配置文件

位置：`.github/workflows/android-ci.yml`

主要配置项：
```yaml
name: Android CI
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - checkout (with submodules)
      - setup JDK 17
      - gradle build
      - upload artifacts
```

## 常见问题

### Q1: 编译失败怎么办？
查看 workflow 的详细日志，常见原因：
- Gradle 依赖下载失败（网络问题）
- 代码编译错误
- 子模块未正确初始化

### Q2: 如何手动触发编译？
目前配置为自动触发，如需手动触发可以：
1. 在 GitHub Actions 页面点击 "Run workflow"
2. 或者添加 `workflow_dispatch` 触发器

### Q3: 如何优化编译速度？
- ✅ 已启用 Gradle 缓存
- ✅ 使用 `--no-daemon` 避免后台进程
- 可以考虑添加依赖缓存

### Q4: APK 在哪里下载？
编译成功后，在 workflow run 页面的 "Artifacts" 部分下载。

## 自定义配置

### 添加 Release 编译
在 `android-ci.yml` 中添加：

```yaml
- name: Build Release APK
  run: cd gallery/Android/src && ./gradlew assembleRelease --no-daemon
  
- name: Upload Release APK
  uses: actions/upload-artifact@v4
  with:
    name: app-release-apk
    path: gallery/Android/src/app/build/outputs/apk/release/*.apk
```

### 添加测试
```yaml
- name: Run Tests
  run: cd gallery/Android/src && ./gradlew test --no-daemon
```

### 添加代码质量检查
```yaml
- name: Run Lint
  run: cd gallery/Android/src && ./gradlew lint --no-daemon
```

## 相关资源

- [GitHub Actions 文档](https://docs.github.com/en/actions)
- [Android CI 最佳实践](https://developer.android.com/studio/build/optimize-your-build)
- [Gradle 缓存优化](https://docs.gradle.org/current/userguide/build_cache.html)

## 状态徽章

可以将以下徽章添加到 README.md 中：

```markdown
![CI Status](https://github.com/XiTu893/GalleryApi/actions/workflows/android-ci.yml/badge.svg)
```

---

**注意**: 首次编译可能需要较长时间（5-10 分钟）用于下载依赖，后续编译会利用缓存加快速度。
