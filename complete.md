# 后端未开发接口清单

## 一、公告管理 — 完全缺失（7个接口）

> 需要新建 Announcement 实体、controller、service、mapper

| 接口 | 方法 | 说明 | 前端页面 |
|------|------|------|----------|
| `/api/announcements` | GET | 公告列表（分页+分类筛选+关键字搜索） | announcement/announcement.js |
| `/api/announcements/top` | GET | 获取置顶公告 | announcement/announcement.js |
| `/api/announcements/{id}` | GET | 获取公告详情 | announcement/detail/detail.js |
| `/api/announcements` | POST | 发布公告（教师） | teacher/announce-publish.js |
| `/api/announcements/{id}` | PUT | 更新公告（教师） | teacher/announce-publish.js |
| `/api/announcements/{id}` | DELETE | 删除公告（教师） | teacher/announce-publish.js |
| `/api/announcements/teacher/list-all` | GET | 教师获取全部公告列表 | teacher/announce-publish.js |

**状态：** 后端完全无对应代码，前端 3 个页面依赖此模块。

---

## 二、请假管理 — 完全缺失（7个接口）

> 需要新建 Leave 实体（含 type/status/dates/reason/rejectReason 等字段）、controller、service、mapper

| 接口 | 方法 | 说明 | 前端页面 |
|------|------|------|----------|
| `/api/leaves` | GET | 获取请假列表（分页+状态筛选） | leave/leave.js |
| `/api/leaves/stats` | GET | 获取请假统计（total/pending/approved/rejected） | leave/leave.js |
| `/api/leaves` | POST | 申请请假 | leave/apply/apply.js |
| `/api/leaves/{id}` | GET | 获取请假详情 | leave/detail/detail.js |
| `/api/leaves/teacher/pending` | GET | 教师端待审批列表 | teacher/leave-approve.js |
| `/api/leaves/teacher/all` | GET | 教师端全部请假（分页+状态筛选） | teacher/leave-approve.js |
| `/api/leaves/teacher/stats` | GET | 教师端请假统计 | teacher/leave-approve.js, user/user.js |
| `/api/leaves/teacher/approve/{id}` | POST | 教师审批（批准/驳回+驳回原因） | teacher/leave-approve.js |

**状态：** 后端完全无对应代码，前端 4 个页面依赖此模块。

---

## 三、上课签到 — 部分缺失（2个接口）

> CourseCheckinController 已有 today 和 history 查询，缺少提交签到和课表查询

| 接口 | 方法 | 说明 | 前端页面 |
|------|------|------|----------|
| `POST /api/course/checkin` | POST | **上课签到提交** — 已有 DTO(`ClassCheckinRequest`)，但 Controller 中无此接口实现 | course/course.js |
| `GET /api/course/schedule` | GET | **获取完整课程表**（整周） — 前端 teacher/dynamic-code.js 和 schedule/schedule.js 均需调用 | schedule/schedule.js, teacher/dynamic-code.js |

**注意：** ClassCheckinRequest DTO 已经定义好（含 courseId, method, locationLat/Lng, locationAddr, dynamicCode, sessionId, faceVerified），但 CourseCheckinController 中完全没有 checkin 提交逻辑。签到提交是上课打卡的核心功能。

---

## 四、实习打卡 — 完全缺失（6个接口）

> 需要新建 Internship、InternCheckin、InternPhoto 等实体及全套 controller/service/mapper

| 接口 | 方法 | 说明 | 前端页面 |
|------|------|------|----------|
| `/api/intern/my` | GET | 获取我的实习信息（公司、岗位、进度） | intern/intern.js |
| `/api/intern/stats` | GET | 实习统计（company/role/progress/completedDays/totalDays） | intern/intern.js |
| `/api/intern/checkin` | POST | 实习打卡提交（含日志内容、定位、人脸） | intern/intern.js |
| `/api/intern/history` | GET | 实习打卡历史（分页） | intern/intern.js, intern-history/intern-history.js |
| `/api/intern/photo/upload` | POST | 上传实习照片（checkinId + imageUrl） | intern/intern.js |
| `/api/intern/photo/{id}` | DELETE | 删除实习照片 | intern/intern.js |

**状态：** 后端完全无对应代码，前端 2 个页面依赖此模块。实习打卡是三大核心打卡功能之一。

---

## 五、教师端 — 部分缺失（5个接口）

| 接口 | 方法 | 说明 | 前端页面 |
|------|------|------|----------|
| `/api/teacher/dynamic-code/generate` | POST | 生成动态码（含 courseId + duration，返回 dynamicCode + sessionId） | teacher/dynamic-code.js |
| `/api/teacher/dynamic-code/active` | GET | 获取当前课程活跃的动态码 | teacher/dynamic-code.js |
| `/api/teacher/students` | GET | 获取学生列表 | teacher/students.js（备用） |
| `/api/teacher/students/page` | GET | 分页获取学生列表（含 keyword 搜索 + 今日签到/查寝状态） | teacher/students.js |
| `/api/teacher/students/checkin/{studentId}` | GET | 获取学生打卡详情（学生信息+统计+今日课程签到+今日查寝+历史记录） | teacher/student-detail.js |
| `/api/teacher/checkin/today-summary` | GET | 今日签到总结（总人数/已签到/未签到/已查寝/未查寝） | teacher/teacher.js |

**注意：** 后端有 Course 实体和 CourseService，可以支持动态码和课程管理；学生管理需要关联 User 实体查询。这些接口直接影响教师端核心功能。

---

## 六、意见反馈 — 完全缺失（3个接口）

> 需要新建 Feedback 实体（含 type/content/contact/userId）、controller/service/mapper

| 接口 | 方法 | 说明 | 前端页面 |
|------|------|------|----------|
| `/api/feedback` | POST | 提交反馈 | feedback/feedback.js |
| `/api/feedback` | GET | 获取当前用户的反馈列表（分页） | feedback/feedback.js |
| `/api/feedback/teacher` | GET | 教师获取全部反馈列表 | feedback/feedback.js |

**状态：** 后端完全无对应代码，前端 1 个页面依赖此模块。

---

## 七、智能问答 — 部分缺失（1个接口）

| 接口 | 方法 | 说明 | 前端页面 |
|------|------|------|----------|
| `/api/knowledge/unanswered` | GET | 获取未回答的问题日志列表（教师端审核用） | teacher/unanswered/unanswered.js |

**注意：** 后端已有 UnansweredLog 实体和 Service，但 Controller 中没有暴露该查询接口。

---

## 八、仪表盘 — 数据硬编码待修复（1个接口）

| 接口 | 方法 | 问题 | 说明 |
|------|------|------|------|
| `/api/dashboard/overview` | GET | 统计数据硬编码 | stats 中的 dormTotal/classTotal/internTotal/streakDays 全部写死为 6，需要从 checkin_stats 等表真实查询 |

---

## 汇总统计

| 类别 | 缺失数量 | 优先级 |
|------|---------|--------|
| 公告管理 | 7 | 🔴 P0 |
| 请假管理 | 8 | 🔴 P0 |
| 实习打卡 | 6 | 🔴 P0 |
| 上课签到（缺失部分） | 2 | 🟠 P1 |
| 教师端（缺失部分） | 6 | 🟠 P1 |
| 意见反馈 | 3 | 🟡 P2 |
| 智能问答（缺失部分） | 1 | 🟡 P2 |
| 仪表盘（硬编码修复） | 1 | 🟡 P2 |
| **总计缺失** | **约 34 个接口** | |

其中 P0 级接口（10 个以上页面依赖，功能无法正常使用）共约 21 个。
