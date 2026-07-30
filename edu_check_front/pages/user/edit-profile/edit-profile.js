/**
 * 个人信息编辑
 */
import api from '../../../api/index';

const app = getApp();

Page({
  data: {
    form: {
      name: '',
      phone: '',
      email: '',
      college: '',
      major: '',
      grade: ''
    },
    saving: false
  },

  async onLoad() {
    await this.loadProfile();
  },

  async loadProfile() {
    try {
      const profile = await api.getUserProfile();
      if (profile) {
        this.setData({
          form: {
            name: profile.name || '',
            phone: profile.phone || '',
            email: profile.email || '',
            college: profile.college || '',
            major: profile.major || '',
            grade: profile.grade || ''
          }
        });
      }
    } catch (err) {
      app.showToast('加载个人信息失败');
    }
  },

  onFieldChange(e) {
    const field = e.currentTarget.dataset.field;
    const value = e.detail.value;
    this.setData({ [`form.${field}`]: value });
  },

  async save() {
    const { name } = this.data.form;
    if (!name.trim()) {
      app.showToast('姓名不能为空');
      return;
    }

    this.setData({ saving: true });
    try {
      await api.updateUserProfile(this.data.form);
      // 刷新全局数据
      await app.refreshUserData();
      app.showToast('保存成功', 'success');
      wx.navigateBack();
    } catch (err) {
      app.showToast(err.message || '保存失败');
    } finally {
      this.setData({ saving: false });
    }
  }
});
