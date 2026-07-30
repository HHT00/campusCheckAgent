/**
 * 首页 - Feishu 风格数据看板
 */
import api from '../../api/index';

const app = getApp();

Page({
  data: {
    userInfo: {},
    stats: {
      // 学生端
      dormTotal: 15, classTotal: 22, internTotal: 5, streakDays: 3,
      todayDorm: false, todayClass: false, todayIntern: false,
      // 教师端
      totalStudents: 128, classChecked: 96, classNotChecked: 32,
      dormChecked: 112, dormNotChecked: 16, pendingLeave: 3
    },
    role: 'student',
    loading: true
  },

  onLoad() {
    this.syncRole();
  },

  onShow() {
    this.syncRole();
    this.loadDashboard();
  },

  syncRole() {
    const role = app.globalData.role || wx.getStorageSync('userRole') || 'student';
    this.setData({ role });
  },

  async loadDashboard() {
    this.setData({ loading: true });
    try {
      // 用户信息
      const profile = await api.getUserProfile();
      const userInfo = {
        name: profile?.name || app.globalData.userInfo?.name || '',
        college: profile?.college || app.globalData.userInfo?.college || '',
        avatar: profile?.avatar || '',
        studentId: profile?.studentId || ''
      };

      // 统计数据
      const overview = await api.getDashboardOverview();
      const statsData = overview?.stats || {};

      // 今日打卡状态
      let todayDorm = false, todayClass = false, todayIntern = false;
      try {
        const dormStatus = await api.getDormTodayStatus();
        todayDorm = !!dormStatus;
      } catch (_) {}

      // 如果是教师，额外加载教师统计数据
      let teacherStats = {};
      if (this.data.role === 'teacher') {
        try { const s = await api.getTodayCheckinSummary(); if (s) teacherStats = s; } catch (_) {}
      }

      this.setData({
        userInfo,
        stats: {
          dormTotal: statsData.dormTotal || 0,
          classTotal: statsData.classTotal || 0,
          internTotal: statsData.internTotal || 0,
          streakDays: statsData.streakDays || 0,
          todayDorm,
          todayClass,
          todayIntern,
          totalStudents: teacherStats.totalStudents || 128,
          classChecked: teacherStats.classChecked || 96,
          classNotChecked: teacherStats.classNotChecked || 32,
          dormChecked: teacherStats.dormChecked || 112,
          dormNotChecked: teacherStats.dormNotChecked || 16,
          pendingLeave: teacherStats.pendingLeave || 3
        },
        loading: false
      });
    } catch (err) {
      console.warn('[首页] 加载失败:', err);
      // mock 数据兜底
      const mockStats = app.globalData.checkinStats || { dormTotal: 15, classTotal: 22, internTotal: 5, streakDays: 3 };
      this.setData({
        userInfo: app.globalData.userInfo || {
          name: '张同学',
          college: '计算机科学与技术学院',
          avatar: ''
        },
        stats: {
          dormTotal: mockStats.dormTotal || 0,
          classTotal: mockStats.classTotal || 0,
          internTotal: mockStats.internTotal || 0,
          streakDays: mockStats.streakDays || 0,
          todayDorm: false,
          todayClass: false,
          todayIntern: false,
          totalStudents: 128, classChecked: 96, classNotChecked: 32,
          dormChecked: 112, dormNotChecked: 16, pendingLeave: 3
        },
        loading: false
      });
    }
  },

  // ====== 导航 ======
  goProfile() { wx.navigateTo({ url: '/pages/user/user' }); },
  goDormCheckin() { wx.navigateTo({ url: '/pages/dorm/dorm' }); },
  goClassCheckin() { wx.navigateTo({ url: '/pages/course/course' }); },
  goInternCheckin() { wx.navigateTo({ url: '/pages/intern/intern' }); },
  goDormHistory() { wx.navigateTo({ url: '/pages/dorm-history/dorm-history' }); },
  goClassHistory() { wx.navigateTo({ url: '/pages/course-history/course-history' }); },
  goInternHistory() { wx.navigateTo({ url: '/pages/intern-history/intern-history' }); },
  goFace() { wx.navigateTo({ url: '/pages/face/face' }); },
  goTeacherPanel() { wx.navigateTo({ url: '/pages/teacher/teacher' }); },
  goAlertCenter() { wx.navigateTo({ url: '/pages/teacher/alert/alert' }); },
  goKbAdd() { wx.navigateTo({ url: '/pages/teacher/kb-add/kb-add' }); },
  goStudents(e) {
    const filter = e.currentTarget.dataset.filter || '';
    wx.navigateTo({ url: '/pages/teacher/students?filter=' + filter });
  },
  goLeaveApprove() { wx.navigateTo({ url: '/pages/teacher/leave-approve' }); },
  goDynamicCode() { wx.navigateTo({ url: '/pages/teacher/dynamic-code' }); },
  goAnnouncePublish() { wx.navigateTo({ url: '/pages/teacher/announce-publish' }); }
});
