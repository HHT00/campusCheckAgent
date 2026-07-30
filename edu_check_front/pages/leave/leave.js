/**
 * 请假页面 - 从后端API获取数据，教师可跳转审批
 */
import api from '../../api/index';

const app = getApp();

Page({
  data: {
    filterType: 'all',
    hasMore: true,
    page: 1,
    pageSize: 20,
    stats: { total: 0, pending: 0, approved: 0, rejected: 0 },
    leaveList: [],
    loading: false,
    isTeacher: false
  },

  onLoad() {
    this.checkRoleAndLoad();
  },

  onShow() {
    this.checkRoleAndLoad();
  },

  checkRoleAndLoad() {
    const role = app.globalData.role || wx.getStorageSync('userRole') || '';
    this.setData({ isTeacher: role === 'teacher' });
    if (!this.data.isTeacher) {
      this.loadData();
    }
  },

  /** 教师跳转审批页 */
  goLeaveApprove() {
    wx.navigateTo({ url: '/pages/teacher/leave-approve' });
  },

  /** 初始化加载统计 + 列表 */
  async loadData() {
    this.setData({ loading: true });

    try {
      const [statsData, listResult] = await Promise.all([
        api.getLeaveStats(),
        api.getLeaves({
          page: 1,
          size: this.data.pageSize,
          type: this.data.filterType !== 'all' ? this.data.filterType : undefined
        })
      ]);

      this.setData({
        stats: {
          total: statsData?.total || 0,
          pending: statsData?.pending || 0,
          approved: statsData?.approved || 0,
          rejected: statsData?.rejected || 0
        },
        leaveList: listResult?.records || this.data.leaveList,
        hasMore: listResult?.records?.length >= this.data.pageSize,
        page: 1,
        loading: false
      });
    } catch (err) {
      console.warn('[请假] 加载数据失败:', err);
      app.showToast('加载请假数据失败');
      this.setData({ loading: false });
    }
  },

  /** 提交请假 */
  applyLeave() {
    wx.navigateTo({
      url: '/pages/leave/apply/apply'
    });
  },

  /** 切换筛选 */
  switchFilter(e) {
    const type = e.currentTarget.dataset.type;
    if (type === this.data.filterType) return;
    this.setData({ filterType: type, page: 1, leaveList: [] });
    this.loadData();
  },

  /** 查看请假详情 */
  viewLeaveDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({
      url: `/pages/leave/detail?id=${id}`
    });
  },

  /** 加载更多 */
  async loadMore() {
    if (!this.data.hasMore || this.data.loading) return;

    const nextPage = this.data.page + 1;
    this.setData({ loading: true });

    try {
      const result = await api.getLeaves({
        page: nextPage,
        size: this.data.pageSize,
        type: this.data.filterType !== 'all' ? this.data.filterType : undefined
      });

      this.setData({
        leaveList: [...this.data.leaveList, ...(result?.records || [])],
        hasMore: result?.records?.length >= this.data.pageSize,
        page: nextPage,
        loading: false
      });
    } catch (err) {
      console.warn('[请假] 加载更多失败:', err);
      this.setData({ loading: false });
    }
  }
});
