# 校园考勤助手 小程序项目文档

## 项目概述
"AI赋能教育"理念下的大学生考勤管理微信小程序，集查寝打卡、上课打卡、实习打卡三大功能于一体。

## 技术栈
- 微信小程序原生开发
- 语言：JavaScript + WXML + WXSS
- 工具：微信开发者工具

## 项目结构
```
├── app.json / app.js / app.wxss      # 全局配置/逻辑/样式
├── project.config.json                # 项目配置
├── images/                            # 图标资源
│   ├── tab_home.png / tab_home_active.png
│   ├── tab_user.png / tab_user_active.png
│   ├── default_avatar.png / default_photo.png
└── pages/
    ├── index/         # 首页 - 三大功能入口 + 统计 + 思政语录
    ├── announcement/  # 公告 - 通知列表 + 分类筛选
    ├── leave/         # 请假 - 申请 + 审批记录
    ├── dorm/          # 查寝打卡 - 定位+人脸识别
    ├── course/        # 上课打卡 - 限时定位/动态码
    ├── intern/        # 实习打卡 - 定位+日志上报
    └── user/          # 用户中心 - 个人档案+统计
```

## 设计规范
- 主题色：#E74C3C（思政红）
- 辅助色：#2C3E50（深蓝灰）、#3498DB（信息蓝）、#00B894（成功绿）
- 背景色：#F5F7FA
- 字体：PingFang SC / Microsoft YaHei

## 页面路由
- 首页 → `pages/index/index`（Tab首页）
- 公告 → `pages/announcement/announcement`（Tab公告）
- 请假 → `pages/leave/leave`（Tab请假）
- 查寝 → `pages/dorm/dorm`
- 上课 → `pages/course/course`
- 实习 → `pages/intern/intern`
- 我的 → `pages/user/user`（Tab个人中心）

## Tab栏（4项）
首页 | 公告 | 请假 | 我的

## 开发约定
- 全局数据通过 `getApp().globalData` 共享
- check-in 相关变量命名使用 `checkin` 缩写
- 所有页面使用 `page-container` 作为根容器类名
- 全局通用组件样式定义在 app.wxss 中（.btn-primary, .card, .tag等）
- **所有数据操作必须通过后端API完成，禁止在前端使用硬编码mock数据**
- **API请求统一走 `utils/request.js` 封装的HTTP工具，自动携带token**
- **接口地址统一在 `api/index.js` 中定义，页面JS引用api模块发送请求**
- **启动时 `app.js` 自动调用登录API获取token并刷新用户数据**
- **后端API地址基座: `http://localhost:8080/api`（开发环境）**

## API 数据流
```
页面JS → api/index.js（接口定义） → utils/request.js（HTTP请求+token）
    → 后端Controller → Service → Mapper → MySQL
    → 返回统一 Result 结构 { code, message, data }
    → 页面渲染数据、展示错误提示
```

## 记忆持久化规则（自动执行）
- 每次对话产生的核心变更（新功能、架构改动、用户决策、**偏好设定**）必须实时写入 memory/ 或本文件
- **对话中任何偏好设定、命名约定、技术决策一经确认，必须立即写入 memory/，不得积攒到对话结束**
- **每轮对话最后，自动检查并确保所有偏好和决策都已持久化保存**
- 旧记忆与现状不符时主动更新而非追加
- 参见 memory/feedback_auto_memory.md
