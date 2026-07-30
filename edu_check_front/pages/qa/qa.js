/**
 * 智能问答 - 支持多轮对话 + 来源展示 + 自动滚动
 */
import api from '../../api/index';

const app = getApp();

Page({
  data: {
    messages: [],
    inputValue: '',
    hotList: [],
    searching: false,
    sendDisabled: true,
    scrollTarget: 'msg-bottom',
    category: 'all',
    categoryList: [
      { value: 'all', name: '全部' },
      { value: 'frontend', name: '前端' },
      { value: 'backend', name: '后端' },
      { value: 'database', name: '数据库' },
      { value: 'general', name: '通用' }
    ],
    expandedSources: {}
  },

  async onLoad() {
    await this.loadHotQuestions();
  },

  async loadHotQuestions() {
    try {
      const list = await api.getHotQuestions();
      this.setData({ hotList: list || [] });
    } catch (err) {
      console.warn('[问答] 加载热门问题失败:', err);
    }
  },

  onInput(e) {
    const val = e.detail.value;
    this.setData({ inputValue: val, sendDisabled: !val.trim() });
  },

  switchCategory(e) {
    const cat = e.currentTarget.dataset.cat;
    this.setData({ category: cat });
  },

  toggleSource(e) {
    const idx = e.currentTarget.dataset.idx;
    this.setData({ [`expandedSources.${idx}`]: !this.data.expandedSources[idx] });
  },

  async send() {
    const question = this.data.inputValue.trim();
    if (!question || this.data.searching) return;

    const history = this.data.messages
      .filter(m => m.role !== 'system')
      .slice(-8)
      .map(m => ({ role: m.role, content: m.content }));

    const userMsg = { role: 'user', content: question };
    this.setData({
      messages: [...this.data.messages, userMsg],
      inputValue: '',
      sendDisabled: true,
      searching: true
    }, () => this.scrollToBottom());

    try {
      const result = await api.chatWithAgent({ message: question, history });

      const answer = result?.reply || '抱歉，我没有理解您的问题。';

      this.setData({
        messages: [...this.data.messages, {
          role: 'ai',
          content: answer,
          sources: []
        }]
      }, () => this.scrollToBottom());
    } catch (err) {
      this.setData({
        messages: [...this.data.messages, {
          role: 'ai',
          content: '查询失败，请稍后重试。',
          sources: []
        }]
      }, () => this.scrollToBottom());
    } finally {
      this.setData({ searching: false });
    }
  },

  /** 自动滚动到底部：先清空再重置，强制 scroll-into-view 重新定位 */
  scrollToBottom() {
    this.setData({ scrollTarget: '' });
    setTimeout(() => {
      this.setData({ scrollTarget: 'msg-bottom' });
    }, 50);
  },

  askQuestion(e) {
    const q = e.currentTarget.dataset.question;
    this.setData({ inputValue: q }, () => this.send());
  },

  clearChat() {
    this.setData({ messages: [], expandedSources: {} });
  }
});
