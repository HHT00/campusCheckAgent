/**
 * 请假详情页面
 */
import api from '../../../api/index';

const app = getApp();

Page({
  data: {
    leave: null,
    loading: true
  },

  async onLoad(options) {
    const id = options.id;
    if (id) {
      await this.loadDetail(id);
    }
  },

  async loadDetail(id) {
    this.setData({ loading: true });
    try {
      const detail = await api.getLeaveDetail(id);
      this.setData({ leave: detail });
    } catch (err) {
      console.warn('[请假详情] 加载失败:', err);
      app.showToast('加载失败');
    } finally {
      this.setData({ loading: false });
    }
  }
});
