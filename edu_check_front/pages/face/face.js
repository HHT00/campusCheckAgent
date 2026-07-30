/**
 * 人脸录入页面 - 录入/更新人脸用于打卡验证
 */
import api from '../../api/index';

const app = getApp();

Page({
  data: {
    registered: false,
    faceVersion: 0,
    registeredAt: '',
    photoUrl: '',           // 拍照后的临时路径
    photoTaken: false,
    submitting: false,
    loading: true,
    resultVisible: false,
    resultSuccess: false,
    resultMsg: ''
  },

  async onLoad() {
    await this.loadStatus();
  },

  /** 加载人脸录入状态 */
  async loadStatus() {
    this.setData({ loading: true });
    try {
      const status = await api.getFaceStatus();
      console.log("face31","getFacestatus 请求成功，返回值为",status)
      this.setData({
        registered: status.registered || false,
        faceVersion: status.version || 0,
        registeredAt: status.registeredAt || ''
      });
    } catch (err) {
      console.warn('[人脸] 加载状态失败:', err);
    } finally {
      this.setData({ loading: false });
    }
  },

  /** 拍照（前置摄像头） */
  takePhoto() {
    if (this.data.submitting) return;

    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['camera'],
      camera: 'front',
      success: (res) => {
        const tempPath = res.tempFiles[0].tempFilePath;
        this.setData({
          photoUrl: tempPath,
          photoTaken: true,
          resultVisible: false
        });
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

  /** 重新拍照 */
  retakePhoto() {
    this.setData({
      photoUrl: '',
      photoTaken: false,
      resultVisible: false
    });
  },

  /** 提交人脸录入 */
  async submitFace() {
    if (this.data.submitting || !this.data.photoUrl) return;
    this.setData({ submitting: true, resultVisible: false });

    wx.showLoading({ title: '人脸录入中...' });

    try {
      // 将临时文件转为 base64
      const fs = wx.getFileSystemManager();
      const base64 = fs.readFileSync(this.data.photoUrl, 'base64');

      // 调用后端录入接口
      await api.registerFace(base64);

      // 刷新状态
      await this.loadStatus();

      this.setData({
        resultVisible: true,
        resultSuccess: true,
        resultMsg: this.data.registered
          ? '人脸更新成功！打卡时需进行人脸验证'
          : '人脸录入成功！打卡时需进行人脸验证'
      });
    } catch (err) {
      this.setData({
        resultVisible: true,
        resultSuccess: false,
        resultMsg: err.message || '人脸录入失败，请重试'
      });
    } finally {
      wx.hideLoading();
      this.setData({ submitting: false });
    }
  }
});
