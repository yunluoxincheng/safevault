# Skill Dispatcher

**版本**: 1.0.0
**作者**: SafeVault Team
**用途**: 智能分发用户请求到合适的技能

---

## 技能目标

自动分析用户请求，匹配最合适的 skill，提高开发效率和准确性。

---

## 工作流程

```
用户请求
    ↓
1. 请求类型分析
    ├─ UI/界面相关？
    ├─ 网络/同步相关？
    ├─ 安全/加密相关？
    ├─ 调试/错误修复？
    ├─ 文档相关？
    ├─ 重构相关？
    └─ OpenSpec 相关？
    ↓
2. 关键词匹配
    ├─ 查找匹配的 triggers
    ├─ 查找匹配的 error_patterns
    └─ 查找匹配的 file_patterns
    ↓
3. 优先级排序
    ├─ high > medium > low
    └─ 精确匹配 > 模糊匹配
    ↓
4. 调用匹配的 skill
    ├─ 单个匹配 → 直接调用
    ├─ 多个匹配 → 询问用户
    └─ 无匹配 → 通用处理
    ↓
5. 执行完成
```

---

## 触发条件

### 自动触发

当用户请求包含以下关键词时自动分析：
- 任何代码修改/修复请求
- 任何功能实现请求
- 任何问题诊断请求

### 手动调用

```
/skill-dispatcher
/dispatch
```

---

## Skill 匹配规则

### 1. UI/界面类请求

| 关键词 | 触发 Skill | 文件模式 |
|--------|-----------|----------|
| 更新界面, 改UI, 界面美化 | `android-ui-modernization` | `ui/**/*`, `res/layout/*` |
| Material Design 3, M3 | `android-ui-modernization` | `**/*Material*` |

### 2. 安全/加密类请求

| 关键词 | 触发 Skill | 错误模式 |
|--------|-----------|----------|
| 加密, 解密, 密钥 | `android-encryption-fixes` | `javax.crypto.*` |
| BadPaddingException | `android-encryption-fixes` | `BadPaddingException` |
| IllegalBlockSizeException | `android-encryption-fixes` | `IllegalBlockSizeException` |
| KeyPermanentlyInvalidatedException | `android-encryption-fixes` | `KeyPermanentlyInvalidatedException` |
| 生物识别, 指纹, 面容 | `android-biometric-fixes` | `BiometricPrompt.*` |
| FLAG_SECURE, 截图 | `android-security-practices` | `FLAG_SECURE` |

### 3. 网络/同步类请求

| 关键词 | 触发 Skill | 错误模式 |
|--------|-----------|----------|
| API调用, HTTP请求 | `android-retrofit-network` | `Retrofit.*`, `OkHttp.*` |
| WebSocket, 实时消息 | `android-network-sync-fixes` | `WebSocket.*` |
| 同步失败, 数据不一致 | `android-network-sync-fixes` | `Sync.*Error` |
| Token刷新, 401/403错误 | `android-network-sync-fixes` | `401|403` |

### 4. 邮箱验证类请求

| 关键词 | 触发 Skill | 错误模式 |
|--------|-----------|----------|
| 邮箱, 验证码, OTP | `android-email-verification-fixes` | `email.*invalid` |
| 邮件发送失败 | `android-email-verification-fixes` | `send.*email.*fail` |

### 5. 调试/修复类请求

| 关键词 | 触发 Skill |
|--------|-----------|
| 修复bug, 崩溃, ANR | `android-debugging-fixes` |
| 异常处理, NullPointerException | `android-debugging-fixes` |

### 6. 密码功能类请求

| 关键词 | 触发 Skill |
|--------|-----------|
| 密码分享, 分享功能 | `password-sharing-implementation` |
| 密码强度, 强度检测 | `password-strength-algorithms` |

### 7. 架构/MVVM类请求

| 关键词 | 触发 Skill | 文件模式 |
|--------|-----------|----------|
| MVVM, ViewModel, LiveData | `android-mvvm-pattern` | `viewmodel/**/*`, `model/**/*` |
| Repository, Repository模式 | `android-mvvm-pattern` | `repository/**/*` |

### 8. 安全架构升级类请求

| 关键词 | 触发 Skill | 文件模式 |
|--------|-----------|----------|
| 安全架构升级, SecureKeyStorage | `android-security-architecture-upgrade` | `security/**/*KeyManager*` |
| 迁移, 升级版本 | `android-security-architecture-upgrade` | `**/*Migration*` |

### 9. OpenSpec 类请求

| 关键词 | 触发 Skill |
|--------|-----------|
| 创建提案, 新功能提案 | `openspec:proposal` |
| 审查提案, 检查提案 | `openspec-review` |
| 完成审查, 验收 | `openspec-completion` |
| 应用提案, 实现提案 | `openspec:apply` |

### 10. 文档类请求

| 关键词 | 触发 Skill |
|--------|-----------|
| 写文档, 更新文档 | `documenting-code` |
| API文档, 用户指南 | `documenting-code` |

### 11. 重构类请求

| 关键词 | 触发 Skill |
|--------|-----------|
| 重构代码, 优化代码 | `refactoring-code` |
| 清理无用代码 | `refactoring-code` |

### 12. 功能设计类请求

| 关键词 | 触发 Skill |
|--------|-----------|
| 如何实现, 设计方案 | `feature-design-advisor` |

---

## 实现步骤

### Step 1: 分析用户请求

```python
# 伪代码
def analyze_request(user_input):
    # 提取关键词
    keywords = extract_keywords(user_input)

    # 检测错误模式
    error_patterns = detect_error_patterns(user_input)

    # 检测文件模式（如果有上下文）
    file_patterns = detect_file_patterns(user_input)

    return RequestAnalysis(
        keywords=keywords,
        error_patterns=error_patterns,
        file_patterns=file_patterns
    )
```

### Step 2: 查找匹配的 skills

```python
# 伪代码
def find_matching_skills(analysis):
    matches = []

    # 遍历所有 skills
    for skill in available_skills:
        metadata = load_skill_metadata(skill)

        # 检查触发关键词
        if any(kw in metadata.triggers for kw in analysis.keywords):
            matches.append((skill, metadata.priority))

        # 检查错误模式
        if any(ep in metadata.error_patterns for ep in analysis.error_patterns):
            matches.append((skill, metadata.priority))

        # 检查文件模式
        if any(fp in metadata.file_patterns for fp in analysis.file_patterns):
            matches.append((skill, metadata.priority))

    # 按优先级排序
    matches.sort(key=lambda x: x[1], reverse=True)

    return [skill for skill, _ in matches]
```

### Step 3: 调用匹配的 skill

```python
# 伪代码
def dispatch_user_request(user_input):
    # 分析请求
    analysis = analyze_request(user_input)

    # 查找匹配的 skills
    matching_skills = find_matching_skills(analysis)

    # 处理匹配结果
    if len(matching_skills) == 0:
        # 无匹配，使用通用处理
        handle_generic_request(user_input)
    elif len(matching_skills) == 1:
        # 单个匹配，直接调用
        invoke_skill(matching_skills[0], user_input)
    else:
        # 多个匹配，询问用户
        ask_user_to_choose(matching_skills, user_input)
```

---

## 示例场景

### 示例 1: UI 更新请求

```
用户: 帮我把登录界面改好看点，用 Material Design 3

分析:
  - 关键词: [登录界面, 改, 好看, Material Design 3]
  - 文件模式: [ui/login/**, res/layout/login_*.xml]

匹配:
  ✅ android-ui-modernization (priority: high)

动作: 调用 android-ui-modernization skill
```

### 示例 2: 加密错误

```
用户: 登录时出现 BadPaddingException，解密失败了

分析:
  - 关键词: [登录, 解密, 失败]
  - 错误模式: [BadPaddingException]

匹配:
  ✅ android-encryption-fixes (priority: high)
  ⚠️  android-debugging-fixes (priority: medium)

动作: 优先调用 android-encryption-fixes
```

### 示例 3: OpenSpec 提案审查

```
用户: 审查一下 refactor-crypto-algorithms 提案

分析:
  - 关键词: [审查, 提案]

匹配:
  ✅ openspec-review (priority: high)

动作: 调用 openspec-review skill
```

### 示例 4: 功能实现咨询

```
用户: 如何实现密码分享功能，有哪些方案？

分析:
  - 关键词: [如何实现, 密码分享, 方案]

匹配:
  ✅ feature-design-advisor (priority: medium)
  ⚠️  password-sharing-implementation (priority: low)

动作: 询问用户是设计还是实现
```

---

## 多匹配处理策略

当检测到多个匹配的 skill 时：

1. **高优先级优先**: 选择优先级最高的 skill
2. **询问用户**: 使用 AskUserQuestion 让用户选择
3. **智能推荐**: 根据上下文推荐最合适的

---

## 无匹配处理

当没有匹配的 skill 时：

1. **通用处理**: 使用标准流程处理请求
2. **建议技能**: 推荐可能相关的 skills
3. **记录日志**: 记录未匹配的请求用于改进

---

## 相关技能

所有 `.claude/skills/` 下的技能

---

## 更新日志

- **v1.0.0** (2026-03-03): 初始版本
  - 智能请求分析
  - 关键词/错误模式/文件模式匹配
  - 多匹配处理