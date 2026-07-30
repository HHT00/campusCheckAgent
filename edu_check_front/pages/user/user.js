/**
 * 用户中心页面 - 学生/教师双角色
 */
import api from '../../api/index';

const app = getApp();

Page({
  data: {
    userInfo: {},
    stats: {},
    role: 'student',
    profileStats: [
      { label: '今日打卡', value: '1' },
      { label: '连续天数', value: '7天' },
      { label: '总积分', value: '268' },
      { label: '思政学时', value: '32' }
    ],
    archiveData: [
      { label: '课程学习', value: '96%', color: '#E74C3C' },
      { label: '实践活动', value: '12次', color: '#3498DB' },
      { label: '志愿服务', value: '28h', color: '#00B894' },
      { label: '综合评级', value: 'A', color: '#F39C12' }
    ],
    faceRegistered: false,
    loading: true,
    // 教师端未处理请假数
    pendingLeaveCount: 0,
    connected: true
  },

  async onLoad() {
    await this.loadUserData();
    await this.loadFaceStatus();
  },

  async onShow() {
    await Promise.all([
      this.loadUserData(),
      this.loadFaceStatus(),
      this.checkRole()
    ]);
  },

  /** 获取当前角色 */
  checkRole() {
    const role = app.globalData.role || wx.getStorageSync('userRole') || 'student';
    this.setData({
      role,
      connected: app.globalData.connected
    });
    if (role === 'teacher') {
      this.loadTeacherData();
    }
  },

  /** 教师端加载特有数据 */
  async loadTeacherData() {
    try {
      const stats = await api.getTeacherLeaveStats();
      this.setData({ pendingLeaveCount: stats?.pending || 0 });
    } catch (err) {
      console.warn('[用户中心] 加载教师数据失败:', err);
    }
  },

  /** 从后端加载用户数据和统计 */
  async loadUserData() {
    this.setData({ loading: true });

    try {
      // 获取用户信息
      const profile = await api.getUserProfile();
      if (profile) {
        this.setData({
          userInfo: {
            name: profile.name,
            studentId: profile.studentId,
            college: profile.college,
            major: profile.major,
            avatar: profile.avatar,
            role: profile.role
          },
          role: profile.role || app.globalData.role || 'student'
        });
      }

      // 获取打卡统计
      const overview = await api.getDashboardOverview();
      if (overview?.stats) {
        const updatedStats = [...this.data.profileStats];
        updatedStats[1] = { ...updatedStats[1], value: (overview.stats.streakDays || 0) + '天' };
        this.setData({ profileStats: updatedStats });
      }
    } catch (err) {
      console.warn('[用户中心] 加载数据失败:', err);
      this.setData({
        userInfo: app.globalData.userInfo || {},
        role: app.globalData.role || 'student'
      });
    } finally {
      this.setData({ loading: false });
    }
  },

  /** 加载人脸录入状态 */
  async loadFaceStatus() {
    try {
      const status = await api.getFaceStatus();
      this.setData({ faceRegistered: status.registered || false });
    } catch (err) {
      console.warn('[用户中心] 加载人脸状态失败:', err);
    }
  },

  // 编辑资料
  editProfile() {
    wx.navigateTo({ url: '/pages/user/edit-profile/edit-profile' });
  },

  viewAllArchive() {
    app.showToast('思政档案详情功能开发中');
  },

  /** 跳转人脸录入 */
  gotoFace() {
    wx.navigateTo({ url: '/pages/face/face' });
  },

  /** 跳转课程表 */
  gotoCourseSchedule() {
    wx.navigateTo({ url: '/pages/schedule/schedule' });
  },

  gotoMessage() {
    app.showToast('消息通知功能开发中');
  },

  gotoFeedback() {
    wx.navigateTo({ url: '/pages/feedback/feedback' });
  },

  gotoHelp() {
    wx.navigateTo({ url: '/pages/qa/qa' });
  },

  gotoUnanswered() {
    wx.navigateTo({ url: '/pages/teacher/unanswered/unanswered' });
  },

  gotoAbout() {
    app.showToast('关于页面开发中');
  },

  gotoSettings() {
    app.showToast('设置功能开发中');
  },

  // ==================== 教师端功能 ====================

  /** 跳转教师控制台 */
  gotoTeacherPanel() {
    wx.navigateTo({ url: '/pages/teacher/teacher' });
  },

  /** 跳转学生管理 */
  gotoStudents() {
    wx.navigateTo({ url: '/pages/teacher/students' });
  },

  /** 跳转请假审批 */
  gotoLeaveApprove() {
    wx.navigateTo({ url: '/pages/teacher/leave-approve' });
  },

  /** 跳转公告发布 */
  gotoAnnouncePublish() {
    wx.navigateTo({ url: '/pages/teacher/announce-publish' });
  },

  /** 跳转动态码管理 */
  gotoDynamicCode() {
    wx.navigateTo({ url: '/pages/teacher/dynamic-code' });
  },

  /** 切换账号（开发调试用） */
  switchAccount() {
    const self = this;
    wx.showActionSheet({
      itemList: ['切换到学生账号', '切换到教师账号'],
      success(res) {
        if (res.tapIndex === 0) {
          app.switchToStudent().then(() => {
            app.showToast('已切换为学生');
            self.loadUserData();
            self.checkRole();
          });
        } else {
          app.switchToTeacher().then(() => {
            app.showToast('已切换为教师');
            self.loadUserData();
            self.checkRole();
          });
        }
      }
    });
  }
});
