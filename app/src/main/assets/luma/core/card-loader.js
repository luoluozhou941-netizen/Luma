/**
 * Luma 卡片加载器（底座+卡片架构 · 第一步）
 *
 * 卡片规范（写代码前务必确认卡片包也遵守）：
 * 1. 卡片是一个 fetch 回来塞进 innerHTML 的 HTML 片段，不是页面跳转 —— 全局只有一个WebView页面。
 * 2. innerHTML 插入的 <script> 标签浏览器不会自动执行，这里手动提取重建。
 *    外链脚本(src)是异步的，必须等 onload 后才能确定它执行完了；内联脚本是同步的。
 * 3. 卡片自己的JS必须挂在 window.LumaCards[cardId] 下，提供 init()/destroy()，
 *    不要往全局window上直接挂变量/函数 —— 现在卡片和宿主共享同一个JS作用域，没有iframe隔离。
 * 4. 卡片的CSS选择器必须带 #card-<cardId> 前缀，避免污染全局样式
 *    （loader会自动把卡片HTML包在 id="card-<cardId>" 的容器里，卡片CSS写选择器时用这个id当前缀就行）。
 * 5. destroy() 里必须把自己注册的事件监听/定时器清理干净，loader不会替卡片猜。
 */
(function () {
  const LumaCardLoader = {
    _mountRoot: null,

    _getMountRoot() {
      if (!this._mountRoot) {
        this._mountRoot = document.getElementById('card-mount-root');
      }
      return this._mountRoot;
    },

    /**
     * 加载一张卡片。cardId 对应 cards/<cardId>/ 目录。
     * 如果同一张卡片已经加载过，会先自动卸载再重新加载（保证幂等，方便反复测试）。
     */
    async loadCard(cardId) {
      const root = this._getMountRoot();
      if (!root) {
        console.error('[card-loader] 找不到卡片挂载容器 #card-mount-root，先检查 index.html 里有没有这个div');
        return false;
      }

      try {
        if (window.LumaCards && window.LumaCards[cardId]) {
          this.unloadCard(cardId);
        }

        const basePath = `cards/${cardId}/`;
        const manifest = await this._fetchJSON(basePath + 'manifest.json');
        if (!manifest || !manifest.entry) {
          throw new Error('manifest.json 缺失或没有 entry 字段');
        }

        const html = await this._fetchText(basePath + manifest.entry);

        const wrapper = document.createElement('div');
        wrapper.id = `card-${cardId}`;
        wrapper.className = 'luma-card-instance';
        wrapper.innerHTML = html;
        root.appendChild(wrapper);

        // 卡片HTML里可能引用相对路径的脚本(如 "card.js")，这里统一转成相对cards/<id>/的路径，
        // 这样卡片作者在manifest同目录下写 <script src="card.js"> 就能work，不用自己拼完整路径。
        const scripts = Array.from(wrapper.querySelectorAll('script'));
        for (const oldScript of scripts) {
          if (oldScript.getAttribute('src') && !/^(https?:)?\/\//.test(oldScript.getAttribute('src'))) {
            oldScript.setAttribute('src', basePath + oldScript.getAttribute('src'));
          }
          await this._execScript(oldScript);
        }

        // 所有script都confirm执行完了（inline同步跑完 / 外链等到onload），这时候
        // window.LumaCards[cardId] 才应该真的存在，再去调用init，不然容易拿到undefined。
        const cardModule = window.LumaCards && window.LumaCards[cardId];
        if (cardModule && typeof cardModule.init === 'function') {
          cardModule.init();
        } else {
          console.warn(`[card-loader] 卡片 "${cardId}" 没有注册 window.LumaCards['${cardId}'].init()，跳过init调用`);
        }

        console.log(`[card-loader] 卡片 "${cardId}" 加载完成`);
        return true;
      } catch (err) {
        console.error(`[card-loader] 加载卡片 "${cardId}" 失败：`, err);
        if (window.showToast) {
          window.showToast(`卡片加载失败: ${cardId}`, { type: 'error' });
        }
        return false;
      }
    },

    /**
     * 卸载一张卡片：调用它的destroy()做自清理，然后把DOM和命名空间都清掉。
     */
    unloadCard(cardId) {
      try {
        const cardModule = window.LumaCards && window.LumaCards[cardId];
        if (cardModule && typeof cardModule.destroy === 'function') {
          cardModule.destroy();
        }
        if (window.LumaCards) {
          delete window.LumaCards[cardId];
        }

        const wrapper = document.getElementById(`card-${cardId}`);
        if (wrapper) {
          wrapper.remove();
        }

        console.log(`[card-loader] 卡片 "${cardId}" 已卸载`);
        return true;
      } catch (err) {
        console.error(`[card-loader] 卸载卡片 "${cardId}" 失败：`, err);
        if (window.showToast) {
          window.showToast(`卡片卸载失败: ${cardId}`, { type: 'error' });
        }
        return false;
      }
    },

    async _fetchJSON(path) {
      const res = await fetch(path);
      if (!res.ok) throw new Error(`fetch ${path} 失败: HTTP ${res.status}`);
      return res.json();
    },

    async _fetchText(path) {
      const res = await fetch(path);
      if (!res.ok) throw new Error(`fetch ${path} 失败: HTTP ${res.status}`);
      return res.text();
    },

    /**
     * innerHTML插入的<script>不会自动执行，这里手动创建新的script元素替换掉旧的来触发执行。
     * 外链脚本(src)是异步的：必须等onload触发才resolve，调用方才能安全地去调用卡片的init()。
     * 内联脚本是同步的：插入后立刻算执行完，直接resolve。
     */
    _execScript(oldScript) {
      return new Promise((resolve, reject) => {
        const newScript = document.createElement('script');
        for (const attr of Array.from(oldScript.attributes)) {
          newScript.setAttribute(attr.name, attr.value);
        }

        if (oldScript.src) {
          newScript.onload = () => resolve();
          newScript.onerror = () => reject(new Error(`脚本加载失败: ${oldScript.src}`));
          oldScript.replaceWith(newScript);
        } else {
          newScript.textContent = oldScript.textContent;
          oldScript.replaceWith(newScript);
          resolve();
        }
      });
    }
  };

  window.LumaCardLoader = LumaCardLoader;
  window.LumaCards = window.LumaCards || {};
})();
