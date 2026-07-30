/**
 * 查寝打卡页面 - 真实定位 + 人脸比对验证
 */
import api from '../../api/index';
import { getLocationWithAddress, getRealLocation } from '../../utils/location';

const app = getApp();

Page({
  data: {
    currentTime: '',
    currentDate: '',
    currentWeekday: '',
    locationChecked: false,
    locationAddr: '',
    locationLat: '',
    locationLng: '',
    inDormArea: true,
    faceVerified: false,
    faceVerifying: false,      // 人脸验证中
    facePhotoUrl: '',
    faceVerifyMsg: '',         // 验证结果提示
    checkedIn: false,
    canCheckin: false,
    submitting: false,
    loading: true,
    locationing: false,
    history: []
  },

  async onLoad() {
    this.updateTime();
    this.startTimer();
    await Promise.all([
      this.loadTodayStatus(),
      this.loadHistory()
    ]);
  },

  onUnload() {
    this.clearTimer();
  },

  /** 从API获取今日打卡状态 */
  async loadTodayStatus() {
    try {
      const status = await api.getDormTodayStatus();
      console.log("获取今日打卡状态：{}",status)
      if (status) {
        this.setData({
          checkedIn: true,
          canCheckin: false,
          locationChecked: true,
          faceVerified: true,
          inDormArea: status.inDormArea === 1,
          locationAddr: status.locationAddr || '',
          locationLat: status.locationLat || '',
          locationLng: status.locationLng || ''
        });
      }
    } catch (err) {
      console.warn('[查寝] 加载今日状态失败:', err);
    } finally {
      this.setData({ loading: false });
    }
  },

  /** 从API获取打卡记录 */
  async loadHistory() {
    try {
      const result = await api.getDormHistory({ page: 1, size: 5 });
      if (result?.records) {
        this.setData({ history: result.records });
      }
    } catch (err) {
      console.warn('[查寝] 加载记录失败:', err);
    }
  },

  // ====== 时间显示 ======
  updateTime() {
    const now = new Date();
    const pad = n => String(n).padStart(2, '0');
    const weekdays = ['日', '一', '二', '三', '四', '五', '六'];
    this.setData({
      currentTime: `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`,
      currentDate: `${now.getFullYear()}年${pad(now.getMonth() + 1)}月${pad(now.getDate())}日`,
      currentWeekday: `星期${weekdays[now.getDay()]}`
    });
  },

  startTimer() {
    this.timer = setInterval(() => { this.updateTime(); }, 1000);
  },

  clearTimer() {
    if (this.timer) { clearInterval(this.timer); this.timer = null; }
  },

  // ====== 真实 GPS 定位 ======
  async getLocation() {
    if (this.data.locationing) return;
    this.setData({ locationing: true });
    wx.showLoading({ title: '定位中...' });

    try {
      const result = await getLocationWithAddress();
      this.setData({
        locationChecked: true,
        locationLat: result.latitude.toFixed(6),
        locationLng: result.longitude.toFixed(6),
        locationAddr: result.address || `${result.latitude.toFixed(4)}, ${result.longitude.toFixed(4)}`,
        inDormArea: true,
        canCheckin: this.data.faceVerified
      });
      wx.hideLoading();
      app.showToast('定位成功', 'success');
    } catch (err) {
      wx.hideLoading();
      app.showToast(err.message || '定位失败');
    } finally {
      this.setData({ locationing: false });
    }
  },

  // ====== 人脸验证（拍照 → 百度云比对） ======
  async startFaceCheck() {
    if (this.data.faceVerifying) return;

    // 检查是否已录入人脸（接口失败也视为未录入，统一引导用户注册）
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

        // 读取图片为 base64，调后端验证
        try {
          const fs = wx.getFileSystemManager();
          const base64 = fs.readFileSync(tempFilePath, 'base64');
          const result = await api.verifyFace(base64);

          this.setData({
            faceVerified: true,
            faceVerifying: false,
            faceVerifyMsg: '验证通过 ✓',
            canCheckin: this.data.locationChecked
          });
          this.checkCanCheckin();
          app.showToast('人脸验证通过', 'success');
        } catch (err) {
          this.setData({
            faceVerified: false,
            faceVerifying: false,
            faceVerifyMsg: err.message || '验证失败',
            facePhotoUrl: ''  // 清空，让用户重拍
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

  checkCanCheckin() {
    const { locationChecked, faceVerified } = this.data;
    this.setData({ canCheckin: locationChecked && faceVerified });
  },

  // ====== 提交打卡 ======
  async doDormCheckin() {
    if (this.data.submitting) return;
    this.setData({ submitting: true });

    try {
      await api.dormCheckin({
        locationLat: parseFloat(this.data.locationLat),
        locationLng: parseFloat(this.data.locationLng),
        locationAddr: this.data.locationAddr,
        inDormArea: this.data.inDormArea,
        faceImage: this.data.facePhotoUrl || '',
        faceVerified: true
      });

      this.setData({ checkedIn: true, canCheckin: true, submitting: false });

      await app.refreshUserData();

      wx.showModal({
        title: '打卡成功',
        content: '归寝打卡已完成，请保持良好作息习惯！',
        confirmText: '知道了',
        confirmColor: '#E74C3C'
      });

      await this.loadHistory();
    } catch (err) {
      this.setData({ submitting: false });
      app.showToast(err.message || '打卡失败');
    }
  },

  /** 弹出人脸录入引导 */
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
  

  viewAllHistory() {
    wx.navigateTo({ url: '/pages/dorm-history/dorm-history' });
  }
});
