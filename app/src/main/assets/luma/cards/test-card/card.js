window.LumaCards = window.LumaCards || {};

window.LumaCards['test-card'] = {
  _handler: null,

  init() {
    const container = document.getElementById('card-test-card');
    const btn = container && container.querySelector('[data-action="toggle-color"]');
    const title = container && container.querySelector('.test-card-title');

    if (btn && title) {
      this._handler = () => {
        title.style.color = title.style.color === 'rgb(214, 122, 71)' ? '' : '#d67a47';
      };
      btn.addEventListener('click', this._handler);
    }

    console.log('[test-card] init 完成，命名空间和事件绑定都正常');
  },

  destroy() {
    const container = document.getElementById('card-test-card');
    const btn = container && container.querySelector('[data-action="toggle-color"]');

    if (btn && this._handler) {
      btn.removeEventListener('click', this._handler);
    }
    this._handler = null;

    console.log('[test-card] destroy 完成，事件监听已清理');
  }
};
