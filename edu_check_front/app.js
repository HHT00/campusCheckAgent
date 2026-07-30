/**
 * 校园考勤助手 - 全局应用逻辑
 * 启动时自动登录后端获取token，支持角色区分（学生/教师）
 */
import api from './api/index';
import request from './utils/request';



App({
  globalData: {
    userInfo: null,
    checkinStats: null,
    token: '',
    isLoggedIn: false,
    role: '',
    connected: false
  },

  // 小程序初始化完成时触发，全局只触发一次
  async onLaunch() {
    console.log("666，app.js，onLaunch触发")
    try {
      const savedToken = wx.getStorageSync('token');
      const savedRole = wx.getStorageSync('userRole');
      if (savedToken) {
        console.log("666，app.js，已经有登录信息")
        request.setToken(savedToken);
        this.globalData.token = savedToken;
        this.globalData.isLoggedIn = true;
        this.globalData.role = savedRole || '';
        this.globalData.connected = true;
        await this.refreshUserData();
      } else {
        console.log("666，app.js，没有登录信息，发送请求")
        await this.autoLogin('2024011001', '123456');
      }
    } catch (err) {
      console.warn('[App] 启动登录失败:', err.message || err);
      const savedRole = wx.getStorageSync('userRole') || 'student';
      this.ensureOfflineMode(savedRole);
    }
  },

  /** 确保离线模式可用 */
  ensureOfflineMode(role) {
    this.globalData.isLoggedIn = true;
    this.globalData.connected = false;
    this.globalData.role = role || 'student';

    const isTeacher = this.globalData.role === 'teacher';
    const cacheKey = isTeacher ? 'cachedTeacher' : 'cachedUser';
    const cached = wx.getStorageSync(cacheKey);

    if (cached) {
      this.globalData.userInfo = cached;
    } else {
      this.globalData.userInfo = {
        name: isTeacher ? '张老师' : '张学生',
        studentId: isTeacher ? 'T2024001' : '2024011001',
        college: '计算机科学与技术学院',
        major: isTeacher ? undefined : '软件工程',
        grade: isTeacher ? undefined : '2024级',
        avatar: ''
      };
      wx.setStorageSync(cacheKey, this.globalData.userInfo);
    }

    // 学生额外缓存统计
    if (!isTeacher) {
      const cachedStats = wx.getStorageSync('cachedStats');
      this.globalData.checkinStats = cachedStats || {
        dormTotal: 42, classTotal: 68, internTotal: 15, streakDays: 7
      };
    } else {
      this.globalData.checkinStats = {};
    }
  },

  /** 自动登录（开发环境使用默认账号） */
  async autoLogin(studentId = '2024011001', password = '123456') {
    try {
      const res = await api.login(studentId, password);
      request.setToken(res.token);
      this.globalData.token = res.token;
      this.globalData.isLoggedIn = true;
      this.globalData.connected = true;
      this.globalData.role = res.role || 'student';
      wx.setStorageSync('userRole', this.globalData.role);
      console.log("666，app.js，登录成功")
      await this.refreshUserData();
      return res;
    } catch (err) {
      console.error('[App] 自动登录失败:', err.message || err);
      const role = studentId === 'T2024001' ? 'teacher' : 'student';
      this.ensureOfflineMode(role);
      return null;
    }
  },

  /** 使用教师账号登录 */
  async loginAsTeacher(studentId = 'T2024001', password = '123456') {
    return this.autoLogin(studentId, password);
  },

  /** 刷新用户数据和统计 */
  async refreshUserData() {
    try {
      const overview = await api.getDashboardOverview();
      // const overview = {};
      this.globalData.userInfo = overview.userInfo || {};
      this.globalData.checkinStats = overview.stats || {};
    } catch (err) {
      console.warn('[App] 刷新数据失败:', err);
    }
  },

  /** 退出登录 */
  logout() {
    request.clearToken();
    wx.removeStorageSync('userRole');
    wx.removeStorageSync('cachedRole');
    this.globalData.token = '';
    this.globalData.isLoggedIn = false;
    this.globalData.userInfo = null;
    this.globalData.checkinStats = null;
    this.globalData.role = '';
  },

  /** 切换到教师账号 */
  async switchToTeacher() {
    try {
      return await this.loginAsTeacher();
    } catch (err) {
      this.ensureOfflineMode('teacher');
      return null;
    }
  },

  /** 切换到学生账号 */
  async switchToStudent() {
    try {
      return await this.autoLogin('2024011001', '123456');
    } catch (err) {
      this.ensureOfflineMode('student');
      return null;
    }
  },

  showToast(title, icon = 'none') {
    wx.showToast({ title, icon, duration: 2000 });
  },

  showLoading(title = '加载中...') {
    wx.showLoading({ title });
  },

  hideLoading() {
    wx.hideLoading();
  }
});
