/**
 * 教师端 - 发布公告
 */
import api from '../../api/index';

const app = getApp();

Page({
  data: {
    announceList: [],
    page: 1,
    pageSize: 20,
    hasMore: false,
    loading: true,

    // 发布表单
    showForm: false,
    typeIndex: 0,
    typeOptions: [
      { value: 'notice', name: '通知公告' },
      { value: 'activity', name: '活动预告' },
      { value: 'academic', name: '学术讲座' },
      { value: 'policy', name: '思政教育' }
    ],
    form: {
      title: '',
      type: 'notice',
      typeName: '通知公告',
      summary: '',
      content: '',
      department: '',
      isTop: 0
    },
    submitting: false,

    // 编辑模式
    editId: null
  },

  async onLoad() {
    await this.loadAnnouncements();
  },

  async loadAnnouncements() {
    this.setData({ loading: true });
    try {
      const result = await api.getTeacherAllAnnouncements({
        page: this.data.page,
        size: this.data.pageSize
      });
      this.setData({
        announceList: result?.records || [],
        hasMore: result?.records?.length >= this.data.pageSize
      });
    } catch (err) {
      console.warn('[公告管理] 加载失败:', err);
      app.showToast('加载公告失败');
    } finally {
      this.setData({ loading: false });
    }
  },

  showCreateForm() {
    this.setData({
      showForm: true,
      editId: null,
      typeIndex: 0,
      form: {
        title: '',
        type: 'notice',
        typeName: '通知公告',
        summary: '',
        content: '',
        department: '',
        isTop: 0
      }
    });
  },

  hideForm() {
    this.setData({ showForm: false });
  },

  onFieldChange(e) {
    const field = e.currentTarget.dataset.field;
    const value = e.detail.value;
    this.setData({
      [`form.${field}`]: value
    });
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

  onTopChange(e) {
    this.setData({
      'form.isTop': e.detail.value ? 1 : 0
    });
  },

  async submitForm() {
    const { title, summary } = this.data.form;
    if (!title.trim()) {
      app.showToast('请输入公告标题');
      return;
    }
    if (!summary.trim()) {
      app.showToast('请输入公告摘要');
      return;
    }

    this.setData({ submitting: true });
    try {
      if (this.data.editId) {
        await api.updateAnnouncement(this.data.editId, this.data.form);
        app.showToast('更新成功', 'success');
      } else {
        await api.createAnnouncement(this.data.form);
        app.showToast('发布成功', 'success');
      }
      this.setData({ showForm: false });
      this.loadAnnouncements();
    } catch (err) {
      app.showToast(err.message || '操作失败');
    } finally {
      this.setData({ submitting: false });
    }
  },

  editAnnouncement(e) {
    const item = e.currentTarget.dataset.item;
    const idx = this.data.typeOptions.findIndex(o => o.value === item.type);
    this.setData({
      showForm: true,
      editId: item.id,
      typeIndex: idx >= 0 ? idx : 0,
      form: {
        title: item.title,
        type: item.type,
        typeName: item.typeName,
        summary: item.summary,
        content: item.content || '',
        department: item.department || '',
        isTop: item.isTop || 0
      }
    });
  },

  deleteAnnouncement(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '确认删除',
      content: '确定删除此公告？',
      success: async (res) => {
        if (res.confirm) {
          try {
            await api.deleteAnnouncement(id);
            app.showToast('已删除', 'success');
            this.loadAnnouncements();
          } catch (err) {
            app.showToast(err.message || '删除失败');
          }
        }
      }
    });
  }
});
