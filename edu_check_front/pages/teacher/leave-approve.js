/**
 * 教师端 - 请假审批
 */
import api from '../../api/index';

const app = getApp();

Page({
  data: {
    filterStatus: 'pending',
    page: 1,
    pageSize: 20,
    hasMore: false,
    leaves: [],
    stats: { total: 0, pending: 0, approved: 0, rejected: 0 },
    loading: true
  },

  async onLoad() {
    await this.loadData();
  },

  async loadData() {
    this.setData({ loading: true });

    try {
      let listResult;
      const statsData = await api.getTeacherLeaveStats();

      listResult = await api.getTeacherAllLeaves({
        page: this.data.page,
        size: this.data.pageSize,
        status: this.data.filterStatus !== 'all' ? this.data.filterStatus : undefined
      });

      this.setData({
        stats: {
          total: statsData?.total || 0,
          pending: statsData?.pending || 0,
          approved: statsData?.approved || 0,
          rejected: statsData?.rejected || 0
        },
        leaves: listResult?.records || [],
        hasMore: listResult?.records?.length >= this.data.pageSize,
        page: 1,
        loading: false
      });
    } catch (err) {
      console.warn('[请假审批] 加载失败:', err);
      // mock 数据兜底
      this.setData({
        stats: { total: 3, pending: 1, approved: 2, rejected: 0 },
        leaves: [
          { id: 1, studentName: '李同学', studentId: '2024011002', type: 'personal', typeName: '事假', startDate: '2026-07-29', endDate: '2026-07-29', days: 1, reason: '家里有事需回家一趟', status: 'pending', submitTime: '2026-07-28 15:30' },
          { id: 2, studentName: '张小明', studentId: '2024011001', type: 'sick', typeName: '病假', startDate: '2026-07-27', endDate: '2026-07-27', days: 1, reason: '感冒发烧', status: 'approved', submitTime: '2026-07-26 20:00', approveTime: '2026-07-27 09:00' },
          { id: 3, studentName: '陈同学', studentId: '2024011003', type: 'sick', typeName: '病假', startDate: '2026-07-25', endDate: '2026-07-26', days: 2, reason: '身体不适', status: 'approved', submitTime: '2026-07-24 18:00', approveTime: '2026-07-25 10:00' }
        ],
        loading: false
      });
    }
  },

  switchFilter(e) {
    const status = e.currentTarget.dataset.status;
    if (status === this.data.filterStatus) return;
    this.setData({ filterStatus: status, page: 1, leaves: [] }, () => {
      this.loadData();
    });
  },

  approveLeave(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '审批确认',
      content: '确认批准该请假申请？',
      success: async (res) => {
        if (res.confirm) {
          try {
            await api.approveLeave(id, { approved: true });
            app.showToast('已批准', 'success');
            this.loadData();
          } catch (err) {
            app.showToast(err.message || '操作失败');
          }
        }
      }
    });
  },

  rejectLeave(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '驳回请假',
      editable: true,
      placeholderText: '请输入驳回原因（可选）',
      success: async (res) => {
        if (res.confirm) {
          try {
            await api.approveLeave(id, {
              approved: false,
              rejectReason: res.content || '未说明原因'
            });
            app.showToast('已驳回', 'success');
            this.loadData();
          } catch (err) {
            app.showToast(err.message || '操作失败');
          }
        }
      }
    });
  }
});
