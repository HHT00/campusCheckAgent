/**
 * 实习打卡页面 - 从后端API获取数据 + 人脸比对验证
 */
import api from '../../api/index';
import { getLocationWithAddress } from '../../utils/location';

const app = getApp();

Page({
  data: {
    internInfo: {
      company: '',
      role: '',
      progress: 0,
      completedDays: 0,
      totalDays: 0
    },
    internshipId: null,
    locationChecked: false,
    locationAddr: '',
    inCompanyArea: false,
    // 人脸验证
    faceVerified: false,
    faceVerifying: false,
    facePhotoUrl: '',
    faceVerifyMsg: '',
    logContent: '',
    photos: [],
    checkedIn: false,
    canSubmit: false,
    submitting: false,
    loading: true,
    history: []
  },

  async onLoad() {
    await this.loadInternData();
  },

  /** 从API加载实习信息和记录 */
  async loadInternData() {
    this.setData({ loading: true });

    try {
      const [stats, historyResult] = await Promise.all([
        api.getInternStats(),
        api.getInternHistory({ page: 1, size: 5 })
      ]);

      if (stats) {
        this.setData({
          internInfo: {
            company: stats.company || '加载中...',
            role: stats.role || '加载中...',
            progress: stats.progress || 0,
            completedDays: stats.completedDays || 0,
            totalDays: stats.totalDays || 0
          }
        });
      }

      if (historyResult?.records) {
        this.setData({ history: historyResult.records });
      }

      const internship = await api.getMyInternship();
      if (internship) {
        this.setData({ internshipId: internship.id });
      }

    } catch (err) {
      console.warn('[实习] 加载数据失败:', err);
      app.showToast('加载实习数据失败');
    } finally {
      this.setData({ loading: false });
    }
  },

  // ====== 真实定位 ======
  async getLocation() {
    wx.showLoading({ title: '定位中...' });
    try {
      const result = await getLocationWithAddress();
      this.setData({
        locationChecked: true,
        locationAddr: result.address || `${result.latitude.toFixed(4)}, ${result.longitude.toFixed(4)}`,
        inCompanyArea: true
      });
      this.checkCanSubmit();
      wx.hideLoading();
      app.showToast('定位成功', 'success');
    } catch (err) {
      wx.hideLoading();
      app.showToast(err.message || '定位失败');
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

        try {
          const fs = wx.getFileSystemManager();
          const base64 = fs.readFileSync(tempFilePath, 'base64');
          const result = await api.verifyFace(base64);

          this.setData({
            faceVerified: true,
            faceVerifying: false,
            faceVerifyMsg: '验证通过 ✓'
          });
          this.checkCanSubmit();
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

  // ====== 日志输入 ======
  onLogInput(e) {
    this.setData({ logContent: e.detail.value });
    this.checkCanSubmit();
  },

  // ====== 图片上传 ======
  addPhoto() {
    if (this.data.photos.length >= 4) {
      app.showToast('最多上传4张照片');
      return;
    }
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const tempFile = res.tempFiles[0].tempFilePath;
        this.setData({
          photos: [...this.data.photos, { url: tempFile, temp: true }]
        });
      },
      fail: () => {}
    });
  },

  removePhoto(e) {
    const index = e.currentTarget.dataset.index;
    this.setData({
      photos: this.data.photos.filter((_, i) => i !== index)
    });
  },

  checkCanSubmit() {
    const { locationChecked, faceVerified, logContent, checkedIn } = this.data;
    this.setData({
      canSubmit: locationChecked && faceVerified && logContent.trim().length > 0 && !checkedIn
    });
  },

  // ====== 提交打卡 ======
  async doInternCheckin() {
    if (this.data.submitting || this.data.checkedIn) return;
    this.setData({ submitting: true });

    try {
      const result = await api.internCheckin({
        internshipId: this.data.internshipId,
        locationAddr: this.data.locationAddr,
        logContent: this.data.logContent,
        faceVerified: true
      });

      this.setData({
        submitting: false,
        checkedIn: true,
        canSubmit: false
      });

      // 上传本地图片
      const localPhotos = this.data.photos.filter(p => p.temp);
      for (const photo of localPhotos) {
        try {
          await api.uploadInternPhoto(result.id, photo.url);
        } catch (photoErr) {
          console.warn('[实习] 图片上传失败:', photoErr);
        }
      }

      await app.refreshUserData();

      wx.showModal({
        title: '打卡成功',
        content: '实习打卡已完成，日志已保存！',
        confirmText: '知道了',
        confirmColor: '#E74C3C'
      });

      const historyResult = await api.getInternHistory({ page: 1, size: 5 });
      if (historyResult?.records) {
        this.setData({ history: historyResult.records });
      }
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
    wx.navigateTo({ url: '/pages/intern-history/intern-history' });
  }
});
