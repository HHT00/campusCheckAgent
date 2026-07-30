/**
 * 公告页面 - 从后端API获取数据，教师可发布公告
 */
import api from '../../api/index';

const app = getApp();

Page({
  data: {
    currentCategory: 'all',
    searchKeyword: '',
    hasMore: true,
    page: 1,
    pageSize: 20,
    announceList: [],
    topAnnounce: null,
    loading: false,
    isTeacher: false
  },

  onLoad() {
    const role = app.globalData.role || wx.getStorageSync('userRole') || '';
    this.setData({ isTeacher: role === 'teacher' });
    this.loadData();
  },

  onShow() {
    const role = app.globalData.role || wx.getStorageSync('userRole') || '';
    this.setData({ isTeacher: role === 'teacher' });
    // 每次显示刷新数据（确保从公告管理页返回后看到最新状态）
    this.loadData();
  },

  /** 初始化加载置顶 + 列表 */
  async loadData() {
    this.setData({ loading: true });
    try {
      // 并行请求置顶和列表
      const [top, listResult] = await Promise.all([
        api.getTopAnnouncement(),
        api.getAnnouncements({
          page: 1,
          size: this.data.pageSize,
          type: this.data.currentCategory !== 'all' ? this.data.currentCategory : undefined,
          keyword: this.data.searchKeyword || undefined
        })
      ]);

      this.setData({
        topAnnounce: top || null,
        announceList: listResult?.records || [],
        hasMore: listResult?.records?.length >= this.data.pageSize,
        page: 1,
        loading: false
      });
    } catch (err) {
      console.warn('[公告] 加载数据失败:', err);
      app.showToast('加载公告失败');
      this.setData({ loading: false });
    }
  },

  /** 搜索输入 */
  onSearch(e) {
    this.setData({ searchKeyword: e.detail.value });
    // 防抖搜索
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => {
      this.setData({ page: 1, announceList: [] });
      this.loadData();
    }, 500);
  },

  /** 切换分类 */
  switchCategory(e) {
    const category = e.currentTarget.dataset.category;
    if (category === this.data.currentCategory) return;
    this.setData({ currentCategory: category, page: 1, announceList: [] });
    this.loadData();
  },

  /** 查看详情 */
  viewDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({
      url: `/pages/announcement/detail/detail?id=${id}`
    });
  },

  /** 加载更多 */
  async loadMore() {
    if (!this.data.hasMore || this.data.loading) return;

    const nextPage = this.data.page + 1;
    this.setData({ loading: true });

    try {
      const result = await api.getAnnouncements({
        page: nextPage,
        size: this.data.pageSize,
        type: this.data.currentCategory !== 'all' ? this.data.currentCategory : undefined
      });

      const newList = [...this.data.announceList, ...(result?.records || [])];
      this.setData({
        announceList: newList,
        hasMore: result?.records?.length >= this.data.pageSize,
        page: nextPage,
        loading: false
      });
    } catch (err) {
      console.warn('[公告] 加载更多失败:', err);
      this.setData({ loading: false });
    }
  },

  /** 教师发布公告 */
  goPublish() {
    wx.navigateTo({ url: '/pages/teacher/announce-publish' });
  }
});
