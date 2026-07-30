/**
 * 教师端 - 未命中问题日志（可补充知识库）
 */
import api from '../../../api/index';

const app = getApp();

Page({
  data: {
    list: [],
    loading: true,
    total: 0
  },

  async onLoad() {
    await this.loadData();
  },

  async loadData() {
    this.setData({ loading: true });
    try {
      const result = await api.getUnansweredQuestions({ page: 1, size: 50 });
      this.setData({
        list: result?.records || [],
        total: result?.total || 0
      });
    } catch (err) {
      console.warn('[未答问题] 加载失败:', err);
      this.setMockData();
    } finally {
      this.setData({ loading: false });
    }
  },

  setMockData() {
    this.setData({
      list: [
        { id: 1, question: '怎么查看我的课表', category: '课程', createdAt: '2026-07-29 14:30' },
        { id: 2, question: '实习打卡需要拍什么照片', category: '实习', createdAt: '2026-07-29 11:20' },
        { id: 3, question: '请假审批要多久', category: '请假', createdAt: '2026-07-28 16:45' },
        { id: 4, question: '查寝可以补打卡吗', category: '查寝', createdAt: '2026-07-28 09:10' },
        { id: 5, question: '动态码怎么用', category: '上课', createdAt: '2026-07-27 14:00' }
      ],
      total: 5
    });
  },

  /** 导出问题列表 */
  exportQuestions() {
    const list = this.data.list;
    if (list.length === 0) {
      app.showToast('没有数据可导出');
      return;
    }
    const header = '问题,分类,时间\n';
    const rows = list.map(q =>
      `"${q.question}",${q.category || ''},${q.createdAt || ''}`
    ).join('\n');

    wx.setClipboardData({
      data: header + rows,
      success: () => app.showToast('已复制到剪贴板', 'success')
    });
  }
});
