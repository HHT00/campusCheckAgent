/**
 * 上课打卡页面 - 课前10分钟开放签到，自动判定迟到/旷课
 */
import api from '../../api/index';
import { getLocationWithAddress } from '../../utils/location';

const app = getApp();

Page({
  data: {
    isTeacher: false,
    courseList: [],

    // 选中的课程
    selectedCourse: null,

    // 定位
    locationChecked: false,
    locationAddr: '',
    locationLat: null,
    locationLng: null,

    // 人脸验证
    faceVerified: false,
    faceVerifying: false,
    facePhotoUrl: '',
    faceVerifyMsg: '',

    // 动态码
    dynamicCode: '',

    // 签到状态
    checkedIn: false,
    checkedInStatus: '',
    submitting: false,
    loading: true,

    history: []
  },

  async onLoad() {
    const role = app.globalData.role || wx.getStorageSync('userRole') || '';
    this.setData({ isTeacher: role === 'teacher' });
    if (role !== 'teacher') {
      await Promise.all([
        this.loadCourseInfo(),
        this.loadHistory()
      ]);
    }
  },

  async onReady() {
    const role = app.globalData.role || wx.getStorageSync('userRole') || '';
    this.setData({ isTeacher: role === 'teacher' });
    if (role !== 'teacher' && !this.data.loading) {
      await this.loadCourseInfo();
    }
  },

  /** 转换签到状态为显示文字 */
  statusText(status) {
    const map = {
      'present': '已签到',
      'late': '迟到',
      'absent': '缺勤',
      'waiting': '未开始',
      'ready': '可签到'
    };
    return map[status] || '未知';
  },

  /** 转换签到状态为标签样式类 */
  statusClass(status) {
    const map = {
      'present': 'tag-success',
      'late': 'tag-warning',
      'absent': 'tag-danger',
      'waiting': 'tag-primary',
      'ready': 'tag-success'
    };
    return map[status] || 'tag-primary';
  },

  /** 加载今日课程及签到状态 */
  async loadCourseInfo() {
    try {
      const courses = await api.getTodayCourse();
      let list = [];
      if (courses && courses.length > 0) {
        list = courses.map(c => ({
          ...c,
          statusText: this.statusText(c.checkinStatus),
          statusClass: this.statusClass(c.checkinStatus)
        }));
      }
      this.setData({ courseList: list });

      // 自动选中第一个可签到的课程
      const readyCourse = list.find(c => c.checkinStatus === 'ready');
      if (readyCourse) {
        this.selectCourse(readyCourse);
      }
    } catch (err) {
      console.warn('[上课] 加载课程信息失败:', err);
    } finally {
      this.setData({ loading: false });
    }
  },

  /** 选中课程进行签到（已签到的课程也可重新签到） */
  selectCourse(course) {
    const alreadyChecked = course.checkinStatus === 'present' || course.checkinStatus === 'late';
    this.setData({
      selectedCourse: course,
      locationChecked: false,
      locationAddr: '',
      locationLat: null,
      locationLng: null,
      faceVerified: false,
      facePhotoUrl: '',
      faceVerifyMsg: '',
      dynamicCode: '',
      checkedIn: alreadyChecked,
      checkedInStatus: alreadyChecked ? course.checkinStatus : ''
    });
  },

  /** 点击课程项 */
  onCourseTap(e) {
    const id = e.currentTarget.dataset.id;
    const course = this.data.courseList.find(c => c.id === id);
    if (!course) return;

    if (course.checkinStatus === 'waiting') {
      const startParts = course.startTime.split(':');
      const startMin = parseInt(startParts[0]) * 60 + parseInt(startParts[1]);
      const now = new Date();
      const nowMin = now.getHours() * 60 + now.getMinutes();
      const diff = startMin - 10 - nowMin;
      app.showToast('签到尚未开始（还剩约' + diff + '分钟）');
    } else if (course.checkinStatus === 'absent') {
      app.showToast('该课程已结束，无法签到');
    } else {
      // ready/present/late 均可进入签到面板（present/late 支持重新签到）
      this.selectCourse(course);
    }
  },

  /** 加载历史记录 */
  async loadHistory() {
    try {
      const result = await api.getClassHistory({ page: 1, size: 5 });
      if (result?.records) {
        this.setData({ history: result.records });
      }
    } catch (err) {
      console.warn('[上课] 加载签到记录失败:', err);
    }
  },

  // ====== 定位 ======
  async getLocation() {
    wx.showLoading({ title: '定位中...' });
    try {
      const result = await getLocationWithAddress();
      this.setData({
        locationChecked: true,
        locationLat: result.latitude.toFixed(6),
        locationLng: result.longitude.toFixed(6),
        locationAddr: result.address || `${result.latitude.toFixed(4)}, ${result.longitude.toFixed(4)}`
      });
      wx.hideLoading();
      app.showToast('定位成功', 'success');
    } catch (err) {
      wx.hideLoading();
      app.showToast(err.message || '定位失败');
    }
  },

  // ====== 人脸验证 ======
  async startFaceCheck() {
    if (this.data.faceVerifying) return;

    try {
      const faceStatus = await api.getFaceStatus();
      if (!faceStatus || !faceStatus.registered) {
        this.showFaceRegisterPrompt();
        return;
      }
    } catch (err) {
      this.showFaceRegisterPrompt();
      return;
    }

    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['camera'],
      camera: 'front',
      success: async (res) => {
        const tempFilePath = res.tempFiles[0].tempFilePath;
        this.setData({
          facePhotoUrl: tempFilePath,
          faceVerifying: true,
          faceVerifyMsg: '人脸验证中...'
        });

        try {
          const fs = wx.getFileSystemManager();
          const base64 = fs.readFileSync(tempFilePath, 'base64');
          await api.verifyFace(base64);

          this.setData({
            faceVerified: true,
            faceVerifying: false,
            faceVerifyMsg: '验证通过 ✓'
          });
          app.showToast('人脸验证通过', 'success');
        } catch (err) {
          this.setData({
            faceVerified: false,
            faceVerifying: false,
            faceVerifyMsg: err.message || '验证失败',
            facePhotoUrl: ''
          });
          app.showToast(err.message || '人脸验证失败');
        }
      },
      fail: (err) => {
        if (err.errMsg && err.errMsg.includes('cancel')) {
          app.showToast('已取消拍照');
        } else {
          app.showToast('相机调用失败');
        }
      }
    });
  },

  showFaceRegisterPrompt() {
    wx.showModal({
      title: '提示',
      content: '请先进行人脸录入',
      confirmText: '去录入',
      confirmColor: '#E74C3C',
      success: (res) => {
        if (res.confirm) {
          wx.navigateTo({ url: '/pages/face/face' });
        }
      }
    });
  },

  // ====== 动态码输入 ======
  onDynamicCodeInput(e) {
    this.setData({ dynamicCode: e.detail.value });
  },

  // ====== 提交签到 ======
  async doClassCheckin() {
    if (this.data.submitting) return;
    if (!this.data.selectedCourse) {
      app.showToast('请先选择课程');
      return;
    }
    this.setData({ submitting: true });

    try {
      const result = await api.classCheckin({
        courseId: this.data.selectedCourse.id,
        method: 'location',
        locationLat: this.data.locationLat,
        locationLng: this.data.locationLng,
        locationAddr: this.data.locationAddr,
        faceVerified: true,
        dynamicCode: this.data.dynamicCode || undefined
      });

      const checkedInStatus = result?.status || 'present';

      this.setData({
        submitting: false,
        checkedIn: true,
        checkedInStatus
      });

      await app.refreshUserData();

      wx.showToast({
        title: checkedInStatus === 'late' ? '已签到（迟到）' : '签到成功',
        icon: 'success',
        duration: 2000
      });

      // 重置定位和人脸状态，保留课程选中，可再次签到覆盖
      this.setData({
        locationChecked: false,
        locationAddr: '',
        locationLat: null,
        locationLng: null,
        faceVerified: false,
        facePhotoUrl: '',
        faceVerifyMsg: '',
        dynamicCode: ''
      });

      await this.loadHistory();
    } catch (err) {
      this.setData({ submitting: false });
      app.showToast(err.message || '签到失败');
    }
  },

  viewAllHistory() {
    wx.navigateTo({ url: '/pages/course-history/course-history' });
  },

  /** 教师跳转动态码管理 */
  goDynamicCode() {
    wx.navigateTo({ url: '/pages/teacher/dynamic-code' });
  }
});
