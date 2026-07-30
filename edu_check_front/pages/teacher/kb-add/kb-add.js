/**
 * 新建知识库条目（自动同步到 RAG 索引）
 */
const app = getApp();

Page({
  data: {
    categoryIndex: 0,
    categoryOptions: ['通用', '上课', '查寝', '实习', '请假', '人脸', '统计', '教师', '公告', '课程', '助手', '通知'],
    question: '',
    answer: '',
    keywords: '',
    submitting: false,
    resultVisible: false,
    resultSuccess: false,
    resultMsg: ''
  },

  onCategoryChange(e) {
    this.setData({ categoryIndex: parseInt(e.detail.value) });
  },

  onQuestionInput(e) {
    this.setData({ question: e.detail.value });
  },

  onAnswerInput(e) {
    this.setData({ answer: e.detail.value });
  },

  onKeywordsInput(e) {
    this.setData({ keywords: e.detail.value });
  },

  submit() {
    const question = this.data.question.trim();
    const answer = this.data.answer.trim();

    if (!question) {
      app.showToast('请输入问题');
      return;
    }
    if (!answer) {
      app.showToast('请输入答案');
      return;
    }

    this.setData({ submitting: true, resultVisible: false });

    const token = wx.getStorageSync('token') || '';
    wx.request({
      url: 'http://localhost:8080/api/admin/kb',
      method: 'POST',
      header: { 'Authorization': token, 'Content-Type': 'application/json' },
      data: {
        category: this.data.categoryOptions[this.data.categoryIndex],
        question,
        answer,
        keywords: this.data.keywords
      },
      success: (res) => {
        if (res.data?.code === 200) {
          this.setData({
            resultVisible: true,
            resultSuccess: true,
            resultMsg: '✅ 添加成功，已同步到 RAG 索引，智能问答立即可用',
            question: '',
            answer: '',
            keywords: ''
          });
          app.showToast('添加成功', 'success');
        } else {
          this.setData({
            resultVisible: true,
            resultSuccess: false,
            resultMsg: '❌ 添加失败：' + (res.data?.message || '未知错误')
          });
        }
      },
      fail: () => {
        this.setData({
          resultVisible: true,
          resultSuccess: false,
          resultMsg: '❌ 网络错误，请检查后端是否启动'
        });
      },
      complete: () => {
        this.setData({ submitting: false });
      }
    });
  }
});
