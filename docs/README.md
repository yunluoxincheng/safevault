
# SafeVault 文档

欢迎来到 SafeVault 项目文档中心。

## 📁 文档结构

```
docs/
├── README.md                    # 本文件
├── USER-DOCS-INDEX.md          # 用户文档索引（从这开始！）
├── security-architecture.md    # 安全存储架构文档
│
├── user-guide/                 # 用户指南
│   └── password-sharing.md     # 密码分享功能用户指南
│
├── faq/                        # 常见问题
│   └── sharing-faq.md          # 分享功能 FAQ
│
├── changelog/                  # 更新日志
│   └── v2.1.0-e2e-sharing.md   # v2.1.0 版本更新日志
│
├── api/                        # API 文档
│   ├── authentication.md       # 认证 API
│   ├── data-sync.md            # 数据同步 API
│   ├── device-management.md    # 设备管理 API
│   ├── contact-sharing.md      # 联系人分享 API
│   ├── database-schema.md      # 数据库 Schema
│   └── migration-guide-v2.2.md # v2.2 迁移指南
│
└── plans/                      # 设计文档
    ├── 2026-01-19-e2e-password-sharing-design.md
    ├── 2026-01-19-e2e-implementation-summary.md
    └── ...
```

---

## 🚀 快速开始

### 我是用户
如果你是 SafeVault 的用户，想了解如何使用密码分享功能：

👉 **[从这里开始：用户文档索引](USER-DOCS-INDEX.md)**

推荐阅读顺序：
1. [用户指南](user-guide/password-sharing.md) - 了解基本功能和使用方法
2. [FAQ 文档](faq/sharing-faq.md) - 遇到问题时查阅
3. [更新日志](changelog/v2.1.0-e2e-sharing.md) - 了解最新功能

### 我是开发者
如果你是开发者，想了解技术实现：

推荐阅读顺序：
1. [设计文档](plans/2026-01-19-e2e-password-sharing-design.md) - 了解架构设计
2. [实现总结](plans/2026-01-19-e2e-implementation-summary.md) - 查看实现细节
3. [API 文档](api/) - 查看 API 接口

---

## 📖 文档分类

### 用户文档
面向最终用户的使用指南和帮助文档。

- **[用户文档索引](USER-DOCS-INDEX.md)** - 用户文档总目录
- **[密码分享用户指南](user-guide/password-sharing.md)** - 完整的功能使用指南
- **[分享功能 FAQ](faq/sharing-faq.md)** - 54+ 个常见问题解答
- **[v2.1.0 更新日志](changelog/v2.1.0-e2e-sharing.md)** - 最新版本详情

### API 文档
面向开发者的 API 接口文档。

- **[认证 API](api/authentication.md)** - 用户认证和授权
- **[数据同步 API](api/data-sync.md)** - 密码数据同步
- **[设备管理 API](api/device-management.md)** - 设备注册和管理

### 设计文档
面向开发者的架构设计和实现文档。

- **[安全存储架构](security-architecture.md)** - 五层安全架构详解
- **[端到端分享设计](plans/2026-01-19-e2e-password-sharing-design.md)** - E2E 分享架构设计
- **[实现总结](plans/2026-01-19-e2e-implementation-summary.md)** - 实现细节总结

---

## 🎯 按主题查找

### 密码分享功能
- 用户指南: [user-guide/password-sharing.md](user-guide/password-sharing.md)
- FAQ: [faq/sharing-faq.md](faq/sharing-faq.md)
- 更新日志: [changelog/v2.1.0-e2e-sharing.md](changelog/v2.1.0-e2e-sharing.md)
- 设计文档: [plans/2026-01-19-e2e-password-sharing-design.md](plans/2026-01-19-e2e-password-sharing-design.md)

### 安全与加密
- **安全架构总览**: [security-architecture.md](security-architecture.md) - 完整的五层安全架构
- 端到端加密原理: [user-guide/password-sharing.md#技术细节](user-guide/password-sharing.md#技术细节)
- 加密算法详解: [faq/sharing-faq.md#技术问题](faq/sharing-faq.md#技术问题)
- 密钥管理: [plans/2026-01-19-e2e-password-sharing-design.md#密钥管理](plans/2026-01-19-e2e-password-sharing-design.md#密钥管理)

### API 接口
- RESTful API: [api/](api/)
- WebSocket: [api/data-sync.md](api/data-sync.md)
- 认证流程: [api/authentication.md](api/authentication.md)

---

## 🔍 搜索技巧

### 查找特定问题
1. 先查看 [FAQ 文档](faq/sharing-faq.md)
2. 使用浏览器搜索功能（Ctrl+F）搜索关键词
3. 如果找不到，查看对应的设计文档

### 查找技术细节
1. 查看 [设计文档](plans/)
2. 查看 [实现总结](plans/2026-01-19-e2e-implementation-summary.md)
3. 查看 [更新日志中的技术改进](changelog/v2.1.0-e2e-sharing.md#技术改进)

### 查找使用方法
1. 查看 [用户指南](user-guide/password-sharing.md)
2. 参考"使用方法"章节
3. 查看代码示例（如有）

---

## 📊 文档统计

| 分类 | 文档数 | 总字数 |
|------|--------|--------|
| 用户文档 | 3 | ~10,500 |
| API 文档 | 6 | ~12,000 |
| 设计文档 | 10+ | ~30,000 |
| 安全架构 | 1 | ~4,000 |
| **总计** | **20+** | **~56,500+** |

---

## 🤝 贡献文档

### 如何改进文档
1. Fork 项目仓库
2. 修改文档
3. 提交 Pull Request
4. 说明改进原因

### 文档规范
- 使用 Markdown 格式
- 遵循现有文档结构
- 添加示例和图示
- 保持简洁清晰

### 文档模板
- 用户指南模板: `docs/templates/user-guide-template.md`
- API 文档模板: `docs/templates/api-doc-template.md`
- 设计文档模板: `docs/templates/design-doc-template.md`

---

## 📞 获取帮助

### 文档相关
- 文档问题：提交 GitHub Issue
- 文档改进：提交 Pull Request
- 内容错误：联系文档维护者

### 技术支持
- **技术支持**: support@safevault.app
- **GitHub Issues**: https://github.com/your-repo/safevault/issues
- **Discussions**: https://github.com/your-repo/safevault/discussions

---

## 🔗 外部资源

### 官方资源
- **SafeVault 主页**: (即将推出)
- **GitHub 仓库**: https://github.com/your-repo/safevault
- **下载页面**: (即将推出)

### 技术参考
- **Android 开发者文档**: https://developer.android.com/
- **Material Design**: https://m3.material.io/
- **MVVM 架构**: https://developer.android.com/topic/architecture

### 安全参考
- **OWASP 密码存储**: https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
- **NIST 密码指南**: https://pages.nist.gov/800-63-3/
- **加密最佳实践**: https://github.com/owsasp/aws-security-development-practice

---

## 📅 更新记录

### 2026-02-08
- 新增安全存储架构文档
- 更新文档目录结构和统计信息

### 2026-01-19
- 创建用户文档结构
- 添加密码分享功能用户指南
- 添加 54+ 个 FAQ 问题
- 添加 v2.1.0 更新日志
- 创建用户文档索引

### 2026-01-18
- 添加 E2E 分享设计文档
- 添加实现总结文档

### 2025-12-20
- 添加 API 文档结构
- 添加认证 API 文档
- 添加数据同步 API 文档

---

## 📄 许可证

本文档遵循 MIT 许可证。

---

**文档版本**: 1.1
**最后更新**: 2026-02-08
**维护者**: SafeVault 文档团队

---

## 快速链接

- [安全存储架构](security-architecture.md) - 五层安全架构详解
- [用户文档索引](USER-DOCS-INDEX.md) - 用户从这里开始
- [密码分享用户指南](user-guide/password-sharing.md) - 功能使用指南
- [分享功能 FAQ](faq/sharing-faq.md) - 常见问题
- [v2.1.0 更新日志](changelog/v2.1.0-e2e-sharing.md) - 版本详情
- [GitHub 仓库](https://github.com/your-repo/safevault) - 源代码
