/**
 * 教师工作台 — Feishu 风格
 */
import api from '../../api/index';
const app = getApp();

Page({
  data: {
    currentDate: '',
    syncStatus: '',
    vectorStatus: '',
    summary: { totalStudents: 128, classChecked: 96, classNotChecked: 32, dormChecked: 112, dormNotChecked: 16 },
    pendingCount: 3,
    alerts: [
      { level: 'danger', title: '连续旷课学生 3 人', desc: '张三、李四、王五' },
      { level: 'warn', title: '近7天晚归频次较高', desc: '5号宿舍楼 318 室' }
    ],
    weekTrend: [
      { label: '周一', rate: 88, color: '#10B981' },
      { label: '周二', rate: 92, color: '#10B981' },
      { label: '周三', rate: 85, color: '#10B981' },
      { label: '周四', rate: 78, color: '#F59E0B' },
      { label: '周五', rate: 90, color: '#10B981' }
    ],
    courseRanking: [],
    dormRanking: [],
    leaveAnomaly: []
  },

  onLoad() {
    this.updateDate();
  },

  onShow() {
    this.tryLoadFromApi();
  },

  updateDate() {
    const now = new Date();
    const wd = ['日','一','二','三','四','五','六'];
    this.setData({
      currentDate: `${now.getFullYear()}年${now.getMonth()+1}月${now.getDate()}日 星期${wd[now.getDay()]}`
    });
  },

  /** 尝试从 API 加载，失败不动（默认已有 mock 数据） */
  async tryLoadFromApi() {
    try {
      const s = await api.getTodayCheckinSummary();
      if (s) this.setData({ summary: s });
    } catch (_) { /* 保持 mock */ }

    try {
      const stats = await api.getTeacherLeaveStats();
      if (stats) this.setData({ pendingCount: stats.pending || 0 });
    } catch (_) { /* 保持 mock */ }

    // 加载数据分析排行
    try {
      const cr = await api.getCourseRanking();
      if (cr && cr.length > 0) this.setData({ courseRanking: cr });
    } catch (_) {}
    try {
      const dr = await api.getDormRanking();
      if (dr && dr.length > 0) this.setData({ dormRanking: dr });
    } catch (_) {}
    try {
      const la = await api.getLeaveAnomaly();
      if (la && la.length > 0) this.setData({ leaveAnomaly: la });
    } catch (_) {}
  },

  /** 手动触发后端数据同步 */
  triggerSync() {
    this.setData({ syncStatus: '同步中...' });
    wx.request({
      url: 'http://localhost:8080/api/sync/all',
      method: 'POST',
      header: { 'Authorization': wx.getStorageSync('token') || '' },
      success: (res) => {
        if (res.data?.code === 200) {
          this.setData({ syncStatus: '✅ 同步完成 ' + (res.data.data?.elapsed || '') });
          // 重新加载数据
          this.tryLoadFromApi();
        } else {
          this.setData({ syncStatus: '❌ 同步失败' });
        }
      },
      fail: () => {
        this.setData({ syncStatus: '❌ 后端未连接' });
      },
      complete: () => {
        setTimeout(() => this.setData({ syncStatus: '' }), 5000);
      }
    });
  },

  /** 重建知识库向量索引 */
  rebuildVectors() {
    this.setData({ vectorStatus: '重建中…' });
    wx.request({
      url: 'http://localhost:8080/api/sync/vectors',
      method: 'POST',
      header: { 'Authorization': wx.getStorageSync('token') || '' },
      success: (res) => {
        if (res.data?.code === 200) {
          this.setData({ vectorStatus: '✅ 重建完成' });
          app.showToast('向量索引已重建', 'success');
        } else {
          this.setData({ vectorStatus: '❌ 重建失败' });
        }
      },
      fail: () => { this.setData({ vectorStatus: '❌ 后端未连接' }); },
      complete: () => { setTimeout(() => this.setData({ vectorStatus: '' }), 5000); }
    });
  },

  exportSummary() {
    const s = this.data.summary;
    const t = s.totalStudents || 1;
    const csv = `指标,人数,占比\n总人数,${s.totalStudents},100%\n已签到,${s.classChecked},${(s.classChecked/t*100).toFixed(1)}%\n未签到,${s.classNotChecked},${(s.classNotChecked/t*100).toFixed(1)}%\n已查寝,${s.dormChecked},${(s.dormChecked/t*100).toFixed(1)}%\n未查寝,${s.dormNotChecked},${(s.dormNotChecked/t*100).toFixed(1)}%`;
    wx.setClipboardData({ data: csv, success: () => app.showToast('已复制', 'success') });
  },

  goStudents(e) {
    const f = e.currentTarget.dataset.filter || '';
    wx.navigateTo({ url: '/pages/teacher/students?filter=' + f });
  },
  goAlertCenter() { wx.navigateTo({ url: '/pages/teacher/alert/alert' }); },
  goLeaveApprove() { wx.navigateTo({ url: '/pages/teacher/leave-approve' }); },
  goDynamicCode() { wx.navigateTo({ url: '/pages/teacher/dynamic-code' }); },
  goAnnouncePublish() { wx.navigateTo({ url: '/pages/teacher/announce-publish' }); },
  goKbAdd() { wx.navigateTo({ url: '/pages/teacher/kb-add/kb-add' }); }
});
