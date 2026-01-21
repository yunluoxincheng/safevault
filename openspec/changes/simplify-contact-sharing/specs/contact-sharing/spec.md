# 联系人分享功能规范

## ADDED Requirements

### Requirement: 用户只能分享密码给已添加的好友

系统 MUST 确保用户只能将密码分享给已经建立好友关系的联系人。

#### Scenario: 创建联系人分享

**Given** 用户 A 已登录
**And** 用户 B 是用户 A 的好友
**When** 用户 A 选择用户 B 并创建密码分享
**Then** 分享创建成功
**And** 分享状态为 PENDING
**And** 用户 B 收到分享通知

#### Scenario: 尝试分享给非好友用户

**Given** 用户 A 已登录
**And** 用户 C 不是用户 A 的好友
**When** 用户 A 尝试向用户 C 创建密码分享
**Then** 系统返回错误 "只能分享给好友"
**And** 分享未创建

#### Scenario: 好友关系解除后无法创建新分享

**Given** 用户 A 和用户 B 曾经是好友
**And** 好友关系已被解除
**When** 用户 A 尝试向用户 B 创建密码分享
**Then** 系统返回错误 "只能分享给好友"

---

### Requirement: 分享支持权限控制

系统 MUST 允许发送者设置接收者对分享密码的权限。

#### Scenario: 创建仅可查看的分享

**Given** 用户 A 向用户 B 分享密码
**When** 用户 A 设置权限为 can_view=true, can_save=false
**Then** 用户 B 可以查看密码内容
**But** 用户 B 无法保存到自己的密码库

#### Scenario: 创建不可撤销的分享

**Given** 用户 A 向用户 B 分享密码
**When** 用户 A 设置 is_revocable=false
**Then** 用户 A 无法撤销此分享

---

### Requirement: 分享支持过期时间控制

系统 MUST 支持发送者设置分享的过期时间。

#### Scenario: 创建有过期时间的分享

**Given** 用户 A 向用户 B 分享密码
**When** 用户 A 设置过期时间为 1 小时后
**And** 当前时间超过过期时间
**Then** 分享状态自动变为 EXPIRED
**And** 用户 B 无法访问此分享

#### Scenario: 创建永不过期的分享

**Given** 用户 A 向用户 B 分享密码
**When** 用户 A 设置永不过期
**Then** 分享不会自动过期
**And** 只要未撤销，用户 B 始终可以访问

---

### Requirement: 接收者可以接受或忽略分享

系统 MUST 允许接收者选择接受或忽略收到的分享。

#### Scenario: 接收者接受分享

**Given** 用户 B 收到来自用户 A 的分享
**And** 分享状态为 PENDING
**When** 用户 B 接受分享
**Then** 分享状态变为 ACCEPTED
**And** 用户 B 可以查看和使用密码
**And** 用户 A 收到接受通知

#### Scenario: 接收者忽略分享

**Given** 用户 B 收到来自用户 A 的分享
**And** 分享状态为 PENDING
**When** 用户 B 不做任何操作
**Then** 分享状态保持 PENDING
**And** 分享仍然有效直到过期或撤销

---

### Requirement: 发送者可以撤销可撤销的分享

系统 MUST 允许发送者撤销标记为可撤销的分享。

#### Scenario: 撤销可撤销的分享

**Given** 用户 A 向用户 B 分享了密码
**And** 分享的 is_revocable=true
**And** 分享状态为 ACCEPTED
**When** 用户 A 撤销分享
**Then** 分享状态变为 REVOKED
**And** 用户 B 无法再访问此分享
**And** 用户 B 收到撤销通知

#### Scenario: 尝试撤销不可撤销的分享

**Given** 用户 A 向用户 B 分享了密码
**And** 分享的 is_revocable=false
**When** 用户 A 尝试撤销分享
**Then** 系统返回错误 "此分享不可撤销"
**And** 分享状态保持不变

---

### Requirement: 系统实时通知分享状态变化

系统 MUST 使用 WebSocket 向相关用户推送分享状态变化的通知。

#### Scenario: 新分享创建时通知接收者

**Given** 用户 A 向用户 B 创建新分享
**When** 分享创建成功
**Then** 系统 WebSocket 向用户 B 推送通知
**And** 通知包含分享 ID、发送者信息和密码标题

#### Scenario: 分享被接受时通知发送者

**Given** 用户 B 接受来自用户 A 的分享
**When** 接受操作完成
**Then** 系统 WebSocket 向用户 A 推送通知
**And** 通知包含分享 ID 和接受者信息

#### Scenario: 分享被撤销时通知接收者

**Given** 用户 A 撤销给用户 B 的分享
**When** 撤销操作完成
**Then** 系统 WebSocket 向用户 B 推送通知
**And** 通知包含分享 ID 和撤销原因

---

### Requirement: 用户可以查看分享历史

系统 MUST 允许用户查看自己创建的分享和接收的分享。

#### Scenario: 查看创建的分享列表

**Given** 用户 A 已登录
**When** 用户 A 请求查看创建的分享
**Then** 系统返回用户 A 创建的所有分享
**And** 每个分享包含接收者、状态、创建时间、过期时间

#### Scenario: 查看接收的分享列表

**Given** 用户 B 已登录
**When** 用户 B 请求查看接收的分享
**Then** 系统返回用户 B 接收的所有分享
**And** 每个分享包含发送者、密码数据、权限、状态

---

## REMOVED Requirements

### Requirement: 直接链接分享功能（已移除）

**移除原因**：安全风险，与产品定位不符

#### Scenario: 生成分享链接（已移除）

用户可以生成分享链接，任何人通过链接都可以访问分享的密码。

#### Scenario: 二维码分享（已移除）

用户可以生成二维码，其他用户扫描后直接访问分享内容。

---

### Requirement: 附近用户发现功能（已移除）

**移除原因**：需要位置权限，使用频率低

#### Scenario: 注册用户位置（已移除）

用户可以注册自己的当前位置，用于附近用户发现。

#### Scenario: 查找附近用户（已移除）

用户可以查找指定半径内的其他 SafeVault 用户。

#### Scenario: 向附近用户分享（已移除）

用户可以向附近发现的用户直接分享密码。
