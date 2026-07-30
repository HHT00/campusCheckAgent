/**
 * 意见反馈 - 学生可提交，教师可查看全部
 */
import api from '../../api/index';

const app = getApp();

Page({
  data: {
    role: 'student',
    // 提交表单
    showForm: false,
    typeIndex: 0,
    typeOptions: [
      { value: 'suggestion', name: '建议' },
      { value: 'bug', name: '问题反馈' },
      { value: 'complaint', name: '投诉' },
      { value: 'other', name: '其他' }
    ],
    form: {
      type: 'suggestion',
      content: '',
      contact: ''
    },
    submitting: false,
    // 列表
    list: [],
    loading: true
  },

  async onLoad() {
    const role = app.globalData.role || wx.getStorageSync('userRole') || 'student';
    this.setData({ role });
    await this.loadList();
  },

  async loadList() {
    this.setData({ loading: true });
    try {
      if (this.data.role === 'teacher') {
        const result = await api.getTeacherFeedback({ page: 1, size: 50 });
        this.setData({ list: result?.records || [] });
      } else {
        const result = await api.getMyFeedback({ page: 1, size: 50 });
        this.setData({ list: result?.records || [] });
      }
    } catch (err) {
      console.warn('[反馈] 加载失败:', err);
    } finally {
      this.setData({ loading: false });
    }
  },

  showSubmitForm() {
    this.setData({
      showForm: true,
      form: { type: 'suggestion', content: '', contact: '' }
    });
  },

  hideForm() {
    this.setData({ showForm: false });
  },

  onTypeChange(e) {
    const idx = parseInt(e.detail.value);
    const option = this.data.typeOptions[idx];
    if (option) {
      this.setData({
        typeIndex: idx,
        'form.type': option.value,
        'form.typeName': option.name
      });
    }
  },

  onContentInput(e) {
    this.setData({ 'form.content': e.detail.value });
  },

  onContactInput(e) {
    this.setData({ 'form.contact': e.detail.value });
  },

  async submit() {
    if (!this.data.form.content.trim()) {
      app.showToast('请输入反馈内容');
      return;
    }
    this.setData({ submitting: true });
    try {
      await api.submitFeedback({
        type: this.data.form.type,
        content: this.data.form.content,
        contact: this.data.form.contact
      });
      app.showToast('提交成功', 'success');
      this.hideForm();
      this.loadList();
    } catch (err) {
      app.showToast(err.message || '提交失败');
    } finally {
      this.setData({ submitting: false });
    }
  }
});
