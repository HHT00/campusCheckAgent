/**
 * 请假申请页面
 */
import api from '../../../api/index';

const app = getApp();

Page({
  data: {
    form: {
      type: 'sick',
      startDate: '',
      endDate: '',
      reason: ''
    },
    typeOptions: [
      { value: 'sick', name: '病假' },
      { value: 'personal', name: '事假' },
      { value: 'official', name: '公假' },
      { value: 'annual', name: '年假' }
    ],
    typeIndex: 0,
    currentTypeName: '病假',
    submitting: false,
    minDate: ''
  },

  onLoad() {
    const today = new Date();
    const y = today.getFullYear();
    const m = String(today.getMonth() + 1).padStart(2, '0');
    const d = String(today.getDate()).padStart(2, '0');
    const dateStr = `${y}-${m}-${d}`;
    this.setData({
      minDate: dateStr,
      'form.startDate': dateStr,
      'form.endDate': dateStr
    });
  },

  onTypeChange(e) {
    const idx = parseInt(e.detail.value);
    const typeObj = this.data.typeOptions[idx];
    if (typeObj) {
      this.setData({
        'form.type': typeObj.value,
        typeIndex: idx,
        currentTypeName: typeObj.name
      });
    }
  },

  onStartDateChange(e) {
    this.setData({ 'form.startDate': e.detail.value });
  },

  onEndDateChange(e) {
    this.setData({ 'form.endDate': e.detail.value });
  },

  onReasonInput(e) {
    this.setData({ 'form.reason': e.detail.value });
  },

  async submit() {
    const { startDate, endDate, reason } = this.data.form;
    if (!reason.trim()) {
      app.showToast('请输入请假事由');
      return;
    }
    if (startDate > endDate) {
      app.showToast('结束日期不能早于开始日期');
      return;
    }

    this.setData({ submitting: true });
    try {
      await api.applyLeave(this.data.form);
      app.showToast('提交成功', 'success');
      setTimeout(() => {
        wx.navigateBack();
      }, 1500);
    } catch (err) {
      app.showToast(err.message || '提交失败');
    } finally {
      this.setData({ submitting: false });
    }
  }
});
