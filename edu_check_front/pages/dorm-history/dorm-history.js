/**
 * 查寝打卡记录详情页 - 只读历史记录
 */
import api from '../../api/index';

const app = getApp();

Page({
  data: {
    recordList: [],
    page: 1,
    pageSize: 20,
    hasMore: true,
    loading: false,
    total: 0
  },

  onLoad() {
    this.loadRecords();
  },

  onPullDownRefresh() {
    this.setData({ page: 1, recordList: [], hasMore: true });
    this.loadRecords(() => wx.stopPullDownRefresh());
  },

  async loadRecords(callback) {
    if (this.data.loading) return;
    this.setData({ loading: true });

    try {
      const result = await api.getDormHistory({
        page: this.data.page,
        size: this.data.pageSize
      });

      const records = result?.records || [];
      this.setData({
        recordList: this.data.page === 1 ? records : [...this.data.recordList, ...records],
        hasMore: records.length >= this.data.pageSize,
        total: result?.total || 0,
        loading: false
      });
    } catch (err) {
      console.warn('[查寝记录] 加载失败:', err);
      app.showToast('加载记录失败');
      this.setData({ loading: false });
    } finally {
      callback && callback();
    }
  },

  loadMore() {
    if (!this.data.hasMore || this.data.loading) return;
    this.setData({ page: this.data.page + 1 });
    this.loadRecords();
  }
});
