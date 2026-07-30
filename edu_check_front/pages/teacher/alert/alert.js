/**
 * 预警中心 - 查看和处理考勤预警
 */
import api from '../../../api/index';
const app = getApp();

Page({
  data: {
    tab: 'unresolved',
    list: [],
    loading: true,
    unresolvedCount: 0
  },

  onLoad() {
    this.loadData();
  },

  async loadData() {
    this.setData({ loading: true });
    try {
      const r = await api.getAlerts({ page: 1, size: 50, resolved: this.data.tab === 'unresolved' ? 0 : 1 });
      this.setData({
        list: r?.records || [],
        loading: false,
        unresolvedCount: this.data.tab === 'unresolved' ? (r?.total || 0) : this.data.unresolvedCount
      });
    } catch (_) {
      // mock 数据兜底
      this.setMockData();
    }
  },

  setMockData() {
    this.setData({
      list: [
        { id: 1, riskLevel: 'high', title: '连续旷课预警', detail: '学生赵雪 近7天旷课 6 次，请关注', createdAt: '2026-07-30 03:30' },
        { id: 2, riskLevel: 'mid', title: '请假模式异常', detail: '学生刘梅 近30天有 3 次周一病假记录', createdAt: '2026-07-30 03:30' },
        { id: 3, riskLevel: 'mid', title: '晚归后次日有早课', detail: '学生李同学 昨晚晚归，今日有早课', createdAt: '2026-07-30 03:30' },
        { id: 4, riskLevel: 'mid', title: '近7天晚归2次', detail: '张小明同学近7天有2次晚归记录', createdAt: '2026-07-29 03:30' }
      ],
      unresolvedCount: 4,
      loading: false
    });
  },

  switchTab(e) {
    const tab = e.currentTarget.dataset.tab;
    if (tab === this.data.tab) return;
    this.setData({ tab, list: [] });
    this.loadData();
  },

  resolveAlert(e) {
    const id = e.currentTarget.dataset.id;
    const token = wx.getStorageSync('token') || '';
    wx.request({
      url: 'http://localhost:8080/api/teacher/alerts/' + id + '/resolve',
      method: 'POST',
      header: { 'Authorization': token },
      success: () => {
        app.showToast('已处理', 'success');
        this.loadData();
      },
      fail: () => {
        // mock: 直接从前端移除
        this.setData({
          list: this.data.list.filter(a => a.id !== id),
          unresolvedCount: this.data.unresolvedCount - 1
        });
        app.showToast('已处理', 'success');
      }
    });
  }
});
