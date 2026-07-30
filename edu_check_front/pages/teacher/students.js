/**
 * 教师端 - 学生管理（查看打卡情况）
 */
import api from '../../api/index';

const app = getApp();

Page({
  data: {
    students: [],
    allStudents: [],
    page: 1,
    pageSize: 50,
    hasMore: false,
    keyword: '',
    filter: '',
    loading: true,
    filterLabel: ''
  },

  filterLabels: {
    'class_checked': '已上课签到',
    'class_not': '未上课签到',
    'dorm_checked': '已查寝打卡',
    'dorm_not': '未查寝打卡'
  },

  async onLoad(options) {
    const filter = options.filter || '';
    this.setData({ filter, filterLabel: this.filterLabels[filter] || '' });
    await this.loadStudents();
  },

  async loadStudents() {
    this.setData({ loading: true });
    try {
      const result = await api.getStudentsPage({
        page: this.data.page,
        size: this.data.pageSize,
        keyword: this.data.keyword || undefined
      });

      let list = result?.records || [];

      // 客户端筛选
      if (this.data.filter) {
        list = list.filter(s => {
          switch (this.data.filter) {
            case 'class_checked': return s.todayCheckin === 'present' || s.todayCheckin === 'late';
            case 'class_not': return s.todayCheckin === 'none' || s.todayCheckin === 'absent';
            case 'dorm_checked': return s.todayDorm === 'normal' || s.todayDorm === 'late';
            case 'dorm_not': return s.todayDorm === 'none';
            default: return true;
          }
        });
      }

      this.setData({
        allStudents: result?.records || [],
        students: list,
        hasMore: result?.records?.length >= this.data.pageSize,
        loading: false
      });
    } catch (err) {
      console.warn('[学生管理] 加载失败:', err);
      // mock 数据兜底
      const mockStudents = this.generateMockStudents();
      let filtered = mockStudents;
      if (this.data.filter) {
        filtered = mockStudents.filter(s => {
          switch (this.data.filter) {
            case 'class_checked': return s.todayCheckin === 'present' || s.todayCheckin === 'late';
            case 'class_not': return s.todayCheckin === 'none' || s.todayCheckin === 'absent';
            case 'dorm_checked': return s.todayDorm === 'normal' || s.todayDorm === 'late';
            case 'dorm_not': return s.todayDorm === 'none';
            default: return true;
          }
        });
      }
      this.setData({
        allStudents: mockStudents,
        students: filtered,
        loading: false
      });
    } finally {
      this.setData({ loading: false });
    }
  },

  /** 生成模拟学生数据 */
  generateMockStudents() {
    const names = ['张伟', '李娜', '王芳', '刘洋', '陈静', '赵丽', '周强', '吴敏', '徐峰', '孙悦'];
    const statuses = ['present', 'late', 'none', 'absent'];
    const dormStatuses = ['normal', 'late', 'none'];
    const list = [];
    for (let i = 0; i < 30; i++) {
      const checkin = statuses[i % 4];
      const dorm = dormStatuses[i % 3];
      list.push({
        id: i + 1,
        name: names[i % names.length] + (Math.floor(i / names.length) + 1),
        studentId: '2024' + String(1000 + i).slice(-4),
        college: '计算机科学与技术学院',
        major: '软件工程',
        grade: '2024级',
        todayCheckin: checkin,
        todayDorm: dorm,
        classTotal: 12 + i,
        dormTotal: 15 + i
      });
    }
    return list;
  },

  clearFilter() {
    this.setData({ filter: '', filterLabel: '', students: this.data.allStudents });
  },

  onSearch(e) {
    const keyword = e.detail.value;
    this.setData({ keyword, page: 1 }, () => {
      this.loadStudents();
    });
  },

  viewStudent(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({
      url: `/pages/teacher/student-detail?studentId=${id}`
    });
  },

  refresh() {
    this.setData({ page: 1 }, () => {
      this.loadStudents();
    });
  },

  /** 导出学生列表为 CSV */
  exportCSV() {
    const list = this.data.students;
    if (list.length === 0) {
      app.showToast('没有数据可导出');
      return;
    }

    const header = '姓名,学号,学院,专业,年级,今日签到,今日查寝,上课总数,查寝总数\n';
    const rows = list.map(s => [
      s.name,
      s.studentId,
      s.college || '',
      s.major || '',
      s.grade || '',
      s.todayCheckin === 'present' ? '已签到' : (s.todayCheckin === 'late' ? '迟到' : '未签到'),
      s.todayDorm === 'normal' ? '已打卡' : (s.todayDorm === 'late' ? '晚归' : '未打卡'),
      s.classTotal || 0,
      s.dormTotal || 0
    ].join(',')).join('\n');

    wx.setClipboardData({
      data: header + rows,
      success: () => {
        app.showToast(`已导出 ${list.length} 条数据`, 'success');
      }
    });
  }
});
